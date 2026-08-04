(() => {
  'use strict';

  const STORAGE_KEY = 'htolohState_v1';
  const DIFF_POINTS = { easy: 5, medium: 10, hard: 20 };
  const DIFF_LABEL = { easy: 'Easy', medium: 'Medium', hard: 'Hard' };
  const FREQ_LABEL = { daily: 'Daily', weekly: 'Weekly', 'one-time': 'One-time' };
  const WHEEL_COLORS = ['#F97316', '#FB923C', '#FDBA74', '#EA580C', '#2563EB', '#60A5FA', '#FCD34D', '#F59E0B'];
  const MASCOT_ORDER = ['cat', 'plant', 'ghost'];
  const MASCOT_EMOJI = {
    cat: { happy: '😻', neutral: '😼', sad: '🙀' },
    plant: { happy: '🌸', neutral: '🌿', sad: '🥀' },
    ghost: { happy: '👻', neutral: '😶', sad: '💀' },
  };
  const MASCOT_NAME = { cat: 'the house cat', plant: 'the house plant', ghost: 'the house ghost' };
  const SPECIAL_BADGE_NAMES = {
    'wash-dishes': 'Pan King',
    'take-out-trash': 'Trash Can Lord',
    'vacuum': 'Vacuum Vanquisher',
    'clean-bathroom': 'Porcelain Paladin',
    'fold-laundry': 'Fabric Folder Supreme',
    'water-plants': 'Green Thumb',
  };
  const HOUSEMATE_EMOJI_CHOICES = ['🦊', '🐱', '🐶', '🐼', '🐰', '🦁', '🐸', '🐨', '🦄', '🐵', '🦉', '🐷', '🐧', '🐻'];
  const CHORE_EMOJI_CHOICES = ['🍽️', '🗑️', '🧹', '🌿', '🚽', '👕', '🛏️', '🪟', '🍳', '🧺', '🚿', '🧽', '📦', '🐾'];

  // ---------- date helpers ----------
  function todayStr() {
    return dateStr(new Date());
  }
  function dateStr(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
  function addDays(str, n) {
    const [y, m, d] = str.split('-').map(Number);
    const dt = new Date(y, m - 1, d);
    dt.setDate(dt.getDate() + n);
    return dateStr(dt);
  }
  function dayLabel(str) {
    const [y, m, d] = str.split('-').map(Number);
    const dt = new Date(y, m - 1, d);
    return dt.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
  }
  function uid(prefix) {
    return `${prefix}-${Math.random().toString(36).slice(2, 9)}`;
  }

  // ---------- state ----------
  function defaultState() {
    return {
      housemates: [],
      chores: [
        { id: 'wash-dishes', name: 'Wash Dishes', icon: '🍽️', difficulty: 'medium', frequency: 'daily' },
        { id: 'take-out-trash', name: 'Take Out Trash', icon: '🗑️', difficulty: 'easy', frequency: 'daily' },
        { id: 'vacuum', name: 'Vacuum', icon: '🧹', difficulty: 'medium', frequency: 'weekly' },
        { id: 'water-plants', name: 'Water Plants', icon: '🌿', difficulty: 'easy', frequency: 'daily' },
        { id: 'clean-bathroom', name: 'Clean Bathroom', icon: '🚽', difficulty: 'hard', frequency: 'weekly' },
        { id: 'fold-laundry', name: 'Fold Laundry', icon: '👕', difficulty: 'medium', frequency: 'weekly' },
      ],
      assignments: [],
      settings: { mascotType: 'cat', nextIdx: 0 },
    };
  }

  let state = load();

  function load() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return defaultState();
      const parsed = JSON.parse(raw);
      if (!parsed.housemates || !parsed.chores || !parsed.assignments) return defaultState();
      parsed.settings = parsed.settings || { mascotType: 'cat', nextIdx: 0 };
      return parsed;
    } catch (e) {
      return defaultState();
    }
  }

  function save() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }

  // ---------- toast ----------
  let toastTimer = null;
  function toast(msg) {
    const el = document.getElementById('toast');
    el.textContent = msg;
    el.hidden = false;
    requestAnimationFrame(() => el.classList.add('show'));
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      el.classList.remove('show');
      setTimeout(() => { el.hidden = true; }, 250);
    }, 2600);
  }

  // ---------- assignment pool logic ----------
  function dailyAssignedForDate(choreId, date) {
    return state.assignments.some(a => a.choreId === choreId && a.date === date);
  }
  function weeklyAssignedInWindow(choreId, windowStart, windowEnd) {
    return state.assignments.some(a => a.choreId === choreId && a.date >= windowStart && a.date <= windowEnd);
  }
  function oneTimeAssignedEver(choreId) {
    return state.assignments.some(a => a.choreId === choreId);
  }
  function hasAssignmentOnDate(housemateId, date) {
    return state.assignments.some(a => a.housemateId === housemateId && a.date === date);
  }

  function poolForDate(date, windowStart, windowEnd) {
    return state.chores.filter(c => {
      if (c.frequency === 'daily') return !dailyAssignedForDate(c.id, date);
      if (c.frequency === 'weekly') return !weeklyAssignedInWindow(c.id, windowStart, windowEnd);
      if (c.frequency === 'one-time') return !oneTimeAssignedEver(c.id);
      return false;
    });
  }

  function pickHousemate(date) {
    if (state.housemates.length === 0) return null;
    const withoutToday = state.housemates.filter(h => !hasAssignmentOnDate(h.id, date));
    const candidates = withoutToday.length ? withoutToday : state.housemates;
    const idx = state.settings.nextIdx % candidates.length;
    state.settings.nextIdx = (state.settings.nextIdx + 1) % 1000000;
    return candidates[idx];
  }

  function weightedPickChore(pool, housemate) {
    const weights = pool.map(c => {
      if (c.difficulty === 'hard' && housemate.lastDifficulty === 'hard') return 0.25;
      return 1;
    });
    const total = weights.reduce((a, b) => a + b, 0);
    let r = Math.random() * total;
    for (let i = 0; i < pool.length; i++) {
      r -= weights[i];
      if (r <= 0) return i;
    }
    return pool.length - 1;
  }

  function createAssignment(chore, housemate, date) {
    state.assignments.push({
      id: uid('asn'),
      choreId: chore.id,
      housemateId: housemate.id,
      date,
      status: 'pending',
    });
    housemate.lastDifficulty = chore.difficulty;
  }

  // ---------- spirit / morale ----------
  function computeMorale() {
    const today = todayStr();
    const windowStart = addDays(today, -6);
    const relevant = state.assignments.filter(a => a.date >= windowStart && a.date <= today);
    if (relevant.length === 0) return null;
    const done = relevant.filter(a => a.status === 'done').length;
    return Math.round((done / relevant.length) * 100);
  }

  function renderSpirit() {
    const pct = computeMorale();
    const type = state.settings.mascotType;
    const emojiEl = document.getElementById('spiritEmoji');
    const moodEl = document.getElementById('spiritMood');
    const barEl = document.getElementById('spiritBarFill');
    const pctEl = document.getElementById('spiritPct');

    let tier = 'neutral';
    let displayPct = pct === null ? 70 : pct;
    if (pct === null) {
      moodEl.textContent = `Meet ${MASCOT_NAME[type]}! Spin the wheel to get started.`;
    } else if (pct >= 90) {
      tier = 'happy';
      moodEl.textContent = `${capitalize(MASCOT_NAME[type])} is thriving — the house is sparkling! ✨`;
    } else if (pct >= 50) {
      tier = 'neutral';
      moodEl.textContent = `${capitalize(MASCOT_NAME[type])} is doing okay, but could use some help.`;
    } else {
      tier = 'sad';
      moodEl.textContent = `${capitalize(MASCOT_NAME[type])} is begging you to clean up! 🥺`;
    }

    emojiEl.textContent = MASCOT_EMOJI[type][tier];
    barEl.style.width = `${displayPct}%`;
    pctEl.textContent = pct === null ? 'No chores completed yet' : `${pct}% clean (last 7 days)`;
  }

  function capitalize(s) { return s.charAt(0).toUpperCase() + s.slice(1); }

  // ---------- today tasks ----------
  function renderToday() {
    const today = todayStr();
    const container = document.getElementById('todayTasks');
    container.innerHTML = '';

    if (state.housemates.length === 0) {
      container.innerHTML = '<p class="empty-state">Add some housemates in the Manage tab to get started! 🏠</p>';
      return;
    }

    const relevant = state.assignments.filter(a => a.date === today || (a.date < today && a.status === 'pending'));

    if (relevant.length === 0) {
      container.innerHTML = '<p class="empty-state">No tasks yet — hit Spin the Wheel! 🎡</p>';
      return;
    }

    state.housemates.forEach(hm => {
      const mine = relevant.filter(a => a.housemateId === hm.id)
        .sort((a, b) => a.date.localeCompare(b.date));
      if (mine.length === 0) return;

      const group = document.createElement('div');
      group.className = 'task-group';

      const header = document.createElement('div');
      header.className = 'task-group-header';
      header.innerHTML = `
        <div class="avatar">${hm.emoji}</div>
        <div class="task-group-name">${escapeHtml(hm.name)}</div>
        <div class="task-group-points">${hm.points} pts</div>
      `;
      group.appendChild(header);

      mine.forEach(a => {
        const chore = state.chores.find(c => c.id === a.choreId);
        if (!chore) return;
        const overdue = a.date < today && a.status === 'pending';
        const row = document.createElement('div');
        row.className = 'task-row' + (overdue ? ' overdue' : '');
        row.innerHTML = `
          <button class="task-check ${a.status === 'done' ? 'done' : ''}" data-assignment="${a.id}" aria-label="Mark ${escapeHtml(chore.name)} done">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
          </button>
          <div class="task-label">
            <div class="task-name ${a.status === 'done' ? 'done' : ''}">${chore.icon} ${escapeHtml(chore.name)}</div>
            <div class="task-meta">${overdue ? 'Overdue · ' + dayLabel(a.date) : FREQ_LABEL[chore.frequency]}</div>
          </div>
          <div class="task-points">+${DIFF_POINTS[chore.difficulty]}</div>
        `;
        group.appendChild(row);
      });

      container.appendChild(group);
    });

    container.querySelectorAll('.task-check').forEach(btn => {
      btn.addEventListener('click', () => toggleAssignment(btn.dataset.assignment));
    });
  }

  function toggleAssignment(assignmentId) {
    const a = state.assignments.find(x => x.id === assignmentId);
    if (!a) return;
    const chore = state.chores.find(c => c.id === a.choreId);
    const hm = state.housemates.find(h => h.id === a.housemateId);
    const pts = DIFF_POINTS[chore ? chore.difficulty : 'medium'] || 5;
    if (a.status === 'done') {
      a.status = 'pending';
      if (hm) hm.points = Math.max(0, hm.points - pts);
    } else {
      a.status = 'done';
      if (hm) hm.points += pts;
    }
    save();
    renderAll();
  }

  // ---------- week view ----------
  function renderWeek() {
    const table = document.getElementById('weekTable');
    table.innerHTML = '';
    if (state.housemates.length === 0) {
      table.innerHTML = '<tr><td class="empty-state">Add housemates first.</td></tr>';
      return;
    }
    const today = todayStr();
    const days = Array.from({ length: 7 }, (_, i) => addDays(today, i));

    const thead = document.createElement('thead');
    const headRow = document.createElement('tr');
    headRow.innerHTML = '<th>Housemate</th>' + days.map(d => `<th>${dayLabel(d)}</th>`).join('');
    thead.appendChild(headRow);
    table.appendChild(thead);

    const tbody = document.createElement('tbody');
    state.housemates.forEach(hm => {
      const row = document.createElement('tr');
      let cells = `<td>${hm.emoji} ${escapeHtml(hm.name)}</td>`;
      days.forEach(d => {
        const dayAssignments = state.assignments.filter(a => a.housemateId === hm.id && a.date === d);
        if (dayAssignments.length === 0) {
          cells += '<td><span class="week-cell-empty">—</span></td>';
        } else {
          cells += '<td>' + dayAssignments.map(a => {
            const chore = state.chores.find(c => c.id === a.choreId);
            if (!chore) return '';
            return `<span class="week-cell-chore ${a.status === 'done' ? 'done' : ''}">${chore.icon}</span>`;
          }).join('') + '</td>';
        }
      });
      row.innerHTML = cells;
      tbody.appendChild(row);
    });
    table.appendChild(tbody);
  }

  // ---------- leaderboard & badges ----------
  function computeStreak(housemateId) {
    let streak = 0;
    let d = todayStr();
    for (let i = 0; i < 365; i++) {
      const dayAssignments = state.assignments.filter(a => a.housemateId === housemateId && a.date === d);
      if (dayAssignments.length === 0) { d = addDays(d, -1); continue; }
      if (dayAssignments.every(a => a.status === 'done')) { streak++; d = addDays(d, -1); }
      else break;
    }
    return streak;
  }

  function renderLeaderboard() {
    const list = document.getElementById('leaderboardList');
    list.innerHTML = '';
    if (state.housemates.length === 0) {
      list.innerHTML = '<li class="empty-state">No housemates yet.</li>';
    } else {
      const sorted = [...state.housemates].sort((a, b) => b.points - a.points);
      sorted.forEach((hm, i) => {
        const streak = computeStreak(hm.id);
        hm.streak = streak;
        const li = document.createElement('li');
        li.className = 'leaderboard-row';
        li.innerHTML = `
          <div class="leaderboard-rank">${i + 1}</div>
          <div class="avatar">${hm.emoji}</div>
          <div class="leaderboard-name">${escapeHtml(hm.name)}${streak > 0 ? ` <span class="leaderboard-streak">🔥 ${streak}d</span>` : ''}</div>
          <div class="leaderboard-points">${hm.points} pts</div>
        `;
        list.appendChild(li);
      });
    }
    save();
    renderBadges();
  }

  function computeBadges() {
    const counts = {};
    state.assignments.forEach(a => {
      if (a.status !== 'done') return;
      counts[a.choreId] = counts[a.choreId] || {};
      counts[a.choreId][a.housemateId] = (counts[a.choreId][a.housemateId] || 0) + 1;
    });
    const badges = [];
    state.chores.forEach(c => {
      const byHousemate = counts[c.id];
      if (!byHousemate) return;
      const entries = Object.entries(byHousemate).sort((a, b) => b[1] - a[1]);
      if (entries.length === 0) return;
      const [topId, topCount] = entries[0];
      if (topCount <= 0) return;
      if (entries.length > 1 && entries[1][1] === topCount) return;
      const hm = state.housemates.find(h => h.id === topId);
      if (!hm) return;
      const title = SPECIAL_BADGE_NAMES[c.id] || `${c.name} Champion`;
      badges.push({ emoji: c.icon, title, holder: `${hm.emoji} ${hm.name}`, count: topCount });
    });
    return badges;
  }

  function renderBadges() {
    const container = document.getElementById('badgesList');
    const badges = computeBadges();
    if (badges.length === 0) {
      container.innerHTML = '<p class="empty-state">Complete chores to start earning badges! 🏅</p>';
      return;
    }
    container.innerHTML = badges.map(b => `
      <div class="badge">
        <span class="badge-emoji">${b.emoji}</span>
        <span class="badge-title">${escapeHtml(b.title)}</span>
        <span class="badge-holder">— ${escapeHtml(b.holder)} (${b.count})</span>
      </div>
    `).join('');
  }

  // ---------- manage ----------
  function renderManage() {
    const hmList = document.getElementById('housemateList');
    hmList.innerHTML = state.housemates.length
      ? state.housemates.map(hm => `
        <li class="manage-row">
          <div class="avatar">${hm.emoji}</div>
          <div class="manage-row-main">
            <div class="manage-row-title">${escapeHtml(hm.name)}</div>
            <div class="manage-row-sub">${hm.points} pts</div>
          </div>
          <button class="delete-btn" data-remove-housemate="${hm.id}" aria-label="Remove ${escapeHtml(hm.name)}">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"></path><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"></path></svg>
          </button>
        </li>
      `).join('')
      : '<li class="empty-state">No housemates yet.</li>';

    const chList = document.getElementById('choreList');
    chList.innerHTML = state.chores.length
      ? state.chores.map(c => `
        <li class="manage-row">
          <div class="avatar">${c.icon}</div>
          <div class="manage-row-main">
            <div class="manage-row-title">${escapeHtml(c.name)}</div>
            <div class="manage-row-sub">${DIFF_LABEL[c.difficulty]} · ${DIFF_POINTS[c.difficulty]}pt · ${FREQ_LABEL[c.frequency]}</div>
          </div>
          <button class="delete-btn" data-remove-chore="${c.id}" aria-label="Remove ${escapeHtml(c.name)}">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"></path><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"></path></svg>
          </button>
        </li>
      `).join('')
      : '<li class="empty-state">No chores yet.</li>';

    hmList.querySelectorAll('[data-remove-housemate]').forEach(btn => {
      btn.addEventListener('click', () => {
        state.housemates = state.housemates.filter(h => h.id !== btn.dataset.removeHousemate);
        state.assignments = state.assignments.filter(a => a.housemateId !== btn.dataset.removeHousemate);
        save();
        renderAll();
      });
    });
    chList.querySelectorAll('[data-remove-chore]').forEach(btn => {
      btn.addEventListener('click', () => {
        state.chores = state.chores.filter(c => c.id !== btn.dataset.removeChore);
        state.assignments = state.assignments.filter(a => a.choreId !== btn.dataset.removeChore);
        save();
        renderAll();
      });
    });
  }

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  // ---------- wheel ----------
  function buildWheelSegments(items) {
    const svg = document.getElementById('wheelSvg');
    svg.innerHTML = '';
    const n = items.length;
    const cx = 150, cy = 150, r = 145;
    items.forEach((item, i) => {
      const startAngle = (i / n) * 2 * Math.PI - Math.PI / 2;
      const endAngle = ((i + 1) / n) * 2 * Math.PI - Math.PI / 2;
      const x1 = cx + r * Math.cos(startAngle), y1 = cy + r * Math.sin(startAngle);
      const x2 = cx + r * Math.cos(endAngle), y2 = cy + r * Math.sin(endAngle);
      const largeArc = (endAngle - startAngle) > Math.PI ? 1 : 0;
      const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      path.setAttribute('d', `M${cx},${cy} L${x1},${y1} A${r},${r} 0 ${largeArc} 1 ${x2},${y2} Z`);
      path.setAttribute('fill', WHEEL_COLORS[i % WHEEL_COLORS.length]);
      path.setAttribute('stroke', '#fff');
      path.setAttribute('stroke-width', '2');
      svg.appendChild(path);

      const midAngle = (startAngle + endAngle) / 2;
      const lx = cx + (r * 0.62) * Math.cos(midAngle);
      const ly = cy + (r * 0.62) * Math.sin(midAngle);
      const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      text.setAttribute('x', lx);
      text.setAttribute('y', ly);
      text.setAttribute('text-anchor', 'middle');
      text.setAttribute('dominant-baseline', 'middle');
      text.setAttribute('font-size', n > 6 ? '10' : '13');
      text.setAttribute('transform', `rotate(${(midAngle * 180 / Math.PI) + 90}, ${lx}, ${ly})`);
      text.textContent = item.icon;
      svg.appendChild(text);
    });
  }

  function spinWheelTo(n, targetIndex, onDone) {
    const svg = document.getElementById('wheelSvg');
    const segAngle = 360 / n;
    const center = (targetIndex + 0.5) * segAngle;
    const jitter = (Math.random() - 0.5) * (segAngle * 0.6);
    const target = center + jitter;
    const spins = 5;
    const rotationDelta = spins * 360 + ((360 - target) % 360);
    const prev = parseFloat(svg.dataset.rotation || '0') % 360;

    svg.style.transition = 'none';
    svg.style.transform = `rotate(${prev}deg)`;
    void svg.offsetWidth;
    svg.style.transition = '';

    requestAnimationFrame(() => {
      const finalRotation = prev + rotationDelta;
      svg.style.transform = `rotate(${finalRotation}deg)`;
      svg.dataset.rotation = String(finalRotation % 360);
    });

    svg.addEventListener('transitionend', onDone, { once: true });
  }

  let spinning = false;

  function spinOnce() {
    if (spinning) return;
    if (state.housemates.length === 0) { toast('Add a housemate first! 🙋'); return; }
    const today = todayStr();
    const windowEnd = addDays(today, 6);
    const pool = poolForDate(today, today, windowEnd);
    if (pool.length === 0) { toast('All chores are already assigned! 🎉'); return; }

    const housemate = pickHousemate(today);
    const choreIndex = weightedPickChore(pool, housemate);
    const chore = pool[choreIndex];

    spinning = true;
    document.getElementById('wheelStage').hidden = false;
    document.getElementById('wheelResult').hidden = true;
    document.getElementById('spinOneBtn').disabled = true;
    document.getElementById('spinWeekBtn').disabled = true;
    buildWheelSegments(pool);

    document.getElementById('wheelStage').scrollIntoView({ behavior: 'smooth', block: 'center' });

    spinWheelTo(pool.length, choreIndex, () => {
      createAssignment(chore, housemate, today);
      save();
      const resultEl = document.getElementById('wheelResult');
      resultEl.hidden = false;
      resultEl.textContent = `🎉 ${chore.icon} ${chore.name} → ${housemate.emoji} ${housemate.name}!`;
      spinning = false;
      document.getElementById('spinOneBtn').disabled = false;
      document.getElementById('spinWeekBtn').disabled = false;
      renderAll();
    });
  }

  function spinWholeWeek() {
    if (state.housemates.length === 0) { toast('Add a housemate first! 🙋'); return; }
    const today = todayStr();
    const windowEnd = addDays(today, 6);
    let assignedCount = 0;

    for (let offset = 0; offset < 7; offset++) {
      const date = addDays(today, offset);
      let guard = 0;
      while (guard < 50) {
        guard++;
        const pool = poolForDate(date, today, windowEnd);
        if (pool.length === 0) break;
        const housemate = pickHousemate(date);
        if (!housemate) break;
        const choreIndex = weightedPickChore(pool, housemate);
        const chore = pool[choreIndex];
        createAssignment(chore, housemate, date);
        assignedCount++;
        if (!state.chores.some(c => c.frequency === 'daily' && !dailyAssignedForDate(c.id, date))
          && !state.chores.some(c => c.frequency === 'weekly' && !weeklyAssignedInWindow(c.id, today, windowEnd))
          && !state.chores.some(c => c.frequency === 'one-time' && !oneTimeAssignedEver(c.id))) {
          break;
        }
      }
    }

    save();
    renderAll();
    if (assignedCount === 0) {
      toast('Nothing left to assign this week! 🎉');
    } else {
      toast(`This week's schedule is ready! 🗓️ (${assignedCount} tasks assigned)`);
      switchTab('week');
    }
  }

  // ---------- tabs ----------
  function switchTab(name) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === name));
    document.querySelectorAll('.tab-panel').forEach(p => p.classList.toggle('active', p.id === `panel-${name}`));
  }

  // ---------- render all ----------
  function renderAll() {
    renderSpirit();
    renderToday();
    renderWeek();
    renderLeaderboard();
    renderManage();
  }

  // ---------- init ----------
  function populateSelect(selectEl, options) {
    selectEl.innerHTML = options.map(o => `<option value="${o}">${o}</option>`).join('');
  }

  function init() {
    populateSelect(document.getElementById('housemateEmoji'), HOUSEMATE_EMOJI_CHOICES);
    populateSelect(document.getElementById('choreEmoji'), CHORE_EMOJI_CHOICES);

    document.querySelectorAll('.tab-btn').forEach(btn => {
      btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });

    document.getElementById('spinOneBtn').addEventListener('click', spinOnce);
    document.getElementById('spinWeekBtn').addEventListener('click', spinWholeWeek);
    document.getElementById('closeWheelBtn').addEventListener('click', () => {
      document.getElementById('wheelStage').hidden = true;
    });

    document.getElementById('mascotBtn').addEventListener('click', () => {
      const idx = MASCOT_ORDER.indexOf(state.settings.mascotType);
      state.settings.mascotType = MASCOT_ORDER[(idx + 1) % MASCOT_ORDER.length];
      save();
      renderSpirit();
      toast(`House spirit is now ${MASCOT_NAME[state.settings.mascotType]}!`);
    });

    document.getElementById('housemateForm').addEventListener('submit', e => {
      e.preventDefault();
      const nameInput = document.getElementById('housemateName');
      const emojiInput = document.getElementById('housemateEmoji');
      const name = nameInput.value.trim();
      if (!name) return;
      state.housemates.push({ id: uid('hm'), name, emoji: emojiInput.value, points: 0, streak: 0 });
      save();
      nameInput.value = '';
      renderAll();
      toast(`${name} joined the house! 🎉`);
    });

    document.getElementById('choreForm').addEventListener('submit', e => {
      e.preventDefault();
      const nameInput = document.getElementById('choreName');
      const emojiInput = document.getElementById('choreEmoji');
      const diffInput = document.getElementById('choreDifficulty');
      const freqInput = document.getElementById('choreFrequency');
      const name = nameInput.value.trim();
      if (!name) return;
      state.chores.push({
        id: uid('chore'),
        name,
        icon: emojiInput.value,
        difficulty: diffInput.value,
        frequency: freqInput.value,
      });
      save();
      nameInput.value = '';
      renderAll();
      toast(`Added "${name}" to the chore list!`);
    });

    renderAll();
  }

  document.addEventListener('DOMContentLoaded', init);
})();
