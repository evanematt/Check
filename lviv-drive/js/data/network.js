// Road network data for the game.
//
// SCHEMA (shared with tools/import-osm.mjs, so a real export can replace this):
//   nodes:  [{ id, x, z, control }]          x = east (m), z = south (m)
//     control.kind: 'light' | 'priority' | 'uncontrolled' | 'bend'
//     control.main: street name that has priority (kind 'priority')
//     control.minor: 'giveway' | 'stop'         (sign for the other approaches)
//   edges:  [{ id, a, b, name, lanes, oneway, limit, zone, surface, parking }]
//     lanes   : lanes per travel direction
//     oneway  : null | 'ab' | 'ba'
//     zone    : 'city' | 'residential' | 'pedestrian'
//     parking : null | 'right' | 'left' | 'both'  (relative to a->b)
//   crosswalks: [{ edge, t, signal }]   mid-block crossings (t = 0..1 along edge);
//     crossings at junction approaches are generated automatically.
//   landmarks: [{ type, x, z, rot }]
//
// IMPORTANT: this built-in map is SCHEMATIC. Street names are real Lviv
// streets, but their geometry, sign and signal positions are simplified and
// were NOT taken from a survey. Run tools/import-osm.mjs to build
// data/lviv-osm.json from OpenStreetMap; the game loads it when present.

export const SCHEMATIC_META = {
  source: 'schematic',
  title: 'Львів — центр (схематична карта)',
  note: 'Назви вулиць реальні, геометрія та розміщення знаків спрощені.',
};

const X = [-330, -165, 0, 165, 330];
const Z = [-250, -85, 85, 250];
const JITTER = {
  n10: [-6, 4], n20: [8, -6], n30: [0, 8], n40: [10, 0], n01: [-10, 0],
  n41: [8, -6], n02: [-6, 4], n42: [-4, 10], n13: [6, 0], n23: [-8, 6], n33: [0, -4],
};

const CONTROL = {
  n00: { kind: 'bend' }, n40: { kind: 'bend' }, n03: { kind: 'bend' }, n43: { kind: 'bend' },
  n10: { kind: 'priority', main: "вул. В'ячеслава Чорновола", minor: 'giveway' },
  n20: { kind: 'priority', main: "вул. В'ячеслава Чорновола", minor: 'giveway' },
  n30: { kind: 'light' },
  n01: { kind: 'priority', main: 'вул. Городоцька', minor: 'stop' },
  n11: { kind: 'light' },
  n21: { kind: 'uncontrolled' },
  n31: { kind: 'priority', main: 'вул. Личаківська', minor: 'giveway' },
  n41: { kind: 'priority', main: 'вул. Шевченка', minor: 'stop' },
  n02: { kind: 'light' },
  n12: { kind: 'light' },
  n22: { kind: 'uncontrolled' },
  n32: { kind: 'priority', main: 'вул. Личаківська', minor: 'giveway' },
  n42: { kind: 'priority', main: 'вул. Шевченка', minor: 'giveway' },
  n13: { kind: 'priority', main: 'вул. Богдана Хмельницького', minor: 'giveway' },
  n23: { kind: 'priority', main: 'вул. Богдана Хмельницького', minor: 'giveway' },
  n33: { kind: 'light' },
};

function node(c, r) {
  const id = `n${c}${r}`;
  const [jx, jz] = JITTER[id] || [0, 0];
  return { id, x: X[c] + jx, z: Z[r] + jz, control: CONTROL[id] };
}

const CITY = { lanes: 1, oneway: null, limit: 50, zone: 'city', surface: 'asphalt', parking: null };
const OLD_TOWN = { lanes: 1, limit: 20, zone: 'residential', surface: 'cobble' };

function e(a, b, name, opts = {}) {
  return { id: `${a}-${b}`, a, b, name, ...CITY, ...opts };
}

