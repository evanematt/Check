/* Drawing Review — animated flow prototype.
 * A 1920×1080 product mock driven by a scripted cursor, played as one
 * continuous film. Each scene ends in the next scene's starting state; setup()
 * only snaps the UI into place when playback starts or seeks to a scene. */
(() => {
'use strict';

// ---------------------------------------------------------------- icons
const P = (d) => `<path d="${d}"/>`;
const ICONS = {
  logo: '<svg viewBox="0 0 24 24"><path d="M4.5 20V4h3.8l7.4 9.6V4h3.8v16h-3.7L8.3 10.4V20z" fill="currentColor"/></svg>',
  avatar: '<svg viewBox="0 0 24 24"><defs><linearGradient id="avg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#c9a27e"/><stop offset="1" stop-color="#6b4d3a"/></linearGradient></defs><circle cx="12" cy="12" r="12" fill="url(#avg)"/><circle cx="12" cy="9.5" r="4" fill="#f4dcc6"/><path d="M4.5 20.5a8 8 0 0 1 15 0" fill="#2f3a4a"/></svg>',
  plus: P('M12 5v14M5 12h14'),
  minus: P('M5 12h14'),
  folder: P('M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'),
  workflow: '<circle cx="5" cy="17" r="2"/><circle cx="19" cy="17" r="2"/><circle cx="12" cy="7" r="2"/><path d="M6 15.3 10.6 8.6M18 15.3l-4.6-6.7"/>',
  home: P('M3.5 10.5 12 4l8.5 6.5V20a1 1 0 0 1-1 1H15v-6H9v6H4.5a1 1 0 0 1-1-1z'),
  updown: P('m8 15 4 4 4-4M8 9l4-4 4 4'),
  panelLeft: '<rect x="3" y="4" width="18" height="16" rx="2.5"/><path d="M9 4v16"/>',
  panelRight: '<rect x="3" y="4" width="18" height="16" rx="2.5"/><path d="M15 4v16"/>',
  columns: '<rect x="3" y="4" width="18" height="16" rx="2.5"/><path d="M12 4v16"/>',
  dots: '<circle cx="6" cy="12" r="1.1" fill="currentColor"/><circle cx="12" cy="12" r="1.1" fill="currentColor"/><circle cx="18" cy="12" r="1.1" fill="currentColor"/>',
  dotsV: '<circle cx="12" cy="6" r="1.1" fill="currentColor"/><circle cx="12" cy="12" r="1.1" fill="currentColor"/><circle cx="12" cy="18" r="1.1" fill="currentColor"/>',
  share: '<circle cx="18" cy="5.5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="18.5" r="2.5"/><path d="m8.2 10.8 7.6-4.1M8.2 13.2l7.6 4.1"/>',
  drawing: '<path d="M4 20l1.2-4.6L15.6 5a2.1 2.1 0 0 1 3 3L8.2 18.6z"/><path d="M13.8 6.8l3 3"/><path d="M13 20h7"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/>',
  sort: P('M7 4v16M3.5 16.5 7 20l3.5-3.5M17 20V4M13.5 7.5 17 4l3.5 3.5'),
  filter: P('M4 6.5h16M7 12h10M10 17.5h4'),
  brain: '<circle cx="12" cy="12" r="1.4" fill="currentColor"/><ellipse cx="12" cy="12" rx="9" ry="3.8"/><ellipse cx="12" cy="12" rx="9" ry="3.8" transform="rotate(60 12 12)"/><ellipse cx="12" cy="12" rx="9" ry="3.8" transform="rotate(120 12 12)"/>',
  chevDown: P('m6 9 6 6 6-6'),
  chevRight: P('m9 6 6 6-6 6'),
  chevLeft: P('m15 6-6 6 6 6'),
  arrowRight: P('M5 12h14M13 6l6 6-6 6'),
  x: P('M6.5 6.5l11 11M17.5 6.5l-11 11'),
  check: P('m5 12.5 4.5 4.5L19 7'),
  checkCircle: '<circle cx="12" cy="12" r="9"/><path d="m8 12.5 2.8 2.8L16 9.8"/>',
  pencil: P('M4 20l1-4.5L16 4.5a2.1 2.1 0 0 1 3 3L8 18.5z'),
  copy: '<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/>',
  flag: P('M5 21V4h11l-2 4 2 4H5'),
  send: '<path d="M4.5 4.5 20 12 4.5 19.5 7.5 12z"/><path d="M7.5 12H13"/>',
  download: P('M12 4v11M7 10l5 5 5-5M5 20h14'),
  upload: P('M12 16V4M7 9l5-5 5 5M5 20h14'),
  export: '<path d="M12 15V3M7.5 7.5 12 3l4.5 4.5"/><path d="M5 12v7a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7"/>',
  history: '<path d="M3.5 12a8.5 8.5 0 1 0 2.8-6.3L3.5 8.3"/><path d="M3.5 3.5v4.8h4.8"/><path d="M12 7.5V12l3 2"/>',
  sheet: '<path d="M6 3h8l5 5v13H6z"/><path d="M14 3v5h5M9 12h7M9 16h7M12.5 12v7"/>',
  braces: P('M8 4c-2 0-2.5 1-2.5 3v2.5C5.5 11 4.5 12 3.5 12c1 0 2 1 2 2.5V17c0 2 .5 3 2.5 3M16 4c2 0 2.5 1 2.5 3v2.5c0 1.5 1 2.5 2 2.5-1 0-2 1-2 2.5V17c0 2-.5 3-2.5 3'),
  refresh: '<path d="M20 11a8 8 0 0 0-14.3-4.8L3.5 8.5"/><path d="M3.5 4v4.5H8"/><path d="M4 13a8 8 0 0 0 14.3 4.8l2.2-2.3"/><path d="M20.5 20v-4.5H16"/>',
  file: '<path d="M6 3h8l5 5v13H6z"/><path d="M14 3v5h5"/>',
  layers: '<path d="m12 3 9 5-9 5-9-5z"/><path d="m3 13 9 5 9-5"/>',
  alert: '<path d="M12 4 2.8 19.5h18.4z"/><path d="M12 10v4.2M12 17h.01"/>',
  artifactFolder: '<rect x="3.5" y="4" width="17" height="16" rx="2"/><path d="M3.5 9h17"/>',
  autodesk: '<img src="assets/logo-autodesk.png" alt="Autodesk">',
  fieldwire: '<img src="assets/logo-fieldwire.png" alt="Fieldwire">',
  bentley: '<img src="assets/logo-bentley.png" alt="Bentley">',
  play: '<svg viewBox="0 0 24 24"><path d="M7 4.5v15l12.5-7.5z" fill="currentColor"/></svg>',
  pause: '<svg viewBox="0 0 24 24"><rect x="6" y="4.5" width="4" height="15" rx="1" fill="currentColor"/><rect x="14" y="4.5" width="4" height="15" rx="1" fill="currentColor"/></svg>',
  restart: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3.5 12a8.5 8.5 0 1 0 2.8-6.3L3.5 8.3"/><path d="M3.5 3.5v4.8h4.8"/></svg>',
};
const icon = (n) => {
  const v = ICONS[n] || '';
  return v.startsWith('<svg') || v.startsWith('<img') ? v
    : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">${v}</svg>`;
};
const hydrate = (root) => root.querySelectorAll('[data-icon]').forEach((el) => {
  if (!el.children.length && !el.textContent.trim()) el.innerHTML = icon(el.dataset.icon);
});

// ---------------------------------------------------------------- content
const PROMPT = 'Can you run a full code check on Level 2 against IBC 2021 and our internal QA/QC standards? Focus on egress widths, fire-rated assemblies, and door hardware labeling.';
const ANSWER = [
  'I dispatched 8 sub-agents — one per sheet in the Level 2 set — to review egress, rated assemblies, and door hardware against IBC 2021 Chapter 10, Chapter 7, and your QA/QC manual §4. So far: 14 verified findings, 3 Critical and 5 High.',
  'Top issues: two exit-access corridors below the 44" minimum clear width (IBC §1020.3), one rated wall missing its UL assembly callout, and four doors without fire-rating labels. Full table updating in Drawing Review as agents complete their pages.',
];
const QUESTIONS = [
  { q: 'Is this project aligned with our Q3 goals?', opts: [
    'Yes, this project aligns with our Q3 goals and will help us achieve our outcomes.',
    'Yes, this project perfectly aligns with our Q3 goals by enhancing user engagement and boosting productivity.',
    'No, this project does not align with our Q3 goals as it diverts resources from our main objectives.',
    'Partially, this project aligns with some Q3 goals but needs adjustments to fully meet our targets.'] },
  { q: 'Which sheets should be included in this review?', opts: [
    'All Level 2 sheets (A-101 – A-108)',
    'Only architectural floor plans',
    'Life-safety and egress plans only',
    'Sheets changed since the last submittal'] },
];
const CUSTOM_ANSWER = 'All Level 2 sheets, plus the door schedule on A-601';
const TASKS = [
  'Read all pages of the internal standards document thoroughly',
  'Analyze and list all standards that must be checked',
  'Determine number of pages in drawing set',
  'Check each drawing page against all applicable standards',
  'Verify all drawing pages have been checked and provide summary',
];
const DISCIPLINES = ['General (G)', 'Hazardous materials', 'Survey / Mapping (V)', 'Geotechnical (B)', 'Civil (C)', 'Landscape (L)', 'Structural (S)', 'Architectural (A)', 'Interiors (I)', 'Fire protection (F)'];
const discKey = (d) => d.replace(/ \(.\)$/, '');

const DR = [
  { t: 'Empty Design Criteria Table', sev: 'H', cat: 'Hazardous materials',
    desc: 'The DESIGN CRITERIA table in the title block is completely empty with no values for service, phase, design/ambient temperature, delta, temp, or length/HD. For a fuel oil piping system, these engineering parameters are critical and must be populated.',
    refs: ['Fuel Oil Level 1 Piping Plan - Title Block', 'Mappaenmundi genr... | Drawing Page 6'],
    target: [596, 138], box: [204, 36],
    callout: 'Legend references silt fence and inlet protection, but no installation sequencing or maintenance intervals are called out on the plan. EPA CGP 2022 Part 2.1.1 and local SWPPP requirements mandate that temporary BMPs be sequenced and maintained until permanent stabilization.' },
  { t: 'Empty Operating Service Criteria', sev: 'H', cat: 'Civil',
    desc: 'The OPERATING SERVICE block on the owner panel lists no operating pressure, flow or service class. These values drive the pipe schedule and must be completed before the set is issued.',
    refs: ['Title Block - Owner panel', 'Mappaenmundi genr... | Drawing Page 6'],
    target: [792, 176], box: [440, 214],
    callout: 'Owner block lists the Town of Andover but no operating service criteria. QA/QC manual §4.2 requires service class and design pressure on every sheet.' },
  { t: 'Criteria Table Empty', sev: 'M', cat: 'General',
    desc: 'The revisions table is blank while the sheet is issued as revision 2. Every revision needs a date, description and approver.',
    refs: ['Revisions table', 'QA/QC Manual §4.6'],
    target: [792, 372], box: [440, 318],
    callout: 'Revisions table is blank while the sheet is issued as Rev. 2. Record each revision with date, description and approver (QA/QC §4.6).' },
  { t: 'Blank Design Criteria Table', sev: 'H', cat: 'Civil',
    desc: 'Legend symbology for R&D CONC. CURB does not match the hatch used on the plan, so the extent of removal cannot be verified.',
    refs: ['Legend', 'CAD Standard §2.3'],
    target: [70, 505], box: [150, 392],
    callout: 'Legend symbol for R&D CONC. CURB does not match the hatch used on the plan. Align symbology with the CAD standard before issuing.' },
  { t: 'Exit-access corridor below 44" clear', sev: 'H', cat: 'Architectural',
    desc: 'The exit-access path between the sidewalk and the N. Main St. entrance measures 40" clear. IBC §1020.3 requires 44" minimum where the occupant load exceeds 50.',
    refs: ['IBC 2021 §1020.3', 'Mappaenmundi genr... | Drawing Page 6'],
    target: [310, 256], box: [14, 290],
    callout: 'Corridor between the sidewalk and the N. Main St. entrance measures 40" clear. IBC §1020.3 requires 44" minimum for an occupant load over 50.' },
  { t: 'Door missing fire-rating label', sev: 'M', cat: 'Fire protection',
    desc: 'The entry door at the Public Safety Center sits in a 1-hour rated wall, but the door schedule shows no fire-rating label for the door or frame.',
    refs: ['IBC 2021 §716', 'Door schedule A-601'],
    target: [420, 236], box: [470, 20],
    callout: 'Entry door is in a 1-hr rated wall but the schedule shows no fire-rating label. Provide a labeled door and frame per IBC §716.' },
];
const SUB = [
  { t: 'Shop drawing stamp missing', sev: 'H', cat: 'General', desc: 'Submittal 08 71 00-02 is missing the contractor review stamp required before architect review.', refs: ['Spec 01 33 00'], target: [700, 120], box: [300, 60], callout: 'Contractor review stamp missing on submittal cover. Spec 01 33 00 §1.6.' },
  { t: 'Rebar schedule mismatch', sev: 'M', cat: 'Structural', desc: 'Bar sizes in the schedule do not match the typical footing detail.', refs: ['S-501 Typical details'], target: [300, 300], box: [360, 330], callout: 'Schedule calls #5 bars while detail 3/S-501 shows #6. Confirm with the structural engineer.' },
  { t: 'Unapproved substitution: closers', sev: 'M', cat: 'Architectural', desc: 'The submitted door closer differs from the specified basis of design without a substitution request.', refs: ['Spec 08 71 00'], target: [500, 420], box: [120, 380], callout: 'Closer model differs from the basis of design. Submit a substitution request per Spec 01 25 00.' },
];
const data = () => (S.dataset === 'sub' ? SUB : DR);

// ---------------------------------------------------------------- dom
const $ = (s, r = document) => r.querySelector(s);
const stage = $('#stage'), frame = $('#frame'), rig = $('#rig'), viewport = $('#viewport'), overlay = $('#overlay'), cursorEl = $('#cursor');
const chatCol = $('#chatCol'), issuesCol = $('#issuesCol'), artifactCol = $('#artifactCol');
const issueList = $('#issueList'), chipsEl = $('#chips');
const answerEl = $('#answer');

// panes
function paneHTML(id, active, alt) {
  const tabs = ['Mappaenmundi genr... | Drawing Page 6', 'Relliance on early te... | Drawing Page 2', 'Relliance on early te... | Drawing Page 2', 'Relliance on early te... | Drawing Page 2', 'Relliance on early te... | Drawing Page 2']
    .map((t, i) => `<div class="tab ${i === active ? 'active' : ''} ${i === 2 && alt ? 'alt' : ''}"><span data-icon="file"></span><span class="lbl">${t}</span>${i === active ? '<span class="x dim" data-icon="x"></span>' : ''}</div>`).join('');
  const title = active === 0 ? 'Mappaenmundi genr... | Drawing Page 6' : 'Relliance on early te... | Drawing Page 2';
  return `<div class="pane" id="${id}">
    <div class="tabs"><div class="tabs-in">${tabs}</div><button class="pane-close" data-icon="x" aria-label="Close"></button></div>
    <div class="toolbar"><span class="ttl">${title}</span>
      <div class="pager"><span data-icon="chevLeft"></span><span class="pg">1</span><span class="dim">/ 8</span><span data-icon="chevRight"></span></div>
      ${id === 'paneA' ? '<button class="tb-btn icon split" data-icon="columns" aria-label="Split view"></button>' : ''}
      <button class="tb-btn export"><span data-icon="export"></span><span class="export-label">Export</span></button></div>
    <div class="canvas"><div class="paper"><img src="assets/drawing.png" alt="Site preparation and demolition plan" draggable="false">
      <svg class="lead hidden" viewBox="0 0 840 559"><polyline points="0,0 0,0 0,0"/></svg>
      <div class="marks"></div>
      <div class="callout hidden"><span class="txt"></span><span class="cx" data-icon="x"></span></div></div>
      <div class="zoom"><button data-icon="plus" aria-label="Zoom in"></button><span class="zl">100%</span><button data-icon="minus" aria-label="Zoom out"></button></div></div>
  </div>`;
}
$('#panes').innerHTML = paneHTML('paneA', 0, true) + paneHTML('paneB', 2, false);
const paneA = $('#paneA'), paneB = $('#paneB');

// files view
$('#filesView').innerHTML = `
  <div class="add-files"><span data-icon="plus"></span>Add files</div>
  <div class="tree-row" style="--d:0"><span data-icon="chevDown" class="dim"></span><span data-icon="folder" class="dim"></span>Andriy Mulyar</div>
  <div class="tree-row" style="--d:1"><span data-icon="chevDown" class="dim"></span><span data-icon="folder" class="dim"></span>Product review</div>
  <div class="tree-row" style="--d:2"><span data-icon="chevRight" class="dim"></span><span data-icon="folder" class="dim"></span>Studs</div>
  <div class="tree-row" style="--d:2"><span data-icon="file" class="pdf"></span>KitchenAppliances_Reviews_2023.pdf</div>
  <div class="tree-row" style="--d:2"><span data-icon="file" class="pdf"></span>GadgetsAndGizmos_UserFeedback_baraku.pdf</div>
  <div class="tree-row" style="--d:2"><span data-icon="file" class="pdf"></span>A-101_Floor-Plan_Level-2_Rev2.pdf</div>
  <div class="tree-row" style="--d:0"><span data-icon="chevDown" class="dim"></span><span data-icon="artifactFolder" class="dim"></span>Artifacts</div>
  <div class="tree-row sel" id="treeDR" style="--d:1"><span data-icon="drawing" class="dim"></span>Drawing review.pdf</div>
  <div class="tree-row" id="treeSub" style="--d:1"><span data-icon="drawing" class="dim"></span>Submittal review.pdf</div>`;

// answer words
ANSWER.forEach((p, i) => {
  $(`#ans${i + 1}`).innerHTML = p.split(' ').map((w) => `<span class="w">${w.replace(/</g, '&lt;')}</span>`).join(' ');
});
$('#msgUser').textContent = PROMPT;
hydrate(document);

// ---------------------------------------------------------------- timing / cancellation
let RUN = 0, paused = false, speed = 1, clock = 0, flowing = false;
class Cancel extends Error {}
function wait(ms) {
  const id = RUN;
  return new Promise((res, rej) => {
    let last = window.__CAPTURE__ ? window.__vnow : performance.now(), acc = 0;
    const tick = (t) => {
      // capture mode (video export) runs on a virtual clock stepped one frame at a time
      const now = window.__CAPTURE__ ? window.__vnow : t;
      if (id !== RUN) return rej(new Cancel());
      if (!paused) { const d = (now - last) * speed; acc += d; clock += d; }
      last = now;
      if (acc >= ms) res(); else requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  });
}
const enter = (el, cls = 'enter') => { el.classList.remove(cls); void el.offsetWidth; el.classList.add(cls); };

// ---------------------------------------------------------------- state
const base = () => ({
  launched: false, attached: false, typed: '', sent: false, thinking: false,
  q: 0, tasks: null, answered: false, answerFull: false,
  issues: false, artifact: false, chat: true, split: false,
  mode: 'issues', dataset: 'dr', expanded: 0, status: {}, filters: [], marks: false,
});
let S = base(), Prev = base(), builtDataset = null;

const BODY_W = 1868, SIDE_W = 458;

function render() {
  const inst = stage.classList.contains('instant');
  const enterIf = (cond, el, cls) => { if (!inst && cond) enter(el, cls); };

  // chat
  chatCol.classList.toggle('launched', S.launched);
  chatCol.classList.toggle('sent', S.sent);
  chatCol.classList.toggle('is-thinking', S.thinking && !S.answered);
  chatCol.classList.toggle('answered', S.answered);
  enterIf(S.sent && !Prev.sent, $('#msgFile'));
  enterIf(S.sent && !Prev.sent, $('#msgUser'));
  enterIf(S.thinking && !Prev.thinking, $('#thinking'));
  enterIf(S.answered && !Prev.answered, $('.meta', answerEl));
  answerEl.classList.toggle('full', S.answerFull);
  $('#composer').classList.toggle('attached', S.attached);
  enterIf(S.attached && !Prev.attached, $('#attach'));
  $('#typed').textContent = S.typed;
  $('#cInput').classList.toggle('has-text', !!S.typed);
  $('#send').classList.toggle('ready', !!S.typed);
  $('#wfPill').classList.toggle('on', S.launched);

  // breadcrumbs
  setCrumb($('#crumb1'), S.launched ? 'Workflows' : 'New session', inst);
  setCrumb($('#crumb2'), S.sent ? 'Habitat House Residential Drawing' : S.launched ? 'Drawing Review' : '', inst);
  $('#crumbSep2').style.display = S.launched ? '' : 'none';
  $('#topbar').classList.toggle('lined', S.answered);

  // header toggles
  const bI = $('#btnIssues'), bC = $('#btnChat');
  bI.classList.toggle('disabled', !S.answered);
  bI.classList.toggle('on', S.issues);
  bC.classList.toggle('gone', !S.artifact);
  bC.classList.toggle('on', S.artifact && S.chat);

  // floating panels
  $('#qPanel').classList.toggle('open', S.q > 0);
  if (S.q > 0 && S.q !== Prev.q) renderQ(S.q, !inst && Prev.q > 0);
  $('#tasksPanel').classList.toggle('open', S.tasks !== null);
  if (S.tasks !== null && S.tasks !== Prev.tasks) renderTasks(S.tasks, inst ? -1 : Prev.tasks);

  // layout
  const iw = S.issues ? SIDE_W : 0;
  const chat = S.chat || !S.artifact;
  const cw = !chat ? 0 : S.artifact ? SIDE_W : BODY_W - iw;
  const aw = S.artifact ? BODY_W - iw - cw : 0;
  if (!inst && S.issues && !Prev.issues) {
    // list cascades in behind the sliding panel (45ms stagger, follow-through after the panel)
    $$('.issue-card', issueList).forEach((c, i) => anim(c, [{ opacity: 0, transform: 'translateX(-22px)' }, { opacity: 1, transform: 'none' }],
      { duration: 420, delay: 180 + i * 45, easing: EASE.enter, fill: 'backwards' }));
  }
  if (!inst && S.artifact && !Prev.artifact) {
    anim($('.canvas', paneA), [{ opacity: 0, transform: 'translateY(28px) scale(.96)' }, { opacity: 1, transform: 'none' }],
      { duration: 700, delay: 220, easing: EASE.enter, fill: 'backwards' });
  }
  if (!inst && S.split && !Prev.split) {
    anim($('.canvas', paneB), [{ opacity: 0, transform: 'translateX(48px)' }, { opacity: 1, transform: 'none' }],
      { duration: 650, delay: 200, easing: EASE.enter, fill: 'backwards' });
  }
  issuesCol.style.width = iw + 'px';
  artifactCol.style.width = aw + 'px';
  chatCol.style.width = cw + 'px';
  chatCol.classList.toggle('lined', S.artifact && cw > 0);
  chatCol.classList.toggle('narrow', cw < 700);

  // issues panel
  const inner = $('.issues-inner');
  inner.classList.toggle('files-mode', S.mode === 'files');
  if (S.mode === 'files' && Prev.mode !== 'files' && !inst) enter($('#filesView'));
  $('#modeLabel').textContent = S.mode === 'files' ? 'Files' : 'Issues';
  $('#artifactSelLabel').textContent = S.dataset === 'sub' ? 'Submittal Review.json' : 'Drawing Review.json';
  if (builtDataset !== S.dataset) { buildIssues(); if (!inst) enter(issueList, 'swap'); }
  updateIssues(inst);
  renderChips(inst);
  const fc = $('#filterCount');
  const n = S.filters.length;
  if (fc.textContent !== String(n || '')) { fc.textContent = n || ''; if (!inst && n) enter(fc, 'bump'); }
  $('#btnFilter').classList.toggle('has-count', n > 0);

  // artifact
  paneB.classList.toggle('collapsed', !S.split);
  $('.split', paneA).classList.toggle('on', S.split);
  $('.canvas', paneA).parentElement.classList.toggle('show-marks', S.marks);
  paneB.classList.toggle('show-marks', S.marks);
  $('#artCard').classList.toggle('on', S.artifact);
  const paneW = S.split ? aw / 2 : aw;
  updatePane(paneA, S.expanded, inst, S.split ? paneW : 0);
  updatePane(paneB, S.dataset === 'dr' ? 4 : 1, inst, paneW);

  Prev = JSON.parse(JSON.stringify(S));
}

function setCrumb(el, text, inst) {
  if (el.textContent === text) return;
  el.textContent = text;
  if (!inst) enter(el, 'swap');
}

// ---------------------------------------------------------------- panels
function renderQ(n, swap) {
  const q = QUESTIONS[n - 1];
  const p = $('#qPanel');
  p.innerHTML = `<div class="q-head"><span class="q-text">${q.q}</span>
      <span class="q-nav"><span data-icon="chevLeft"></span><span><span class="strong">${n}</span> of 2</span><span data-icon="chevRight"></span><span data-icon="x" style="color:var(--text)"></span></span></div>
    <div class="q-body">${q.opts.map((o, i) => `<div class="q-opt" data-i="${i}"><span class="num">${i + 1}</span><span class="lbl">${o}</span><span data-icon="arrowRight"></span></div>`).join('')}
      <div class="q-custom"><span class="num" data-icon="pencil"></span><span class="q-input"><span class="qtxt">Enter custom response</span><span class="caret"></span></span>
      <button class="skip">Skip question</button><button class="q-send" data-icon="arrowRight"></button></div></div>`;
  hydrate(p);
  if (swap) enter($('.q-body', p), 'fade');
}

function renderTasks(k, prev) {
  const p = $('#tasksPanel');
  p.innerHTML = `<div class="t-head"><span data-icon="chevDown"></span><b>Tasks</b><small>${Math.min(k, 5)} of 5 completed</small></div>
    ${TASKS.map((t, i) => {
      const st = i < k ? 'done' : i === k ? 'active' : '';
      const ico = i < k ? icon('checkCircle') : i === k ? '<span class="spin"></span>' : '<span class="ring"></span>';
      const fresh = i < k && i >= prev && prev >= 0 ? ' enter' : '';
      return `<div class="t-item ${st}${fresh}"><span class="t-ico">${ico}</span>${t}</div>`;
    }).join('')}`;
  hydrate(p);
}

// ---------------------------------------------------------------- issues
function buildIssues() {
  builtDataset = S.dataset;
  issueList.innerHTML = data().map((it, i) => `
    <div class="issue" data-i="${i}"><div class="clip"><div class="issue-card">
      <div class="issue-head">
        <div class="i-ava" data-icon="logo"></div>
        <div class="i-main"><div class="i-title"><span class="t">${it.t}</span><span class="sev">${it.sev}</span><span class="dot"></span><span class="status"></span></div>
          <div class="i-sub">Medium</div></div>
        <button class="i-ctrl i-more" data-icon="dotsV" aria-label="Issue actions"></button>
        <button class="i-ctrl i-chev" data-icon="chevRight" aria-label="Expand"></button>
      </div>
      <div class="i-body"><div><div class="i-body-in">
        <h4>${it.t}</h4><div class="i-cat">${it.cat}</div><p class="i-desc">${it.desc}</p>
        <div class="hr"></div><div class="i-actions"></div><div class="hr"></div>
        <div class="refs-h">References <span class="n">${it.refs.length}</span></div>
        <div class="ref-list">${it.refs.map((r, j) => `<span class="ref ${j === 0 ? 'boxed' : ''}">${r}</span>`).join('')}</div>
      </div></div></div>
    </div></div></div>`).join('');
  hydrate(issueList);
  [paneA, paneB].forEach((p) => {
    $('.marks', p).innerHTML = data().map((it, i) => `<span class="marker" data-i="${i}" style="left:${it.target[0]}px;top:${it.target[1]}px;transition-delay:${i * 60}ms">${i + 1}</span>`).join('');
  });
}
const visible = (it) => !S.filters.length || S.filters.includes(it.cat);

function updateIssues(inst) {
  data().forEach((it, i) => {
    const el = issueList.children[i];
    const st = S.status[i] || 'open';
    el.classList.toggle('expanded', S.expanded === i);
    el.classList.toggle('filtered', !visible(it));
    el.classList.toggle('accepted', st === 'accepted');
    el.classList.toggle('ignored', st === 'ignored');
    const badge = $('.status', el);
    const label = st === 'accepted' ? 'Accepted' : st === 'ignored' ? 'Ignored' : '';
    if (badge.textContent !== label) { badge.textContent = label; if (!inst && label) enter(badge); }
    const acts = $('.i-actions', el);
    if (acts.dataset.st !== st) {
      acts.dataset.st = st;
      acts.innerHTML = st === 'open'
        ? `<button class="btn primary act-accept"><span data-icon="check"></span>Accept finding</button><button class="btn outline act-ignore"><span data-icon="x"></span>Ignore finding</button>`
        : st === 'accepted'
          ? `<button class="btn muted act-accept"><span data-icon="check"></span>Accepted</button><button class="btn outline act-ignore"><span data-icon="x"></span>Ignore finding</button>`
          : `<button class="btn outline small act-reopen"><span data-icon="refresh"></span>Reopen</button>`;
      hydrate(acts);
    }
  });
}

function renderChips(inst) {
  const want = S.filters;
  const have = [...chipsEl.children].filter((c) => !c.classList.contains('out')).map((c) => c.dataset.k);
  have.forEach((k) => {
    if (!want.includes(k)) {
      const c = chipsEl.querySelector(`[data-k="${CSS.escape(k)}"]:not(.out)`);
      if (inst) c.remove(); else { c.classList.add('out'); setTimeout(() => c.remove(), 260); }
    }
  });
  want.forEach((k) => {
    if (!have.includes(k)) {
      const c = document.createElement('span');
      c.className = 'chip' + (inst ? '' : ' enter');
      c.dataset.k = k;
      c.innerHTML = `${k}<span class="x" data-icon="x"></span>`;
      hydrate(c);
      chipsEl.appendChild(c);
    }
  });
  chipsEl.classList.toggle('show', want.length > 0 && S.mode === 'issues');
}

// ---------------------------------------------------------------- artifact panes
// fitW > 0 zooms the page so the callout and its target fill a pane that wide.
function updatePane(pane, idx, inst, fitW) {
  const it = data()[idx];
  const paper = $('.paper', pane), c = $('.callout', pane), lead = $('.lead', pane), txt = $('.txt', c);
  $$('.marker', pane).forEach((m) => {
    const d = data()[+m.dataset.i];
    m.classList.toggle('active', +m.dataset.i === idx);
    m.classList.toggle('filtered', !d || !visible(d));
  });
  const show = S.marks && it && visible(it);
  // view
  let zoom = 1;
  if (it && fitW > 0) {
    const x0 = Math.min(it.box[0], it.target[0]) - 14, x1 = Math.max(it.box[0] + 304, it.target[0]) + 14;
    const y0 = Math.min(it.box[1], it.target[1]) - 14, y1 = Math.max(it.box[1] + 150, it.target[1]) + 14;
    zoom = Math.min(1.6, Math.max(1.1, (fitW - 24) / (x1 - x0)));
    paper.style.setProperty('--cx', (x0 + x1) / 2);
    paper.style.setProperty('--cy', (y0 + y1) / 2);
  } else {
    paper.style.setProperty('--cx', 420);
    paper.style.setProperty('--cy', 280);
  }
  paper.style.setProperty('--z', zoom);
  $('.zl', pane).textContent = Math.round(zoom * 100) + '%';

  if (!show) { c.classList.add('hidden'); lead.classList.add('hidden'); return; }
  const changed = txt.textContent !== it.callout;
  if (changed) { txt.textContent = it.callout; if (!inst) enter(txt, 'fade'); }
  c.style.left = it.box[0] + 'px';
  c.style.top = it.box[1] + 'px';
  c.classList.remove('hidden');
  const drawLead = () => {
    const [tx, ty] = it.target, [bx, by] = it.box, bw = 304, bh = c.offsetHeight;
    let sx, sy, ex, ey;
    if (tx > bx + bw) { sx = bx + bw; sy = by + bh * 0.43; ex = Math.max(sx, tx - 26); ey = sy; }
    else if (tx < bx) { sx = bx; sy = by + bh * 0.43; ex = Math.min(sx, tx + 26); ey = sy; }
    else if (ty < by) { sx = tx; sy = by; ex = sx; ey = ty + 20; }
    else { sx = tx; sy = by + bh; ex = sx; ey = ty - 20; }
    const r = 10, dx = tx - ex, dy = ty - ey, L = Math.hypot(dx, dy) || 1;
    $('polyline', lead).setAttribute('points', `${sx},${sy} ${ex},${ey} ${tx - dx / L * r},${ty - dy / L * r}`);
    lead.classList.remove('hidden');
  };
  if (changed && !inst) { lead.classList.add('hidden'); setTimeout(drawLead, 380 / speed); } else drawLead();
}
function $$(s, r = document) { return [...r.querySelectorAll(s)]; }

// ---------------------------------------------------------------- overlay: menus, toast
function box(el) {
  // stage coordinates, correct at any camera zoom (including mid-move)
  const sr = stage.getBoundingClientRect(), r = el.getBoundingClientRect(), k = sr.width / 1920;
  return { x: (r.left - sr.left) / k, y: (r.top - sr.top) / k, w: r.width / k, h: r.height / k };
}
function showMenu(anchor, items, { place = 'bottom-start', dx = 0, dy = 6, width } = {}) {
  const m = document.createElement('div');
  m.className = 'menu';
  if (width) m.style.width = width + 'px';
  m.innerHTML = items.map((it) => it === '-' ? '<div class="mi-sep"></div>' :
    `<div class="mi ${it.tall ? 'tall' : ''} ${it.checked ? 'checked' : ''}" data-id="${it.id}">` +
    (it.check ? `<span class="chk">${icon('check')}</span>` : '') +
    (it.icon ? `<span data-icon="${it.icon}"></span>` : '') +
    (it.brand ? `<span class="brand">${icon(it.brand)}</span>` : '') +
    (it.html || `<span>${it.label}</span>`) +
    (it.sub ? `<span class="sub-chev">${icon('chevRight')}</span>` : '') +
    (it.tick ? `<span class="tick">${icon('check')}</span>` : '') + '</div>').join('');
  hydrate(m);
  overlay.appendChild(m);
  const r = box(anchor);
  let x = r.x + dx, y = r.y + r.h + dy;
  if (place === 'bottom-end') x = r.x + r.w - m.offsetWidth + dx;
  if (place === 'top-start') y = r.y - m.offsetHeight - dy;
  if (place === 'right-start') { x = r.x + r.w + dx; y = r.y + dy; }
  m.style.left = x + 'px';
  m.style.top = y + 'px';
  // items cascade in just behind the container (stagger stays under 200ms)
  $$('.mi', m).forEach((el, i) => anim(el, [{ opacity: 0, transform: 'translateY(-5px)' }, { opacity: 1, transform: 'none' }],
    { duration: 220, delay: 50 + i * 22, easing: EASE.ui, fill: 'backwards' }));
  return m;
}
const mi = (m, id) => m.querySelector(`[data-id="${id}"]`);
function closeMenus(now) {
  $$('.menu', overlay).forEach((m) => {
    if (now) m.remove(); else { m.classList.add('out'); setTimeout(() => m.remove(), 160); }
  });
  $$('.stage .on[data-menu-anchor]').forEach((el) => { el.classList.remove('on'); delete el.dataset.menuAnchor; });
}
const anchorOn = (el) => { el.classList.add('on'); el.dataset.menuAnchor = '1'; };

let toastTimer;
function toast(text, ms = 2200) {
  $('#toastText').textContent = text;
  $('#toast').classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => $('#toast').classList.remove('show'), ms / speed);
}

// ---------------------------------------------------------------- motion language
// One personality for the whole film: calm, product-demo "Premium/Corporate".
// Camera and panel moves share one signature curve; entrances decelerate,
// small confirmations pop with a light overshoot.
const EASE = {
  camera: 'cubic-bezier(.4, 0, .2, 1)',     // signature curve: camera + on-screen moves
  enter: 'cubic-bezier(.05, .7, .1, 1)',    // emphasized decelerate for entrances
  exit: 'cubic-bezier(.3, 0, 1, 1)',        // accelerate for exits
  ui: 'cubic-bezier(.2, 0, 0, 1)',          // snappy UI
  pop: 'cubic-bezier(.34, 1.45, .64, 1)',   // small overshoot for badges, markers, checks
  cursor: 'cubic-bezier(.45, .05, .2, 1)',
};
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
const anim = (el, frames, opts) => el.animate(frames, { ...opts, duration: opts.duration / speed, delay: (opts.delay || 0) / speed });

// ---------------------------------------------------------------- camera
// The camera is a translate+scale on the 1920×1080 stage inside a fixed frame,
// like the zoom-and-pan of an edited screen recording.
let cam = { x: 0, y: 0, z: 1 }, camAnim = null;
const camT = (c) => `translate(${c.x}px, ${c.y}px) scale(${c.z})`;
const camCenter = (c) => ({ x: (960 - c.x) / c.z, y: (540 - c.y) / c.z });
function camFor(cx, cy, z) {
  z = Math.max(1, z);
  const x = Math.min(0, Math.max(1920 - 1920 * z, 960 - cx * z));
  const y = Math.min(0, Math.max(1080 - 1080 * z, 540 - cy * z));
  return { x, y, z };
}
function rectOf(targets, pad) {
  const bs = (Array.isArray(targets) ? targets : [targets]).map((t) => (t.nodeType ? box(t) : t));
  const x0 = Math.min(...bs.map((b) => b.x)) - pad, y0 = Math.min(...bs.map((b) => b.y)) - pad;
  const x1 = Math.max(...bs.map((b) => b.x + b.w)) + pad, y1 = Math.max(...bs.map((b) => b.y + b.h)) + pad;
  return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
}
function camSet(to) {
  if (camAnim) camAnim.cancel();
  cam = to;
  stage.style.transform = camT(to);
}
async function camera(target, { zoom, pad = 60, max = 2.2, dur = 1000, hold = true } = {}) {
  let to = { x: 0, y: 0, z: 1 };
  if (target !== 'full' && !reduceMotion) {
    const r = rectOf(target, pad);
    to = camFor(r.x + r.w / 2, r.y + r.h / 2, zoom || Math.min(max, 1920 / r.w, 1080 / r.h));
  }
  // start from wherever the camera is right now, even mid-move
  const m = new DOMMatrix(getComputedStyle(stage).transform);
  const from = { x: m.e, y: m.f, z: m.a || 1 };
  const frames = [{ transform: camT(from) }];
  const a = camCenter(from), b = camCenter(to);
  // 1/3 rule: a long pan between two close-ups pulls out through a mid keyframe
  if (from.z > 1.15 && to.z > 1.15 && Math.hypot(a.x - b.x, a.y - b.y) * Math.min(from.z, to.z) > 640) {
    frames.push({ transform: camT(camFor((a.x + b.x) / 2, (a.y + b.y) / 2, Math.max(1, Math.min(from.z, to.z) * 0.7))), offset: 0.5 });
    dur *= 1.25;
  }
  frames.push({ transform: camT(to) });
  if (camAnim) camAnim.cancel();
  cam = to;
  stage.style.transform = camT(to);
  camAnim = dur > 0 ? anim(stage, frames, { duration: dur, easing: EASE.camera }) : null;
  frame.classList.toggle('zoomed', to.z > 1.05);
  if (hold && dur > 0) await wait(dur);
}
// reaction beat: the whole frame breathes in once when the layout changes
function kick() {
  if (reduceMotion) return;
  anim(rig, [{ transform: 'scale(1)' }, { transform: 'scale(1.016)' }, { transform: 'scale(1)' }], { duration: 560, easing: 'ease-in-out' });
}

// ---------------------------------------------------------------- cursor
let cur = { x: 1180, y: 620 }, hovered = null, curAnim = null;
function clearHover() { $$('.stage .hover').forEach((e) => e.classList.remove('hover')); hovered = null; }
async function moveTo(el, { ox = 0.5, oy = 0.5, dx = 0, dy = 0, hover = el } = {}) {
  const b = el.nodeType ? box(el) : el;
  const x = b.x + b.w * ox + dx, y = b.y + b.h * oy + dy;
  const dist = Math.hypot(x - cur.x, y - cur.y);
  const dur = Math.min(950, Math.max(260, 180 + dist * 0.5));
  if (dist > 2) {
    // travel on a gentle arc, never a straight line
    const bend = Math.min(70, dist * 0.12) * (x >= cur.x ? -1 : 1);
    const nx = -(y - cur.y) / dist, ny = (x - cur.x) / dist, pts = [];
    for (let i = 0; i <= 12; i++) {
      const t = i / 12, k = 4 * t * (1 - t) * bend;
      pts.push({ transform: `translate(${cur.x + (x - cur.x) * t + nx * k}px, ${cur.y + (y - cur.y) * t + ny * k}px)` });
    }
    if (curAnim) curAnim.cancel();
    cursorEl.style.transform = `translate(${x}px, ${y}px)`;
    curAnim = anim(cursorEl, pts, { duration: dur, easing: EASE.cursor });
  }
  cur = { x, y };
  await wait(dur);
  if (hovered) hovered.classList.remove('hover');
  hovered = hover && hover.nodeType ? hover : null;
  if (hovered) hovered.classList.add('hover');
}
async function click(el, opts) {
  await moveTo(el, opts);
  await wait(110);
  cursorEl.classList.remove('click'); void cursorEl.offsetWidth; cursorEl.classList.add('click');
  const target = (opts && opts.hover) || el;
  if (target.classList) target.classList.add('press');
  await wait(150);
  if (target.classList) target.classList.remove('press');
  await wait(90);
}
async function typeText(setter, text) {
  let s = '';
  for (const ch of text) {
    s += ch;
    setter(s);
    await wait(ch === ' ' ? 40 : 18 + Math.random() * 26);
  }
}

// ---------------------------------------------------------------- setup
function setup(state) {
  if (flowing) return;
  flowing = true;
  stage.classList.add('instant');
  closeMenus(true);
  clearHover();
  clearTimeout(toastTimer);
  $('#toast').classList.remove('show');
  $('#cInput').classList.remove('focus');
  $$('.stream .w.on').forEach((w) => w.classList.remove('on'));
  answerEl.classList.remove('card-in');
  $('#btnIssues').classList.remove('pulse');
  S = Object.assign(base(), state);
  render();
  void stage.offsetHeight;
  stage.classList.remove('instant');
  camSet({ x: 0, y: 0, z: 1 });
  frame.classList.remove('zoomed');
  // opening dissolve after a start or seek
  anim(rig, [{ opacity: 0, transform: 'scale(1.03)' }, { opacity: 1, transform: 'scale(1)' }], { duration: 650, easing: EASE.enter });
}
const AFTER_ANSWER = { launched: true, sent: true, thinking: true, answered: true, answerFull: true };
// frames the issues panel and the drawing together (stage coordinates)
const PANEL_AND_DRAWING = { x: 52, y: 96, w: 1410, h: 780 };
const PANEL_TOP = { x: 52, y: 52, w: 880, h: 470 };

// ---------------------------------------------------------------- chapters
const CHAPTERS = [
  { title: 'Launch workflow', desc: 'The user opens Drawing Review from Workflows, attaches the drawing set and types a prompt.', async run() {
    setup({});
    await wait(700);
    const wf = $('#railWorkflows');
    camera({ x: 0, y: 60, w: 520, h: 300 }, { dur: 1100, hold: false });
    await click(wf);
    anchorOn(wf);
    const m = showMenu(wf, [
      { id: 'dr', icon: 'drawing', label: 'Drawing Review' },
      { id: 'sr', icon: 'file', label: 'Submittal Review' },
      { id: 'sc', icon: 'checkCircle', label: 'Spec Compliance' },
      { id: 'rfi', icon: 'pencil', label: 'RFI Drafting' },
    ], { place: 'right-start', dx: 10, dy: -6, width: 220 });
    await wait(500);
    await moveTo(mi(m, 'sc'));
    await wait(200);
    await click(mi(m, 'dr'));
    closeMenus();
    S.launched = true; render();
    await camera($('.hero-workflow'), { pad: 170, max: 1.7, dur: 1300 });
    await wait(800);
    await camera($('#composerWrap'), { pad: 110, dur: 1100 });
    const plus = $('#cPlus');
    await click(plus);
    anchorOn(plus);
    const m2 = showMenu(plus, [{ id: 'up', icon: 'upload', label: 'Upload from computer' }, { id: 'drive', icon: 'folder', label: 'Add from Drive' }], { place: 'top-start', dy: 8, width: 230 });
    await wait(400);
    await click(mi(m2, 'drive'));
    closeMenus();
    S.attached = true; render();
    await wait(700);
    await click($('#cInput'), { ox: 0.3 });
    $('#cInput').classList.add('focus');
    await typeText((s) => { S.typed = s; render(); }, PROMPT);
    await wait(450);
    await click($('#send'));
    $('#cInput').classList.remove('focus');
    S.typed = ''; S.attached = false; S.sent = true; render();
    await camera('full', { dur: 1100 });
    S.thinking = true; render();
    await wait(1200);
  } },

  { title: 'Clarifying questions', desc: 'While reasoning, the agent opens a panel with questions, answer options and a field for a custom response.', async run() {
    setup({ launched: true, sent: true, thinking: true });
    await wait(500);
    await camera([$('#thinking'), $('#msgUser')], { pad: 80, max: 1.8, dur: 1100 });
    await wait(500);
    S.q = 1; render();
    await camera([$('#qPanel'), $('#composer')], { pad: 50, dur: 1100 });
    const opts = () => $$('#qPanel .q-opt');
    await moveTo(opts()[3], { ox: 0.4 });
    await wait(350);
    await moveTo(opts()[1], { ox: 0.45 });
    await wait(300);
    await click(opts()[0], { ox: 0.35 });
    opts()[0].classList.add('picked');
    anim(opts()[0], [{ transform: 'scale(1)' }, { transform: 'scale(1.015)' }, { transform: 'scale(1)' }], { duration: 320, easing: EASE.pop });
    await wait(550);
    S.q = 2; render();
    await wait(900);
    await moveTo(opts()[0], { ox: 0.3 });
    await wait(300);
    const custom = $('#qPanel .q-input');
    camera($('#qPanel .q-custom'), { zoom: 2.4, dur: 900, hold: false });
    await click(custom, { ox: 0.15 });
    custom.classList.add('focus');
    $('#qPanel .q-custom').classList.add('typing');
    await typeText((s) => { custom.classList.add('has-text'); $('.qtxt', custom).textContent = s; }, CUSTOM_ANSWER);
    await wait(400);
    await click($('#qPanel .q-send'));
    await wait(250);
    S.q = 0; render();
    await camera('full', { dur: 1000 });
    await wait(500);
  } },

  { title: 'Plan & tasks', desc: 'The agent builds a plan and a task list to fulfil the request, checking tasks off as it goes.', async run() {
    setup({ launched: true, sent: true, thinking: true });
    await wait(600);
    S.tasks = 0; render();
    await camera($('#tasksPanel'), { pad: 90, dur: 1100 });
    for (let k = 1; k <= 5; k++) {
      await wait(k === 1 ? 700 : 850);
      S.tasks = k; render();
      // slow push-in as the plan progresses
      camera($('#tasksPanel'), { pad: 90 - k * 10, dur: 900, hold: false });
    }
    await wait(1000);
    S.tasks = null; render();
    await camera('full', { dur: 1000 });
    await wait(300);
  } },

  { title: 'Drawing Review ready', desc: 'The final response summarises the findings and links the Drawing Review artifact with all identified callouts.', async run() {
    setup({ launched: true, sent: true, thinking: true });
    await wait(500);
    S.answered = true; render();
    camera(answerEl, { pad: 70, max: 1.6, dur: 1200, hold: false });
    await wait(450);
    for (const w of $$('.stream .w', answerEl)) { w.classList.add('on'); await wait(22); }
    await wait(200);
    answerEl.classList.add('card-in');
    await camera($('#artCard'), { zoom: 2.1, dur: 1000 });
    await wait(500);
    S.answerFull = true; render();
    answerEl.classList.remove('card-in');
    enter($('#btnIssues'), 'pulse');
    await camera($('#btnIssues'), { zoom: 2.6, dur: 1200 });
    await wait(900);
  } },

  { title: 'Issues panel', desc: 'The header button on the right opens the issues panel, which slides in on the left.', async run() {
    setup(AFTER_ANSWER);
    await camera($('#btnIssues'), { zoom: 2.6, dur: 0 });
    await wait(600);
    await click($('#btnIssues'));
    S.issues = true; render();
    await camera('full', { dur: 1000 });
    await wait(200);
    const issues = $$('.issue', issueList);
    await camera([$('.ip-head'), ...$$('.issue-card', issueList)], { pad: 30, max: 1.9, dur: 1100 });
    await moveTo($('.i-desc', issues[0]), { ox: 0.5, hover: null });
    await wait(700);
    await click($('.issue-head', issues[1]), { ox: 0.35 });
    S.expanded = 1; render();
    await wait(1300);
    await click($('.issue-head', issues[0]), { ox: 0.35 });
    S.expanded = 0; render();
    await wait(900);
  } },

  { title: 'Issue actions', desc: 'Each finding can be accepted, ignored or pushed to Autodesk, Fieldwire or Bentley ProjectWise.', async run() {
    setup({ ...AFTER_ANSWER, issues: true });
    await wait(400);
    const it = issueList.children[0];
    await camera([it, { x: 360, y: 150, w: 300, h: 200 }], { pad: 40, dur: 1100 });
    const more = $('.i-more', it);
    await click(more);
    anchorOn(more);
    const m = showMenu(more, [
      { id: 'acc', icon: 'check', label: 'Accept finding' },
      { id: 'ign', icon: 'x', label: 'Ignore finding' }, '-',
      { id: 'adsk', brand: 'autodesk', label: 'Create issue in Autodesk' },
      { id: 'fw', brand: 'fieldwire', label: 'Create issue in Fieldwire' },
      { id: 'pw', brand: 'bentley', label: 'Create issue in Bentley ProjectWise' },
    ], { place: 'bottom-start', dy: 6, width: 280 });
    await wait(450);
    await moveTo(mi(m, 'adsk'), { ox: 0.4 });
    await wait(300);
    await moveTo(mi(m, 'pw'), { ox: 0.4 });
    await wait(250);
    await click(mi(m, 'fw'), { ox: 0.4 });
    closeMenus();
    toast('Issue created in Fieldwire · FW-1042');
    await wait(1400);
    await camera([$('.i-actions', it), $('.i-title', it)], { pad: 40, max: 2.5, dur: 1000 });
    await click($('.act-accept', it));
    S.status = { 0: 'accepted' }; render();
    await wait(1400);
    await click($('.act-ignore', it));
    S.status = { 0: 'ignored' }; render();
    await wait(1400);
    await click($('.act-reopen', it));
    S.status = {}; render();
    await wait(700);
    await camera('full', { dur: 1000 });
  } },

  { title: 'Drawing with callouts', desc: 'Opening the artifact shows the drawing with marked callouts, synced to the issue selected in the panel.', async run() {
    setup({ ...AFTER_ANSWER, issues: true });
    await wait(300);
    await camera($('#artCard'), { zoom: 2, dur: 1100 });
    await click($('#artCard'), { ox: 0.3 });
    S.artifact = true; render();
    await camera('full', { dur: 1100 });
    S.marks = true; render();
    await wait(700);
    await camera(PANEL_AND_DRAWING, { dur: 1000 });
    const heads = $$('.issue-head', issueList);
    await click(heads[1], { ox: 0.35 });
    S.expanded = 1; render();
    await wait(1500);
    await click(heads[4], { ox: 0.35 });
    S.expanded = 4; render();
    await wait(800);
    // push into the callout for detail, then back
    await camera([$('.callout', paneA), $('.marker.active', paneA)], { pad: 50, max: 2.2, dur: 1100 });
    await wait(1400);
    await camera(PANEL_AND_DRAWING, { dur: 1100 });
    await click(heads[0], { ox: 0.35 });
    S.expanded = 0; render();
    await wait(1100);
  } },

  { title: 'Filter issues', desc: 'Findings can be filtered by discipline or severity; the list and the drawing markers update together.', async run() {
    setup({ ...AFTER_ANSWER, issues: true, artifact: true, marks: true });
    await wait(300);
    await camera(PANEL_TOP, { dur: 1100 });
    const fb = $('#btnFilter');
    await click(fb);
    anchorOn(fb);
    const m = showMenu(fb, [{ id: 'disc', icon: 'layers', label: 'Discipline', sub: true }, { id: 'sev', icon: 'alert', label: 'Severity', sub: true }], { dx: -40, width: 200 });
    await wait(300);
    await moveTo(mi(m, 'disc'), { ox: 0.4 });
    const sub = showMenu(mi(m, 'disc'), DISCIPLINES.map((d) => ({ id: discKey(d), check: true, label: d })), { place: 'right-start', dx: 12, dy: -6, width: 220 });
    await wait(450);
    for (const k of ['General', 'Hazardous materials', 'Civil']) {
      await click(mi(sub, k), { ox: 0.3 });
      mi(sub, k).classList.add('checked');
      S.filters.push(k); render();
      await wait(300);
    }
    await wait(300);
    await click($('.issues-inner'), { ox: 0.5, oy: 0.93, hover: null });
    closeMenus();
    await camera(PANEL_AND_DRAWING, { dur: 1100 });
    await wait(1200);
    const chip = chipsEl.querySelector('[data-k="General"] .x');
    await camera(chipsEl, { zoom: 2.4, pad: 20, dur: 900 });
    await click(chip);
    S.filters = S.filters.filter((f) => f !== 'General'); render();
    await wait(500);
    await camera(PANEL_AND_DRAWING, { dur: 1000 });
    await wait(1200);
    for (const k of [...S.filters]) {
      await click(chipsEl.querySelector(`[data-k="${k}"] .x`));
      S.filters = S.filters.filter((f) => f !== k); render();
      await wait(350);
    }
    await wait(600);
  } },

  { title: 'Download & history', desc: 'The artifact can be downloaded as Excel or JSON, and its change history browsed by version.', async run() {
    setup({ ...AFTER_ANSWER, issues: true, artifact: true, marks: true });
    await wait(300);
    await camera({ x: 300, y: 60, w: 720, h: 400 }, { dur: 1100 });
    const more = $('#btnMore');
    await click(more);
    anchorOn(more);
    let m = showMenu(more, [{ id: 'dl', icon: 'download', label: 'Download', sub: true }, { id: 'vh', icon: 'history', label: 'Version history', sub: true }], { width: 170 });
    await wait(300);
    await moveTo(mi(m, 'dl'), { ox: 0.4 });
    let sub = showMenu(mi(m, 'dl'), [{ id: 'xls', icon: 'sheet', label: 'Download as Excel' }, { id: 'json', icon: 'braces', label: 'Download as JSON' }], { place: 'right-start', dx: 12, dy: -6, width: 180 });
    await wait(350);
    await moveTo(mi(sub, 'json'), { ox: 0.4 });
    await wait(300);
    await click(mi(sub, 'xls'), { ox: 0.4 });
    closeMenus();
    toast('Drawing Review.xlsx downloaded');
    await wait(1500);
    await click(more);
    anchorOn(more);
    m = showMenu(more, [{ id: 'dl', icon: 'download', label: 'Download', sub: true }, { id: 'vh', icon: 'history', label: 'Version history', sub: true }], { width: 170 });
    await wait(300);
    await moveTo(mi(m, 'dl'), { ox: 0.4 });
    await moveTo(mi(m, 'vh'), { ox: 0.4 });
    sub = showMenu(mi(m, 'vh'), [
      { id: 'v4', tall: true, html: '<span class="vt">Version 4<small>02:36 PM <span class="cur">Current</span></small></span>' },
      { id: 'v3', tall: true, html: '<span class="vt">Version 3<small>01:35 PM</small></span>' },
      { id: 'v2', tall: true, html: '<span class="vt">Version 2<small>12:56 AM</small></span>' },
      { id: 'v1', tall: true, html: '<span class="vt">Version 1<small>09:36 AM</small></span>' },
    ], { place: 'right-start', dx: 12, dy: -6, width: 170 });
    await wait(450);
    await moveTo(mi(sub, 'v2'), { ox: 0.4 });
    await wait(350);
    await click(mi(sub, 'v3'), { ox: 0.4 });
    closeMenus();
    toast('Viewing Version 3 · 01:35 PM');
    await wait(1200);
    await camera('full', { dur: 1000 });
  } },

  { title: 'Issues ↔ Files', desc: 'The selectors at the top of the panel switch the artifact source, or switch between found issues and files in context.', async run() {
    setup({ ...AFTER_ANSWER, issues: true, artifact: true, marks: true });
    await wait(300);
    await camera({ x: 52, y: 52, w: 600, h: 360 }, { dur: 1100 });
    const aSel = $('#artifactSel');
    await click(aSel);
    anchorOn(aSel);
    let m = showMenu(aSel, [{ id: 'dr', icon: 'drawing', label: 'Drawing Review.json', tick: true }, { id: 'sub', icon: 'drawing', label: 'Submittal Review.json' }], { place: 'bottom-end', width: 230 });
    await wait(350);
    await click(mi(m, 'sub'), { ox: 0.4 });
    closeMenus();
    S.dataset = 'sub'; S.expanded = 0; render();
    await camera(PANEL_AND_DRAWING, { dur: 1100 });
    await wait(1200);
    await camera({ x: 52, y: 52, w: 600, h: 360 }, { dur: 1000 });
    await click(aSel);
    anchorOn(aSel);
    m = showMenu(aSel, [{ id: 'dr', icon: 'drawing', label: 'Drawing Review.json' }, { id: 'sub', icon: 'drawing', label: 'Submittal Review.json', tick: true }], { place: 'bottom-end', width: 230 });
    await wait(350);
    await click(mi(m, 'dr'), { ox: 0.4 });
    closeMenus();
    S.dataset = 'dr'; S.expanded = 0; render();
    await wait(900);
    const mSel = $('#modeSel');
    await click(mSel);
    anchorOn(mSel);
    m = showMenu(mSel, [{ id: 'issues', icon: 'flag', label: 'Issues' }, { id: 'files', icon: 'file', label: 'Files' }], { width: 220 });
    await wait(350);
    await click(mi(m, 'files'), { ox: 0.3 });
    closeMenus();
    S.mode = 'files'; render();
    await camera({ x: 52, y: 52, w: 480, h: 560 }, { dur: 1000 });
    await moveTo($('#treeSub'), { ox: 0.3 });
    await wait(500);
    await moveTo($('#treeDR'), { ox: 0.3 });
    await wait(700);
    await click(mSel);
    anchorOn(mSel);
    m = showMenu(mSel, [{ id: 'issues', icon: 'flag', label: 'Issues' }, { id: 'files', icon: 'file', label: 'Files' }], { width: 220 });
    await wait(350);
    await click(mi(m, 'issues'), { ox: 0.3 });
    closeMenus();
    S.mode = 'issues'; render();
    await wait(600);
    await camera('full', { dur: 1000 });
  } },

  { title: 'Split view', desc: 'Split view shows two drawings at once, each with its own tabs and callouts.', async run() {
    setup({ ...AFTER_ANSWER, issues: true, artifact: true, marks: true });
    await wait(300);
    await camera($('.split', paneA), { zoom: 2.4, dur: 1200 });
    await click($('.split', paneA));
    S.split = true; render();
    await camera('full', { dur: 1100 });
    await camera({ x: 200, y: 52, w: 1340, h: 900 }, { zoom: 1.2, dur: 1400 });
    await wait(800);
    await click($$('.issue-head', issueList)[1], { ox: 0.35 });
    S.expanded = 1; render();
    await wait(2000);
    await click($('.pane-close', paneB));
    S.split = false; S.expanded = 0; render();
    kick();
    await camera('full', { dur: 1000 });
  } },

  { title: 'Layout combinations', desc: 'Issues, artifact and chat can be combined freely.', async run() {
    setup({ ...AFTER_ANSWER, issues: true, artifact: true, marks: true });
    const combo = async (text, fn) => { await fn(); toast(text, 1600); await wait(1700); };
    const toggle = async (el, change) => { await click(el); change(); render(); kick(); };
    await combo('Issues panel + artifact + chat.', async () => wait(400));
    await combo('Issues panel + artifact — chat closed.', () => toggle($('#btnChat'), () => { S.chat = false; }));
    await combo('Artifact + chat — issues panel closed.', async () => {
      await toggle($('#btnChat'), () => { S.chat = true; });
      await wait(700);
      await toggle($('#btnIssues'), () => { S.issues = false; });
    });
    await combo('Artifact only — issues panel and chat closed.', () => toggle($('#btnChat'), () => { S.chat = false; }));
    await combo('Issues panel + chat — artifact closed.', async () => {
      await toggle($('#btnIssues'), () => { S.issues = true; });
      await wait(700);
      await toggle($('#btnChat'), () => { S.chat = true; });
      await wait(700);
      await toggle($('.pane-close', paneA), () => { S.artifact = false; });
    });
    await wait(800);
  } },
];

// ---------------------------------------------------------------- player
// One continuous film. Scene lengths (ms at 1×) place each scene on the timeline
// so the scrubber can seek; seeking starts the scene from its opening state.
const SCENE_MS = [25330, 15090, 8320, 10420, 10020, 14490, 15430, 18640, 12260, 18290, 11920, 17330];
const TOTAL = SCENE_MS.reduce((a, b) => a + b, 0);
const sceneStart = (i) => SCENE_MS.slice(0, i).reduce((a, b) => a + b, 0);
let current = 0;
const bar = $('#timeline'), fill = $('#tlFill'), timeEl = $('#tlTime');
const mmss = (ms) => { const t = Math.max(0, Math.round(ms / 1000)); return `${Math.floor(t / 60)}:${String(t % 60).padStart(2, '0')}`; };
(function tick() {
  const t = Math.min(clock, TOTAL);
  fill.style.transform = `scaleX(${t / TOTAL})`;
  timeEl.textContent = `${mmss(t)} / ${mmss(TOTAL)}`;
  bar.setAttribute('aria-valuenow', Math.round(t / 1000));
  requestAnimationFrame(tick);
})();
bar.setAttribute('aria-valuemax', Math.round(TOTAL / 1000));
function seekTo(ms) {
  let i = 0;
  while (i < SCENE_MS.length - 1 && sceneStart(i + 1) <= ms) i++;
  play(i);
}
bar.addEventListener('click', (e) => {
  const r = bar.getBoundingClientRect();
  seekTo(((e.clientX - r.left) / r.width) * TOTAL);
});
bar.addEventListener('keydown', (e) => {
  if (e.key === 'ArrowRight') { e.preventDefault(); play(Math.min(current + 1, SCENE_MS.length - 1)); }
  if (e.key === 'ArrowLeft') { e.preventDefault(); play(Math.max(current - 1, 0)); }
});
const playBtn = $('#playBtn');
function syncPlayBtn() { playBtn.innerHTML = icon(paused ? 'play' : 'pause'); playBtn.setAttribute('aria-label', paused ? 'Play' : 'Pause'); }
// pausing freezes every running CSS and Web Animation, not just the script
let frozen = [];
function setPaused(p) {
  if (p === paused) return;
  paused = p;
  if (p) { frozen = document.getAnimations().filter((a) => a.playState === 'running'); frozen.forEach((a) => a.pause()); }
  else { frozen.forEach((a) => { if (a.playState === 'paused') a.play(); }); frozen = []; }
  syncPlayBtn();
}
playBtn.addEventListener('click', () => setPaused(!paused));
$('#restartBtn').innerHTML = icon('restart');
$('#restartBtn').addEventListener('click', () => play(0));
const SPEEDS = [1, 1.5, 2, 0.75];
$('#speedBtn').addEventListener('click', (e) => {
  speed = SPEEDS[(SPEEDS.indexOf(speed) + 1) % SPEEDS.length];
  e.currentTarget.textContent = speed + '×';
  frame.style.setProperty('--spd', speed);
});
document.addEventListener('keydown', (e) => {
  if (e.key === ' ' && e.target === document.body) { e.preventDefault(); playBtn.click(); }
});

async function play(from) {
  setPaused(false);
  const id = ++RUN;
  flowing = false;
  for (let i = from; i < CHAPTERS.length; i++) {
    current = i;
    clock = sceneStart(i);
    try { await CHAPTERS[i].run(); } catch (e) { if (e instanceof Cancel) return; throw e; }
    if (new URLSearchParams(location.search).has('timing')) console.log('scene', i, Math.round(clock - sceneStart(i)));
    if (id !== RUN) return;
  }
  if (new URLSearchParams(location.search).has('timing')) console.log('end');
  window.__ended = true;
  try { await wait(2200); } catch (e) { return; }
  if (id === RUN) play(0);
}

// ---------------------------------------------------------------- fit stage
function fit() {
  const vw = viewport.clientWidth, vh = viewport.clientHeight, pad = window.__CAPTURE__ ? 0 : vw < 700 ? 8 : 20;
  const k = Math.max(0.1, Math.min((vw - pad * 2) / 1920, (vh - pad * 2) / 1080));
  frame.style.transform = `translate(${(vw - 1920 * k) / 2}px, ${(vh - 1080 * k) / 2}px) scale(${k})`;
}
new ResizeObserver(fit).observe(viewport);
[paneA, paneB].forEach((p) => new ResizeObserver(() => p.classList.toggle('narrow', p.offsetWidth < 640)).observe(p));
fit();

syncPlayBtn();
const params = new URLSearchParams(location.search);
if (params.has('speed')) speed = +params.get('speed') || 1;
play(Math.max(0, Math.min(CHAPTERS.length - 1, (+params.get('scene') || 1) - 1)));
})();
