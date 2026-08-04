(() => {
  'use strict';

  const STORAGE_KEY = 'htolohState_v1';
  const DIFF_POINTS = { easy: 5, medium: 10, hard: 20 };
  const WHEEL_COLORS = ['#63E6BE', '#FFD43D', '#FF6B9D', '#4D8EFF', '#9B5CFF', '#FF8C42', '#38D9C9', '#FF6B6B'];
  const ACCENT_PALETTE = ['#FF6B6B', '#FFB020', '#34D399', '#4D8EFF', '#C77DFF', '#FF5CA8', '#38B6FF', '#FFD43D', '#2DD4C8', '#FF8A5C'];
  const MASCOT_ORDER = ['cat', 'plant', 'ghost'];
  const MASCOT_EMOJI = {
    cat: { happy: '😻', neutral: '😼', sad: '🙀' },
    plant: { happy: '🌸', neutral: '🌿', sad: '🥀' },
    ghost: { happy: '👻', neutral: '😶', sad: '💀' },
  };
  const HOUSEMATE_EMOJI_CHOICES = ['🦊', '🐱', '🐶', '🐼', '🐰', '🦁', '🐸', '🐨', '🦄', '🐵', '🦉', '🐷', '🐧', '🐻'];
  const CHORE_EMOJI_CHOICES = ['🍽️', '🗑️', '🧹', '🌿', '🚽', '👕', '🛏️', '🪟', '🍳', '🧺', '🚿', '🧽', '📦', '🐾'];

  // ---------- i18n ----------
  const I18N = {
    en: {
      locale: 'en-US',
      tabDashboard: 'Dashboard', tabWeek: 'Week', tabLeaderboard: 'Ranks', tabManage: 'Manage',
      spinOne: 'Spin the Wheel', spinWeek: 'Spin for the Whole Week', close: 'Close',
      todaysTasks: "Today's Tasks", weekSchedule: "This Week's Schedule",
      leaderboard: 'Leaderboard', badges: 'Badges', housemates: 'Housemates', chores: 'Chores',
      namePlaceholder: 'Name', chorePlaceholder: 'Chore name', add: 'Add',
      optEasy: 'Easy · 5pt', optMedium: 'Medium · 10pt', optHard: 'Hard · 20pt',
      optDaily: 'Daily', optWeekly: 'Weekly', optOneTime: 'One-time',
      diffLabel: { easy: 'Easy', medium: 'Medium', hard: 'Hard' },
      freqLabel: { daily: 'Daily', weekly: 'Weekly', 'one-time': 'One-time' },
      overdue: 'Overdue', pts: 'pts', championSuffix: 'Champion',
      emptyHousemates: 'No housemates yet.', emptyChores: 'No chores yet.',
      emptyTasksNoHousemates: 'Add some housemates in the Manage tab to get started! 🏠',
      emptyTasksNone: 'No tasks yet — hit Spin the Wheel! 🎡',
      emptyBadges: 'Complete chores to start earning badges! 🏅',
      noChoresCompleted: 'No chores completed yet',
      moralePctSuffix: 'clean (last 7 days)',
      mascotName: { cat: 'the house cat', plant: 'the house plant', ghost: 'the house ghost' },
      moodIntro: name => `Meet ${name}! Spin the wheel to get started.`,
      moodHappy: name => `${cap(name)} is thriving — the house is sparkling! ✨`,
      moodNeutral: name => `${cap(name)} is doing okay, but could use some help.`,
      moodSad: name => `${cap(name)} is begging you to clean up! 🥺`,
      toastAddHousemateFirst: 'Add a housemate first! 🙋',
      toastAllAssigned: 'All chores are already assigned! 🎉',
      toastNothingWeek: 'Nothing left to assign this week! 🎉',
      toastWeekReady: n => `This week's schedule is ready! 🗓️ (${n} tasks assigned)`,
      toastHousemateJoined: name => `${name} joined the house! 🎉`,
      toastChoreAdded: name => `Added "${name}" to the chore list!`,
      toastMascotChanged: name => `House spirit is now ${name}!`,
      spinResult: (ci, cn, he, hn) => `🎉 ${ci} ${cn} → ${he} ${hn}!`,
      choreNames: {
        'wash-dishes': 'Wash Dishes', 'take-out-trash': 'Take Out Trash', 'vacuum': 'Vacuum',
        'water-plants': 'Water Plants', 'clean-bathroom': 'Clean Bathroom', 'fold-laundry': 'Fold Laundry',
      },
      specialBadges: {
        'wash-dishes': 'Pan King', 'take-out-trash': 'Trash Can Lord', 'vacuum': 'Vacuum Vanquisher',
        'clean-bathroom': 'Porcelain Paladin', 'fold-laundry': 'Fabric Folder Supreme', 'water-plants': 'Green Thumb',
      },
    },
    uk: {
      locale: 'uk-UA',
      tabDashboard: 'Панель', tabWeek: 'Тиждень', tabLeaderboard: 'Рейтинг', tabManage: 'Керування',
      spinOne: 'Крутити колесо', spinWeek: 'Крутити на весь тиждень', close: 'Закрити',
      todaysTasks: 'Завдання на сьогодні', weekSchedule: 'Розклад на тиждень',
      leaderboard: 'Рейтинг', badges: 'Значки', housemates: 'Мешканці', chores: 'Завдання',
      namePlaceholder: "Ім'я", chorePlaceholder: 'Назва завдання', add: 'Додати',
      optEasy: 'Легко · 5б', optMedium: 'Середньо · 10б', optHard: 'Складно · 20б',
      optDaily: 'Щодня', optWeekly: 'Щотижня', optOneTime: 'Одноразово',
      diffLabel: { easy: 'Легко', medium: 'Середньо', hard: 'Складно' },
      freqLabel: { daily: 'Щодня', weekly: 'Щотижня', 'one-time': 'Одноразово' },
      overdue: 'Прострочено', pts: 'балів', championSuffix: 'Чемпіон',
      emptyHousemates: 'Мешканців ще немає.', emptyChores: 'Завдань ще немає.',
      emptyTasksNoHousemates: 'Додайте мешканців на вкладці «Керування», щоб почати! 🏠',
      emptyTasksNone: 'Завдань ще немає — натисніть «Крутити колесо»! 🎡',
      emptyBadges: 'Виконуйте завдання, щоб отримувати значки! 🏅',
      noChoresCompleted: 'Ще жодного завдання не виконано',
      moralePctSuffix: 'чистоти (останні 7 днів)',
      mascotName: { cat: 'домашній кіт', plant: 'домашня рослина', ghost: 'домашній привид' },
      moodIntro: name => `Знайомтесь із ${name}! Крутіть колесо, щоб почати.`,
      moodHappy: name => `${cap(name)} процвітає — у домі аж блищить! ✨`,
      moodNeutral: name => `${cap(name)} почувається непогано, але не завадить допомога.`,
      moodSad: name => `${cap(name)} благає прибратися! 🥺`,
      toastAddHousemateFirst: 'Спочатку додайте мешканця! 🙋',
      toastAllAssigned: 'Усі завдання вже розподілено! 🎉',
      toastNothingWeek: 'На цей тиждень більше нічого розподіляти! 🎉',
      toastWeekReady: n => `Розклад на тиждень готовий! 🗓️ (завдань розподілено: ${n})`,
      toastHousemateJoined: name => `${name} приєднався(лась) до дому! 🎉`,
      toastChoreAdded: name => `Додано «${name}» до списку завдань!`,
      toastMascotChanged: name => `Тепер дух дому — ${name}!`,
      spinResult: (ci, cn, he, hn) => `🎉 ${ci} ${cn} → ${he} ${hn}!`,
      choreNames: {
        'wash-dishes': 'Мити посуд', 'take-out-trash': 'Виносити сміття', 'vacuum': 'Пилососити',
        'water-plants': 'Поливати рослини', 'clean-bathroom': 'Прибирати ванну', 'fold-laundry': 'Складати білизну',
      },
      specialBadges: {
        'wash-dishes': 'Король Каструль', 'take-out-trash': 'Володар Сміттєвого Бака', 'vacuum': 'Переможець Пилососа',
        'clean-bathroom': 'Ванний Паладин', 'fold-laundry': 'Майстер Складання', 'water-plants': 'Зелений Палець',
      },
    },
  };

  function cap(s) { return s.charAt(0).toUpperCase() + s.slice(1); }
  function lang() { return state.settings.lang || 'en'; }
  function T() { return I18N[lang()]; }
  function choreName(c) { return T().choreNames[c.id] || c.name; }

  function colorForId(id) {
    let hash = 0;
    for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
    return ACCENT_PALETTE[hash % ACCENT_PALETTE.length];
  }

  function pillClass(kind) {
    return { easy: 'pill-easy', medium: 'pill-medium', hard: 'pill-hard' }[kind] || 'pill-neutral';
  }

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
    return dt.toLocaleDateString(T().locale, { weekday: 'short', month: 'short', day: 'numeric' });
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
      settings: { mascotType: 'cat', nextIdx: 0, lang: 'en' },
    };
  }

  let state = load();

  function load() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return defaultState();
      const parsed = JSON.parse(raw);
      if (!parsed.housemates || !parsed.chores || !parsed.assignments) return defaultState();
      parsed.settings = parsed.settings || { mascotType: 'cat', nextIdx: 0, lang: 'en' };
      if (!parsed.settings.lang) parsed.settings.lang = 'en';
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

    const mascotName = T().mascotName[type];
    let tier = 'neutral';
    let displayPct = pct === null ? 70 : pct;
    if (pct === null) {
      moodEl.textContent = T().moodIntro(mascotName);
    } else if (pct >= 90) {
      tier = 'happy';
      moodEl.textContent = T().moodHappy(mascotName);
    } else if (pct >= 50) {
      tier = 'neutral';
      moodEl.textContent = T().moodNeutral(mascotName);
    } else {
      tier = 'sad';
      moodEl.textContent = T().moodSad(mascotName);
    }

    emojiEl.textContent = MASCOT_EMOJI[type][tier];
    barEl.style.width = `${displayPct}%`;
    pctEl.textContent = pct === null ? T().noChoresCompleted : `${pct}% ${T().moralePctSuffix}`;
  }

  // ---------- today tasks ----------
  function renderToday() {
    const today = todayStr();
    const container = document.getElementById('todayTasks');
    container.innerHTML = '';

    if (state.housemates.length === 0) {
      container.innerHTML = `<p class="empty-state">${T().emptyTasksNoHousemates}</p>`;
      return;
    }

    const relevant = state.assignments.filter(a => a.date === today || (a.date < today && a.status === 'pending'));

    if (relevant.length === 0) {
      container.innerHTML = `<p class="empty-state">${T().emptyTasksNone}</p>`;
      return;
    }

    state.housemates.forEach(hm => {
      const mine = relevant.filter(a => a.housemateId === hm.id)
        .sort((a, b) => a.date.localeCompare(b.date));
      if (mine.length === 0) return;

      const hmColor = colorForId(hm.id);
      const group = document.createElement('div');
      group.className = 'task-group glow-card';
      group.style.setProperty('--glow-color', `${hmColor}26`);

      const header = document.createElement('div');
      header.className = 'task-group-header';
      header.innerHTML = `
        <div class="avatar" style="--avatar-color:${hmColor}">${hm.emoji}</div>
        <div class="task-group-name">${escapeHtml(hm.name)}</div>
        <div class="task-group-points">${hm.points} ${T().pts}</div>
      `;
      group.appendChild(header);

      mine.forEach(a => {
        const chore = state.chores.find(c => c.id === a.choreId);
        if (!chore) return;
        const cName = choreName(chore);
        const overdue = a.date < today && a.status === 'pending';
        const row = document.createElement('div');
        row.className = 'task-row' + (overdue ? ' overdue' : '');
        const metaPill = overdue
          ? `<span class="pill pill-overdue">${T().overdue} · ${dayLabel(a.date)}</span>`
          : `<span class="pill ${pillClass(chore.difficulty)}">${T().freqLabel[chore.frequency]}</span>`;
        row.innerHTML = `
          <button class="task-check ${a.status === 'done' ? 'done' : ''}" data-assignment="${a.id}" aria-label="${escapeHtml(cName)}">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
          </button>
          <div class="task-label">
            <div class="task-name ${a.status === 'done' ? 'done' : ''}">${chore.icon} ${escapeHtml(cName)}</div>
            <div class="task-meta">${metaPill}</div>
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
      table.innerHTML = `<tr><td class="empty-state">${T().emptyHousemates}</td></tr>`;
      return;
    }
    const today = todayStr();
    const days = Array.from({ length: 7 }, (_, i) => addDays(today, i));

    const thead = document.createElement('thead');
    const headRow = document.createElement('tr');
    headRow.innerHTML = `<th>${T().housemates}</th>` + days.map(d => `<th>${dayLabel(d)}</th>`).join('');
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
      list.innerHTML = `<li class="empty-state">${T().emptyHousemates}</li>`;
    } else {
      const sorted = [...state.housemates].sort((a, b) => b.points - a.points);
      sorted.forEach((hm, i) => {
        const streak = computeStreak(hm.id);
        hm.streak = streak;
        const hmColor = colorForId(hm.id);
        const li = document.createElement('li');
        li.className = 'leaderboard-row glow-card';
        li.style.setProperty('--glow-color', `${hmColor}22`);
        li.innerHTML = `
          <div class="leaderboard-rank">${i + 1}</div>
          <div class="avatar" style="--avatar-color:${hmColor}">${hm.emoji}</div>
          <div class="leaderboard-name">${escapeHtml(hm.name)}${streak > 0 ? ` <span class="leaderboard-streak">🔥 ${streak}d</span>` : ''}</div>
          <div class="leaderboard-points">${hm.points} ${T().pts}</div>
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
      const title = T().specialBadges[c.id] || `${choreName(c)} ${T().championSuffix}`;
      badges.push({ emoji: c.icon, title, holder: `${hm.emoji} ${hm.name}`, count: topCount });
    });
    return badges;
  }

  function renderBadges() {
    const container = document.getElementById('badgesList');
    const badges = computeBadges();
    if (badges.length === 0) {
      container.innerHTML = `<p class="empty-state">${T().emptyBadges}</p>`;
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
          <div class="avatar" style="--avatar-color:${colorForId(hm.id)}">${hm.emoji}</div>
          <div class="manage-row-main">
            <div class="manage-row-title">${escapeHtml(hm.name)}</div>
            <div class="manage-row-sub">${hm.points} ${T().pts}</div>
          </div>
          <button class="delete-btn" data-remove-housemate="${hm.id}" aria-label="Remove ${escapeHtml(hm.name)}">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"></path><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"></path></svg>
          </button>
        </li>
      `).join('')
      : `<li class="empty-state">${T().emptyHousemates}</li>`;

    const chList = document.getElementById('choreList');
    chList.innerHTML = state.chores.length
      ? state.chores.map(c => `
        <li class="manage-row">
          <div class="avatar" style="--avatar-color:${colorForId(c.id)}">${c.icon}</div>
          <div class="manage-row-main">
            <div class="manage-row-title">${escapeHtml(choreName(c))}</div>
            <div class="manage-row-sub">
              <span class="pill ${pillClass(c.difficulty)}">${T().diffLabel[c.difficulty]} · ${DIFF_POINTS[c.difficulty]}pt</span>
              <span class="pill pill-neutral">${T().freqLabel[c.frequency]}</span>
            </div>
          </div>
          <button class="delete-btn" data-remove-chore="${c.id}" aria-label="Remove ${escapeHtml(choreName(c))}">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"></path><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"></path></svg>
          </button>
        </li>
      `).join('')
      : `<li class="empty-state">${T().emptyChores}</li>`;

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

    const bg = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    bg.setAttribute('cx', cx); bg.setAttribute('cy', cy); bg.setAttribute('r', r);
    bg.setAttribute('fill', '#16171D');
    svg.appendChild(bg);

    items.forEach((item, i) => {
      const startAngle = (i / n) * 2 * Math.PI - Math.PI / 2;
      const endAngle = ((i + 1) / n) * 2 * Math.PI - Math.PI / 2;
      const x1 = cx + r * Math.cos(startAngle), y1 = cy + r * Math.sin(startAngle);
      const x2 = cx + r * Math.cos(endAngle), y2 = cy + r * Math.sin(endAngle);
      const largeArc = (endAngle - startAngle) > Math.PI ? 1 : 0;
      const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      path.setAttribute('d', `M${cx},${cy} L${x1},${y1} A${r},${r} 0 ${largeArc} 1 ${x2},${y2} Z`);
      path.setAttribute('fill', WHEEL_COLORS[i % WHEEL_COLORS.length]);
      path.setAttribute('stroke', '#0A0A0D');
      path.setAttribute('stroke-width', '2');
      svg.appendChild(path);

      const midAngle = (startAngle + endAngle) / 2;
      const lx = cx + (r * 0.6) * Math.cos(midAngle);
      const ly = cy + (r * 0.6) * Math.sin(midAngle);
      const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      text.setAttribute('x', lx);
      text.setAttribute('y', ly);
      text.setAttribute('text-anchor', 'middle');
      text.setAttribute('dominant-baseline', 'middle');
      text.setAttribute('font-size', n > 6 ? '15' : '19');
      text.setAttribute('transform', `rotate(${(midAngle * 180 / Math.PI) + 90}, ${lx}, ${ly})`);
      text.textContent = item.icon;
      svg.appendChild(text);

      // rim dot marker at each segment boundary
      const dot = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
      dot.setAttribute('cx', x1);
      dot.setAttribute('cy', y1);
      dot.setAttribute('r', '4.5');
      dot.setAttribute('fill', '#fff');
      dot.setAttribute('opacity', '0.85');
      svg.appendChild(dot);
    });

    const hub = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    hub.setAttribute('cx', cx); hub.setAttribute('cy', cy); hub.setAttribute('r', '14');
    hub.setAttribute('fill', '#fff');
    hub.setAttribute('stroke', '#0A0A0D');
    hub.setAttribute('stroke-width', '3');
    svg.appendChild(hub);
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
    if (state.housemates.length === 0) { toast(T().toastAddHousemateFirst); return; }
    const today = todayStr();
    const windowEnd = addDays(today, 6);
    const pool = poolForDate(today, today, windowEnd);
    if (pool.length === 0) { toast(T().toastAllAssigned); return; }

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
      resultEl.textContent = T().spinResult(chore.icon, choreName(chore), housemate.emoji, housemate.name);
      spinning = false;
      document.getElementById('spinOneBtn').disabled = false;
      document.getElementById('spinWeekBtn').disabled = false;
      renderAll();
    });
  }

  function spinWholeWeek() {
    if (state.housemates.length === 0) { toast(T().toastAddHousemateFirst); return; }
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
      toast(T().toastNothingWeek);
    } else {
      toast(T().toastWeekReady(assignedCount));
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
      toast(T().toastMascotChanged(T().mascotName[state.settings.mascotType]));
    });

    document.getElementById('langBtn').addEventListener('click', () => {
      state.settings.lang = lang() === 'en' ? 'uk' : 'en';
      save();
      applyStaticI18n();
      renderAll();
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
      toast(T().toastHousemateJoined(name));
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
      toast(T().toastChoreAdded(name));
    });

    applyStaticI18n();
    renderAll();
  }

  function applyStaticI18n() {
    document.documentElement.lang = lang();
    document.querySelectorAll('[data-i18n]').forEach(el => {
      const key = el.dataset.i18n;
      if (T()[key] !== undefined) el.textContent = T()[key];
    });
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
      const key = el.dataset.i18nPlaceholder;
      if (T()[key] !== undefined) el.placeholder = T()[key];
    });
    document.getElementById('langBtn').textContent = lang() === 'en' ? 'EN' : 'UA';
  }

  document.addEventListener('DOMContentLoaded', init);
})();
