// Learner profile persisted in localStorage (per browser). Every access is
// guarded: storage can be unavailable (private mode, previews).

const KEY = 'lvivdrive.profile.v1';

const EMPTY = { quests: {}, exams: {}, drills: {}, theory: null, inspection: null, lamps: null, km: 0, history: [] };

export function loadProfile() {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...EMPTY, ...JSON.parse(raw) };
  } catch {
    // storage unavailable
  }
  return structuredClone(EMPTY);
}

export function saveProfile(p) {
  try { localStorage.setItem(KEY, JSON.stringify(p)); } catch { /* ignore */ }
}

export function recordResult(p, kind, id, result) {
  const entry = { kind, id, at: new Date().toISOString(), ...result };
  p.history = [entry, ...(p.history || [])].slice(0, 40);
  if (kind === 'exam') {
    const prev = p.exams[id];
    if (!prev || result.score > prev.score || (result.passed && !prev.passed)) p.exams[id] = { score: result.score, passed: result.passed };
  } else if (kind === 'drill') {
    const prev = p.drills[id];
    if (!prev || result.score > prev.score) p.drills[id] = { score: result.score, passed: result.passed };
  } else if (kind === 'quest') {
    p.quests[id] = true;
  } else if (['theory', 'inspection', 'lamps'].includes(kind)) {
    const prev = p[kind];
    if (!prev || result.score > prev.score) p[kind] = { score: result.score, passed: result.passed };
  }
  saveProfile(p);
}

// 0..100 readiness: theory, technical, drills, exam routes and quests.
export function readiness(p, counts) {
  const parts = [
    p.theory?.passed ? 1 : p.theory ? 0.4 : 0,
    p.inspection?.passed ? 1 : p.inspection ? 0.4 : 0,
    p.lamps?.passed ? 1 : p.lamps ? 0.4 : 0,
    Object.values(p.drills).filter((d) => d.passed).length / counts.drills,
    Object.values(p.exams).filter((d) => d.passed).length / counts.exams,
    Object.keys(p.quests).length / counts.quests,
  ];
  return Math.round((parts.reduce((a, b) => a + b, 0) / parts.length) * 100);
}
