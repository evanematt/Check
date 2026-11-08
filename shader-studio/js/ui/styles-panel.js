// Left panel: browse / search styles, favourites, saved recipes.
import { h, $$ } from '../dom.js';
import { icon } from './icons.js';
import { THUMB } from '../thumbs.js';
import { load, save } from '../storage.js';
import { clone, DEFAULT_ADJUST } from '../state.js';
import { resolveValues, getShader } from '../shaders/index.js';
import { toast } from './components.js';

export function createStylesPanel({ root, store, shaders, thumbs, actions }) {
  let tab = 'explore';
  let query = '';
  let favorites = new Set(load('favorites', []));
  let recipes = load('recipes', []);

  const body = h('div', { class: 'sp-body' });
  const search = h('input', {
    type: 'search', placeholder: 'Search name or tag', 'aria-label': 'Search styles', autocomplete: 'off',
    onInput: (e) => { query = e.target.value.trim().toLowerCase(); applyFilter(); },
  });
  const tabs = [['explore', 'Explore'], ['favorites', 'Favorites'], ['recipes', 'Recipes']].map(([id, label]) =>
    h('button', { class: 'tab', type: 'button', role: 'tab', dataset: { tab: id }, onClick: () => { tab = id; render(); } }, label),
  );
  const searchBox = h('div', { class: 'search' }, icon('search', 14), search);

  root.append(
    h(
      'header',
      { class: 'panel-head' },
      h('h2', {}, 'Styles'),
      h(
        'div',
        { class: 'head-actions' },
        h('button', { class: 'icon-btn', title: 'Wider panel', 'aria-label': 'Toggle wide panel', onClick: actions.toggleWide }, icon('expand', 14)),
        h('button', { class: 'icon-btn', title: 'Collapse panel', 'aria-label': 'Collapse panel', onClick: () => actions.togglePanel('left') }, icon('chevronLeft', 16)),
      ),
    ),
    searchBox,
    h('nav', { class: 'tabs', role: 'tablist' }, tabs),
    body,
  );

  // --- tiles -----------------------------------------------------------------
  function selectStyle(id) {
    store.update((s) => { s.styleId = id; }, { structure: true });
    store.commit();
  }

  function toggleFav(id) {
    favorites.has(id) ? favorites.delete(id) : favorites.add(id);
    save('favorites', [...favorites]);
    if (tab === 'favorites') render();
    else syncState();
  }

  function tile(shader) {
    const canvas = h('canvas', { width: THUMB, height: THUMB });
    thumbs.paint(canvas, shader.id);
    const el = h(
      'div',
      {
        class: 'tile', role: 'button', tabindex: '0', dataset: { id: shader.id }, title: shader.name,
        onClick: () => selectStyle(shader.id),
        onKeydown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); selectStyle(shader.id); } },
      },
      h(
        'div',
        { class: 'thumb' },
        canvas,
        shader.animated ? h('span', { class: 'chip' }, 'Live') : null,
        h('button', {
          class: 'star', type: 'button', 'aria-label': `Favorite ${shader.name}`,
          onClick: (e) => { e.stopPropagation(); toggleFav(shader.id); },
        }, icon('star', 12)),
      ),
      h('span', { class: 'tile-name' }, shader.name),
    );
    return el;
  }

  function syncState() {
    const active = store.state.styleId;
    $$('.tile', body).forEach((el) => {
      const id = el.dataset.id;
      el.classList.toggle('active', id === active);
      el.classList.toggle('fav', favorites.has(id));
      el.classList.toggle('failed', thumbs.failed(id));
    });
  }

  function applyFilter() {
    if (tab !== 'explore') return;
    let any = false;
    $$('.cat', body).forEach((cat) => {
      let vis = 0;
      $$('.tile', cat).forEach((el) => {
        const s = getShader(el.dataset.id);
        const hit = !query || [s.name, s.category, ...s.tags].join(' ').toLowerCase().includes(query);
        el.hidden = !hit;
        if (hit) vis++;
      });
      cat.hidden = vis === 0;
      any ||= vis > 0;
    });
    $$('.empty-note', body).forEach((n) => (n.hidden = any));
  }

  // --- tab bodies -------------------------------------------------------------
  function renderExplore() {
    const groups = new Map();
    shaders.forEach((s) => (groups.get(s.category) || groups.set(s.category, []).get(s.category)).push(s));
    for (const [name, list] of groups) {
      body.append(h('section', { class: 'cat' }, h('h4', {}, name), h('div', { class: 'grid' }, list.map(tile))));
    }
    body.append(h('p', { class: 'empty-note', hidden: true }, 'No styles match your search.'));
    applyFilter();
  }

  function renderFavorites() {
    const list = shaders.filter((s) => favorites.has(s.id));
    body.append(
      list.length
        ? h('div', { class: 'grid pad' }, list.map(tile))
        : h('p', { class: 'empty-note' }, 'Star a style to keep it here.'),
    );
  }

  function renderRecipes() {
    const nameInput = h('input', { type: 'text', placeholder: 'Name this look', maxlength: '40', 'aria-label': 'Recipe name' });
    const saveBtn = () => {
      const s = store.state;
      const style = getShader(s.styleId);
      const name = nameInput.value.trim() || `${style.name} ${recipes.length + 1}`;
      recipes.unshift({
        id: Date.now().toString(36), name, styleId: style.id,
        params: resolveValues(style, s), adjust: clone(s.adjust),
      });
      save('recipes', recipes);
      toast(`Saved “${name}”`);
      render();
    };
    body.append(
      h('div', { class: 'recipe-new' }, nameInput, h('button', { class: 'btn', type: 'button', onClick: saveBtn }, icon('plus', 14), 'Save')),
      recipes.length
        ? h('ul', { class: 'recipes' }, recipes.map((r) => {
            const style = getShader(r.styleId);
            return h('li', {},
              h('button', {
                class: 'recipe', type: 'button',
                onClick: () => {
                  store.update((s) => {
                    s.styleId = r.styleId;
                    s.params[r.styleId] = { ...r.params };
                    s.adjust = { ...DEFAULT_ADJUST, ...r.adjust };
                  }, { structure: true });
                  store.commit();
                },
              }, h('b', {}, r.name), h('small', {}, style.name)),
              h('button', {
                class: 'icon-btn', 'aria-label': `Delete ${r.name}`, title: 'Delete',
                onClick: () => { recipes = recipes.filter((x) => x.id !== r.id); save('recipes', recipes); render(); },
              }, icon('trash', 14)),
            );
          }))
        : h('p', { class: 'empty-note' }, 'Recipes save a style, its settings and your adjustments so you can come back to them.'),
    );
  }

  function render() {
    tabs.forEach((t) => {
      const on = t.dataset.tab === tab;
      t.classList.toggle('active', on);
      t.setAttribute('aria-selected', String(on));
    });
    searchBox.hidden = tab !== 'explore';
    body.replaceChildren();
    ({ explore: renderExplore, favorites: renderFavorites, recipes: renderRecipes })[tab]();
    syncState();
  }

  store.on('structure', syncState);
  thumbs.onUpdate((id) => {
    $$(`.tile[data-id="${id}"] canvas`, body).forEach((c) => thumbs.paint(c, id));
    syncState();
  });
  render();
}
