// App state + undo/redo history.
//
// state = {
//   styleId,                 // active shader id
//   params: { [styleId]: { [paramId]: value } },   // sparse overrides, per style
//   adjust: {...},           // global post adjustments
//   motion: { playing, speed }   // not part of history
// }
//
// Events:  'render'    -> something changed that needs a redraw
//          'structure' -> UI must be rebuilt (style switch, undo/redo, reset)
//          'history'   -> undo/redo availability changed

export const DEFAULT_ADJUST = Object.freeze({
  brightness: 0, // -100..100
  contrast: 0, // -100..100
  exposure: 0, // EV, -2..2
  saturation: 100, // 0..200
  hue: 0, // -180..180
  vignette: 0, // 0..100
  grain: 0, // 0..100
  invert: false,
});

export const DEFAULT_MOTION = Object.freeze({ playing: true, speed: 1 });

export const clone = (o) => JSON.parse(JSON.stringify(o));

const HISTORY_LIMIT = 100;

export function createStore(initial) {
  const state = initial;
  const subs = { render: new Set(), structure: new Set(), history: new Set() };
  const emit = (k) => subs[k].forEach((fn) => fn(state));
  const snap = () =>
    JSON.stringify({ styleId: state.styleId, params: state.params, adjust: state.adjust });

  let stack = [snap()];
  let idx = 0;

  const restore = (s) => {
    Object.assign(state, JSON.parse(s));
    emit('structure');
    emit('render');
    emit('history');
  };

  return {
    state,
    on(kind, fn) {
      subs[kind].add(fn);
      return () => subs[kind].delete(fn);
    },
    /** Mutate state in place, then notify. Call commit() once the gesture ends. */
    update(fn, { structure = false } = {}) {
      fn(state);
      if (structure) emit('structure');
      emit('render');
    },
    /** Push the current state onto the undo stack (no-op if unchanged). */
    commit() {
      const s = snap();
      if (s === stack[idx]) return;
      stack = stack.slice(0, idx + 1);
      stack.push(s);
      if (stack.length > HISTORY_LIMIT) stack.shift();
      idx = stack.length - 1;
      emit('history');
    },
    undo() {
      if (idx > 0) restore(stack[--idx]);
    },
    redo() {
      if (idx < stack.length - 1) restore(stack[++idx]);
    },
    canUndo: () => idx > 0,
    canRedo: () => idx < stack.length - 1,
  };
}
