// WebGL2 render pipeline.
//
//   source image ──► [ style pass (user shader) ] ──► FBO ──► [ post pass: adjust ] ──► canvas
//
// A style is any object from js/shaders/. Its fragment source is wrapped with a
// prelude (see buildFragmentSource) so shader files stay short. Uniforms are set
// by *introspecting* the linked program, so a shader may declare only what it uses.

const VERT = `#version 300 es
in vec2 a_pos;
out vec2 v_uv;
void main() {
  v_uv = a_pos * 0.5 + 0.5;
  gl_Position = vec4(a_pos, 0.0, 1.0);
}`;

// ---------------------------------------------------------------------------
// Shader source wrapping
// ---------------------------------------------------------------------------
const PRELUDE = `#version 300 es
precision highp float;
precision highp int;
uniform sampler2D u_image;   // source image (mipmapped, y-up)
uniform vec2  u_resolution;  // output size in px
uniform vec2  u_imageSize;   // source image size in px
uniform float u_time;        // seconds (scaled by Motion speed)
uniform float u_pxScale;     // output px / preview px  -> multiply any px value by this
uniform vec2  u_mouse;       // pointer over canvas, 0..1, y-up
in vec2 v_uv;
out vec4 outColor;
#define texture2D texture
`;

const TOY_PRELUDE =
  PRELUDE +
  `#define iResolution vec3(u_resolution, 1.0)
#define iTime u_time
#define iTimeDelta 0.016
#define iFrame int(u_time * 60.0)
#define iMouse vec4(u_mouse * u_resolution, 0.0, 0.0)
#define iChannel0 u_image
`;

const TOY_MAIN = `
void main() {
  vec4 c = vec4(0.0, 0.0, 0.0, 1.0);
  mainImage(c, gl_FragCoord.xy);
  outColor = vec4(c.rgb, 1.0);
}
`;

/** Three accepted formats: full GLSL (#version), Shadertoy (mainImage), or a bare main(). */
export function buildFragmentSource(src) {
  if (/^\s*#version/m.test(src)) return { source: src, preludeLines: 0 };
  if (/void\s+mainImage\s*\(/.test(src)) {
    const source = TOY_PRELUDE + src + TOY_MAIN;
    return { source, preludeLines: TOY_PRELUDE.split('\n').length - 1 };
  }
  return { source: PRELUDE + src, preludeLines: PRELUDE.split('\n').length - 1 };
}

// ---------------------------------------------------------------------------
// Post pass (global Adjust tab)
// ---------------------------------------------------------------------------
const POST_FRAG = `#version 300 es
precision highp float;
uniform sampler2D u_tex;
uniform float u_brightness, u_contrast, u_exposure, u_saturation, u_hue;
uniform float u_vignette, u_grain, u_invert, u_time;
uniform vec2 u_res;
uniform float u_dual, u_dualPos, u_dualGap, u_flipB;
in vec2 v_uv;
out vec4 outColor;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

vec3 hueRotate(vec3 c, float a) {
  const mat3 toYIQ = mat3(0.299, 0.596, 0.211, 0.587, -0.274, -0.523, 0.114, -0.322, 0.312);
  const mat3 fromYIQ = mat3(1.0, 1.0, 1.0, 0.956, -0.272, -1.106, 0.621, -0.647, 1.703);
  vec3 y = toYIQ * c;
  float cs = cos(a), sn = sin(a);
  y.yz = mat2(cs, sn, -sn, cs) * y.yz;
  return fromYIQ * y;
}

void main() {
  vec3 c = texture(u_tex, v_uv).rgb;
  // Dual view: second panel is the inverted twin; the gap is painted white at the end
  float inGap = 0.0;
  if (u_dual > 0.5) {
    bool rows = u_dual < 1.5;
    float a = rows ? (1.0 - v_uv.y) * u_res.y : v_uv.x * u_res.x;
    float edge = u_dualPos * (rows ? u_res.y : u_res.x);
    if (a >= edge && u_flipB > 0.5) c = 1.0 - c;
    inGap = step(0.5, u_dualGap) * step(abs(a - edge), u_dualGap * 0.5);
  }
  c *= exp2(u_exposure);
  c += u_brightness / 200.0;
  c = (c - 0.5) * max(0.0, 1.0 + u_contrast / 100.0) + 0.5;
  float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
  c = mix(vec3(l), c, u_saturation / 100.0);
  if (u_hue != 0.0) c = hueRotate(c, radians(u_hue));
  c *= 1.0 - (u_vignette / 100.0) * smoothstep(0.25, 0.85, distance(v_uv, vec2(0.5)));
  c += (hash(gl_FragCoord.xy + fract(u_time) * 100.0) - 0.5) * (u_grain / 100.0) * 0.35;
  c = mix(c, 1.0 - c, u_invert);
  c = mix(c, vec3(1.0), inGap);
  outColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}`;

const NEUTRAL_ADJUST = {
  brightness: 0, contrast: 0, exposure: 0, saturation: 100, hue: 0, vignette: 0, grain: 0, invert: false,
  dual: 0, dualPos: 0.5, dualGap: 0,
};

// ---------------------------------------------------------------------------
// GL helpers
// ---------------------------------------------------------------------------
function compileShader(gl, type, src) {
  const sh = gl.createShader(type);
  gl.shaderSource(sh, src);
  gl.compileShader(sh);
  if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
    const log = gl.getShaderInfoLog(sh) || 'Shader compile failed';
    gl.deleteShader(sh);
    throw new Error(log);
  }
  return sh;
}

function linkProgram(gl, vsSrc, fsSrc) {
  const vs = compileShader(gl, gl.VERTEX_SHADER, vsSrc);
  let fs;
  try {
    fs = compileShader(gl, gl.FRAGMENT_SHADER, fsSrc);
  } catch (e) {
    gl.deleteShader(vs);
    throw e;
  }
  const prog = gl.createProgram();
  gl.attachShader(prog, vs);
  gl.attachShader(prog, fs);
  gl.bindAttribLocation(prog, 0, 'a_pos');
  gl.linkProgram(prog);
  gl.deleteShader(vs);
  gl.deleteShader(fs);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
    const log = gl.getProgramInfoLog(prog) || 'Program link failed';
    gl.deleteProgram(prog);
    throw new Error(log);
  }
  const uniforms = {};
  const n = gl.getProgramParameter(prog, gl.ACTIVE_UNIFORMS);
  for (let i = 0; i < n; i++) {
    const u = gl.getActiveUniform(prog, i);
    uniforms[u.name.replace(/\[0\]$/, '')] = { loc: gl.getUniformLocation(prog, u.name), type: u.type };
  }
  return { prog, uniforms };
}

