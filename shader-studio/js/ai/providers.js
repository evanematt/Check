// AI image-edit providers. Each one turns (photo + style prompt) into a new image Blob.
//
// Everything runs in the browser with the user's own API key (kept in localStorage).
// To use a server-side proxy instead, point "Base URL" at it and leave the key empty
// if the proxy adds its own credentials.
//
// To add a provider: push another object to PROVIDERS with the same shape.

import { toCanvas } from '../images.js';

const toBlob = (canvas, type, q) => new Promise((res) => canvas.toBlob(res, type, q));

/** Downscale (keeps cost and payload small) and flatten transparency onto white. */
export async function prepareInput(source, { maxSide = 1024, type = 'image/jpeg' } = {}) {
  const small = toCanvas(source, maxSide);
  const flat = document.createElement('canvas');
  flat.width = small.width;
  flat.height = small.height;
  const g = flat.getContext('2d');
  g.fillStyle = '#fff';
  g.fillRect(0, 0, flat.width, flat.height);
  g.drawImage(small, 0, 0);
  return toBlob(flat, type, 0.92);
}

const toBase64 = (blob) =>
  new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result).split(',')[1]);
    r.onerror = () => reject(r.error);
    r.readAsDataURL(blob);
  });

function fromBase64(b64, mime) {
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new Blob([bytes], { type: mime || 'image/png' });
}

async function readError(res) {
  let msg = '';
  try {
    const j = await res.json();
    msg = j.error?.message || j.message || '';
  } catch { /* not JSON */ }
  return `${res.status} ${msg || res.statusText}`.trim();
}

const trimSlash = (s) => s.replace(/\/+$/, '');

export const PROVIDERS = [
  {
    id: 'gemini',
    name: 'Google Gemini',
    defaultModel: 'gemini-2.5-flash-image',
    defaultBase: 'https://generativelanguage.googleapis.com',
    keyHint: 'Get a key at aistudio.google.com',
    needsKey: true,
    async edit({ apiKey, model, baseUrl, prompt, image, signal }) {
      const res = await fetch(`${trimSlash(baseUrl)}/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
        method: 'POST',
        signal,
        headers: { 'content-type': 'application/json', ...(apiKey ? { 'x-goog-api-key': apiKey } : {}) },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }, { inline_data: { mime_type: image.type || 'image/jpeg', data: await toBase64(image) } }] }],
          generationConfig: { responseModalities: ['TEXT', 'IMAGE'] },
        }),
      });
      if (!res.ok) throw new Error(await readError(res));
      const json = await res.json();
      const parts = json.candidates?.[0]?.content?.parts || [];
      const img = parts.map((p) => p.inlineData || p.inline_data).find(Boolean);
      if (!img) {
        const text = parts.map((p) => p.text).filter(Boolean).join(' ');
        throw new Error(text ? `The model replied with text instead of an image: ${text.slice(0, 200)}` : 'The model returned no image (it may have been blocked by safety filters).');
      }
      return fromBase64(img.data, img.mimeType || img.mime_type);
    },
  },
  {
    id: 'openai',
    name: 'OpenAI',
    defaultModel: 'gpt-image-1',
    defaultBase: 'https://api.openai.com',
    keyHint: 'Get a key at platform.openai.com',
    needsKey: true,
    async edit({ apiKey, model, baseUrl, prompt, image, signal }) {
      const form = new FormData();
      form.append('model', model);
      form.append('prompt', prompt);
      form.append('size', 'auto');
      form.append('image', image, 'photo.jpg');
      const res = await fetch(`${trimSlash(baseUrl)}/v1/images/edits`, {
        method: 'POST',
        signal,
        headers: apiKey ? { authorization: `Bearer ${apiKey}` } : {},
        body: form,
      });
      if (!res.ok) throw new Error(await readError(res));
      const json = await res.json();
      const item = json.data?.[0];
      if (item?.b64_json) return fromBase64(item.b64_json, 'image/png');
      if (item?.url) return (await fetch(item.url, { signal })).blob();
      throw new Error('The model returned no image.');
    },
  },
];

export const getProvider = (id) => PROVIDERS.find((p) => p.id === id) || PROVIDERS[0];

export const FIDELITY = [
  { label: 'Loose', text: 'Use the photo only as loose inspiration for the subject and mood.' },
  { label: 'Balanced', text: 'Keep the main subject, layout and composition of the photo.' },
  { label: 'Strict', text: 'Keep the exact composition, pose, proportions and framing of the photo; change only the visual style.' },
];

/** Universal wrapper: the user only describes the style. */
export function buildPrompt(style, fidelity = 1) {
  return `Restyle the attached photo in this style: ${style.trim()}. ${FIDELITY[fidelity].text} Do not add text, watermarks or borders. Return a single image.`;
}

export const PRESETS = [
  ['Low-poly 3D', 'low-poly 3D model render, PS1-era game asset, flat-shaded angular facets, simple painted texture, subject isolated on a pure white background, soft studio light'],
  ['Risograph', 'risograph print, two spot colors, coarse halftone grain, slight misregistration, uncoated paper'],
  ['Cyanotype', 'cyanotype blueprint print, deep prussian blue and white, uneven brushed edges'],
  ['Claymation', 'stop-motion claymation, soft handmade plasticine, visible fingerprints, shallow depth of field'],
  ['Anime cel', 'hand-painted anime cel, clean ink outlines, flat shading, painted background'],
  ['Oil painting', 'thick impasto oil painting, visible brush strokes, rich pigments'],
  ['Neon night', 'cyberpunk neon night, magenta and cyan rim light, wet reflections, haze'],
  ['Pencil sketch', 'graphite pencil sketch on paper, cross-hatching, smudged shading'],
  ['Glitch poster', 'glitch art poster, datamosh smears, RGB split, scanline noise, bold graphic design'],
  ['35mm film', '35mm film photograph, halation, heavy grain, faded warm colors'],
];