export function buildSchematicNetwork() {
  const nodes = [];
  for (let r = 0; r < Z.length; r++) for (let c = 0; c < X.length; c++) nodes.push(node(c, r));

  const CH = "вул. В'ячеслава Чорновола";
  const HM = 'вул. Богдана Хмельницького';
  const edges = [
    // East-west
    e('n00', 'n10', CH, { lanes: 2 }), e('n10', 'n20', CH, { lanes: 2 }),
    e('n20', 'n30', CH, { lanes: 2 }), e('n30', 'n40', CH, { lanes: 2 }),
    e('n01', 'n11', 'вул. Дорошенка', { parking: 'right' }),
    e('n11', 'n21', 'вул. Вірменська', { ...OLD_TOWN, oneway: 'ab' }),
    e('n21', 'n31', 'вул. Вірменська', { ...OLD_TOWN, oneway: 'ab' }),
    e('n31', 'n41', 'вул. Підвальна'),
    e('n02', 'n12', 'вул. Князя Романа'),
    e('n12', 'n22', 'вул. Староєврейська', { ...OLD_TOWN, oneway: 'ba' }),
    e('n22', 'n32', 'вул. Староєврейська', { ...OLD_TOWN, oneway: 'ba' }),
    e('n32', 'n42', 'вул. Князя Романа'),
    e('n03', 'n13', HM, { lanes: 2 }), e('n13', 'n23', HM, { lanes: 2 }),
    e('n23', 'n33', HM, { lanes: 2 }), e('n33', 'n43', HM, { lanes: 2 }),
    // North-south
    e('n00', 'n01', 'вул. Городоцька', { lanes: 2 }),
    e('n01', 'n02', 'вул. Городоцька', { lanes: 2 }),
    e('n02', 'n03', 'вул. Городоцька', { lanes: 2 }),
    e('n10', 'n11', 'просп. Свободи', { lanes: 2 }),
    e('n11', 'n12', 'просп. Свободи', { lanes: 2 }),
    e('n12', 'n13', 'просп. Свободи', { lanes: 2 }),
    e('n20', 'n21', 'вул. Руська', OLD_TOWN),
    e('n21', 'n22', 'пл. Ринок', { ...OLD_TOWN, zone: 'pedestrian', limit: 0 }),
    e('n22', 'n23', 'вул. Руська', OLD_TOWN),
    e('n30', 'n31', 'вул. Личаківська'), e('n31', 'n32', 'вул. Личаківська'),
    e('n32', 'n33', 'вул. Личаківська'),
    e('n40', 'n41', 'вул. Шевченка'), e('n41', 'n42', 'вул. Шевченка'),
    e('n42', 'n43', 'вул. Шевченка'),
  ];

  const crosswalks = [
    { edge: 'n11-n12', t: 0.5, signal: false },
    { edge: 'n01-n02', t: 0.5, signal: true },
    { edge: 'n31-n32', t: 0.5, signal: false },
    { edge: 'n13-n23', t: 0.45, signal: false },
    { edge: 'n20-n30', t: 0.5, signal: true },
    { edge: 'n32-n42', t: 0.5, signal: false },
  ];

  const landmarks = [
    { type: 'ratusha', x: 0, z: 0, rot: 0, label: 'Ратуша' },
    { type: 'opera', x: -92, z: -168, rot: Math.PI / 2, label: 'Оперний театр' },
    { type: 'church', x: 105, z: -158, rot: 0, label: 'Домініканський собор' },
    { type: 'church', x: -250, z: 168, rot: 0, label: 'Собор св. Юра' },
    { type: 'plaza', x: 0, z: 0, w: 150, d: 120, label: 'пл. Ринок' },
  ];

  return { meta: SCHEMATIC_META, nodes, edges, crosswalks, landmarks };
}

// Prefer a real export (tools/import-osm.mjs) when it has been generated.
export async function loadNetworkData() {
  try {
    const res = await fetch('data/lviv-osm.json', { cache: 'no-store' });
    if (res.ok) {
      const data = await res.json();
      if (data && Array.isArray(data.nodes) && data.nodes.length > 2) return data;
    }
  } catch {
    // No export present — fall back to the schematic map.
  }
  return buildSchematicNetwork();
}