export function hexToRgb(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
  const n = m ? parseInt(m[1], 16) : 0xffffff;
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

/** Convert a UI value to what the GLSL uniform wants. */
function coerce(param, value) {
  switch (param.type) {
    case 'color': return hexToRgb(value);
    case 'toggle': return value ? 1 : 0;
    default: return Number(value);
  }
}

function setUniform(gl, u, v) {
  if (!u) return; // optimised out / not declared
  switch (u.type) {
    case gl.FLOAT: gl.uniform1f(u.loc, Array.isArray(v) ? v[0] : +v); break;
    case gl.INT:
    case gl.BOOL:
    case gl.SAMPLER_2D: gl.uniform1i(u.loc, Array.isArray(v) ? v[0] | 0 : Math.round(+v)); break;
    case gl.FLOAT_VEC2: gl.uniform2fv(u.loc, v); break;
    case gl.FLOAT_VEC3: gl.uniform3fv(u.loc, v.length === 3 ? v : [v[0], v[0], v[0]]); break;
    case gl.FLOAT_VEC4: gl.uniform4fv(u.loc, v.length === 4 ? v : [...v, 1]); break;
    default: break;
  }
}

// ---------------------------------------------------------------------------
// Renderer
// ---------------------------------------------------------------------------
export class Renderer {
  constructor(canvas) {
    this.canvas = canvas;
    const gl = canvas.getContext('webgl2', {
      alpha: false,
      antialias: false,
      preserveDrawingBuffer: true,
      powerPreference: 'high-performance',
    });
    if (!gl) throw new Error('WebGL2 is not available in this browser.');
    this.gl = gl;
    this.maxSize = Math.min(gl.getParameter(gl.MAX_TEXTURE_SIZE), gl.getParameter(gl.MAX_RENDERBUFFER_SIZE), 8192);

    // Fullscreen triangle
    this.vao = gl.createVertexArray();
    gl.bindVertexArray(this.vao);
    const buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
    gl.enableVertexAttribArray(0);
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);

    this.post = linkProgram(gl, VERT, POST_FRAG);
    this.programs = new Map(); // style.id -> { prog, uniforms } | { error }
    this.image = null; // { tex, w, h }
    this.target = null; // { tex, fbo, w, h }
  }

  /** Upload a canvas/img as the source image. */
  setImage(source) {
    const gl = this.gl;
    if (this.image) gl.deleteTexture(this.image.tex);
    const tex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, tex);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, source);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
    gl.generateMipmap(gl.TEXTURE_2D);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    this.image = { tex, w: source.width, h: source.height };
  }

  /** Compile (once) and return the program entry for a style. entry.error is set on failure. */
  getProgram(style) {
    let entry = this.programs.get(style.id);
    if (entry) return entry;
    const { source, preludeLines } = buildFragmentSource(style.fragment);
    try {
      entry = linkProgram(this.gl, VERT, source);
    } catch (e) {
      entry = { error: `${e.message}\n(first ${preludeLines} lines of the compiled source are the built-in prelude)` };
      console.error(`[shader:${style.id}]`, entry.error);
    }
    this.programs.set(style.id, entry);
    return entry;
  }

  errorFor(style) {
    return style ? this.getProgram(style).error || null : null;
  }

  ensureTarget(w, h) {
    const gl = this.gl;
    if (this.target && this.target.w === w && this.target.h === h) return;
    if (this.target) {
      gl.deleteTexture(this.target.tex);
      gl.deleteFramebuffer(this.target.fbo);
    }
    const tex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, tex);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    const fbo = gl.createFramebuffer();
    gl.bindFramebuffer(gl.FRAMEBUFFER, fbo);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
    this.target = { tex, fbo, w, h };
  }

  /**
   * Draw one frame into this.canvas.
   * @param style   style object, or null to show the untouched image
   * @param values  { [paramId]: value } already merged with defaults
   * @param adjust  global adjustments (or null for neutral)
   * @returns error string if the style failed to compile, else null
   */
  render({ style, values = {}, adjust = null, time = 0, mouse = [0.5, 0.5], width, height, pxScale = 1 }) {
    const gl = this.gl;
    if (!this.image) return null;
    if (this.canvas.width !== width || this.canvas.height !== height) {
      this.canvas.width = width;
      this.canvas.height = height;
    }
    gl.bindVertexArray(this.vao);

    let error = null;
    let input = this.image.tex;
    const entry = style ? this.getProgram(style) : null;

    const a = adjust || NEUTRAL_ADJUST;
    let dual = 0;
    let flipB = 0;

    if (entry && !entry.error) {
      this.ensureTarget(width, height);
      gl.bindFramebuffer(gl.FRAMEBUFFER, this.target.fbo);
      gl.viewport(0, 0, width, height);
      gl.useProgram(entry.prog);
      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, this.image.tex);
      const U = entry.uniforms;
      setUniform(gl, U.u_image, 0);
      setUniform(gl, U.u_resolution, [width, height]);
      setUniform(gl, U.u_imageSize, [this.image.w, this.image.h]);
      setUniform(gl, U.u_time, time);
      setUniform(gl, U.u_pxScale, pxScale);
      setUniform(gl, U.u_mouse, mouse);
      const drawStyle = (flipInvert) => {
        for (const p of style.params) {
          let v = values[p.id] ?? p.default;
          if (flipInvert && p.id === 'invert' && p.type === 'toggle') v = !v;
          setUniform(gl, U[p.uniform], coerce(p, v));
        }
        gl.drawArrays(gl.TRIANGLES, 0, 3);
      };
      drawStyle(false);

      // Dual view: draw the second panel with the style's own "invert" toggled
      // (falls back to inverting colours in the post pass when the style has none).
      dual = a.dual | 0;
      if (dual) {
        if (style.params.some((p) => p.id === 'invert' && p.type === 'toggle')) {
          const edge = Math.round(a.dualPos * (dual === 1 ? height : width));
          gl.enable(gl.SCISSOR_TEST);
          if (dual === 1) gl.scissor(0, 0, width, Math.max(0, height - edge));
          else gl.scissor(edge, 0, Math.max(0, width - edge), height);
          drawStyle(true);
          gl.disable(gl.SCISSOR_TEST);
        } else {
          flipB = 1;
        }
      }
      input = this.target.tex;
    } else if (entry) {
      error = entry.error;
    }

    // Post pass -> canvas
    const P = this.post.uniforms;
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.viewport(0, 0, width, height);
    gl.useProgram(this.post.prog);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, input);
    setUniform(gl, P.u_tex, 0);
    setUniform(gl, P.u_brightness, a.brightness);
    setUniform(gl, P.u_contrast, a.contrast);
    setUniform(gl, P.u_exposure, a.exposure);
    setUniform(gl, P.u_saturation, a.saturation);
    setUniform(gl, P.u_hue, a.hue);
    setUniform(gl, P.u_vignette, a.vignette);
    setUniform(gl, P.u_grain, a.grain);
    setUniform(gl, P.u_invert, a.invert ? 1 : 0);
    setUniform(gl, P.u_time, time);
    setUniform(gl, P.u_res, [width, height]);
    setUniform(gl, P.u_dual, dual);
    setUniform(gl, P.u_dualPos, a.dualPos);
    setUniform(gl, P.u_dualGap, a.dualGap * pxScale);
    setUniform(gl, P.u_flipB, flipB);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
    return error;
  }
}
