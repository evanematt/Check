// Violation catalogue and grading used by every game mode.
//
// References point to the Правила дорожнього руху України (постанова КМУ
// №1306 від 10.10.2001, чинна редакція). Where only a chapter is given, the
// exact paragraph should be checked on zakon.rada.gov.ua — the game cites the
// chapter rather than risk a wrong paragraph number. Fines are deliberately
// not shown: amounts in КУпАП change and a pending bill (№15348) would alter
// the speeding thresholds if adopted.

export const RULES_VERSION = {
  label: 'ПДР України, редакція станом на 2026 р.',
  checked: '2026-10',
  source: 'https://zakon.rada.gov.ua/laws/show/1306-2001-п',
};

// severity: minor (-2) | medium (-5) | major (-10) | critical (exam fail, -25)
export const SEVERITY = {
  minor: { points: 2, label: 'Незначна', color: '#f5c542' },
  medium: { points: 5, label: 'Середня', color: '#ff9a3c' },
  major: { points: 10, label: 'Груба', color: '#ff5a4f' },
  critical: { points: 25, label: 'Критична — іспит не складено', color: '#d0021b' },
};

export const VIOLATIONS = {
  SPEEDING_10: {
    severity: 'minor', title: 'Перевищення швидкості (до 10 км/год)', ref: 'Розділ 12, п. 12.4',
    tip: 'У населеному пункті — не більше 50 км/год, якщо знаки не встановлюють іншого.',
  },
  SPEEDING_20: {
    severity: 'medium', title: 'Перевищення швидкості (10–20 км/год)', ref: 'Розділ 12',
    tip: 'Стежте за знаками 3.29 та житловою зоною (знак 5.31): там не більше 20 км/год (розділ 26).',
  },
  SPEEDING_40: {
    severity: 'major', title: 'Перевищення швидкості більш ніж на 20 км/год', ref: 'Розділ 12; ст. 122 КУпАП',
    tip: 'Перевищення понад 20 км/год тягне адміністративну відповідальність.',
  },
  SPEEDING_CRIT: {
    severity: 'critical', title: 'Перевищення швидкості більш ніж на 40 км/год', ref: 'Розділ 12; ст. 122 КУпАП',
    tip: 'Таке перевищення створює аварійну обстановку.',
  },
  RED_LIGHT: {
    severity: 'critical', title: 'Проїзд на заборонний сигнал світлофора', ref: 'Розділ 8, п. 8.7',
    tip: 'Червоний сигнал (у т.ч. разом із жовтим) забороняє рух. Зупиніться перед стоп-лінією.',
  },
  YELLOW_LIGHT: {
    severity: 'major', title: 'Проїзд на жовтий, коли можна було зупинитися', ref: 'Розділ 8, п. 8.7',
    tip: 'Жовтий забороняє рух, якщо зупинитися можна без екстреного гальмування.',
  },
  STOP_SIGN: {
    severity: 'major', title: 'Не зупинилися перед знаком 2.2 «Проїзд без зупинки заборонено»', ref: 'Розділ 33 (знак 2.2), розділ 16',
    tip: 'Повна зупинка перед стоп-лінією, потім — дати дорогу.',
  },
  NO_YIELD: {
    severity: 'major', title: 'Не надали перевагу в русі', ref: 'Розділ 16',
    tip: 'На другорядній дорозі та на рівнозначному перехресті — дайте дорогу тим, хто має перевагу (на рівнозначному — тим, хто праворуч).',
  },
  NO_YIELD_LEFT: {
    severity: 'major', title: 'Поворот ліворуч без надання дороги зустрічним', ref: 'Розділ 16',
    tip: 'Повертаючи ліворуч, дайте дорогу зустрічним, що рухаються прямо або праворуч.',
  },
  CROSSWALK: {
    severity: 'major', title: 'Не дали дорогу пішоходу на переході', ref: 'Розділ 18, п. 18.1',
    tip: 'На нерегульованому переході водій зобов\'язаний зупинитися і пропустити пішоходів.',
  },
  PED_HIT: {
    severity: 'critical', title: 'Наїзд на пішохода', ref: 'Розділ 18; розділ 2',
    tip: 'Знижуйте швидкість біля переходів і будьте готові зупинитися.',
  },
  WRONG_WAY: {
    severity: 'critical', title: 'Рух назустріч на дорозі з одностороннім рухом', ref: 'Розділ 33 (знак 3.21 «В\'їзд заборонено»)',
    tip: 'Дивіться на знак «В\'їзд заборонено» і напрямок одностороннього руху.',
  },
  PEDESTRIAN_ZONE: {
    severity: 'critical', title: 'В\'їзд у пішохідну зону', ref: 'Розділ 26; знак 5.33 «Пішохідна зона»',
    tip: 'Площа Ринок — пішохідна зона. Рух транспортних засобів заборонено.',
  },
  OPPOSITE_LANE: {
    severity: 'medium', title: 'Рух по смузі зустрічного руху', ref: 'Розділ 11',
    tip: 'Тримайтеся правого краю проїзної частини.',
  },
  NO_SIGNAL: {
    severity: 'medium', title: 'Не подано сигнал повороту', ref: 'Розділ 9, п. 9.2',
    tip: 'Вмикайте покажчик повороту завчасно до маневру (клавіші Z / X).',
  },
  WRONG_SIGNAL: {
    severity: 'medium', title: 'Подано сигнал не в той бік', ref: 'Розділ 9',
    tip: 'Сигнал має відповідати напрямку маневру.',
  },
  SIGNAL_NOT_CANCELLED: {
    severity: 'minor', title: 'Покажчик повороту не вимкнено після маневру', ref: 'Розділ 9, п. 9.2',
    tip: 'Сигнал повороту вводить інших в оману — вимикайте його одразу після маневру.',
  },
  NO_SEATBELT: {
    severity: 'medium', title: 'Рух без пристебнутого ременя безпеки', ref: 'Розділ 2, п. 2.3',
    tip: 'Пристебніться до початку руху (клавіша G).',
  },
  NO_LIGHTS: {
    severity: 'medium', title: 'Не ввімкнено ближнє світло', ref: 'Розділ 19, п. 19.1',
    tip: 'У темну пору доби та в умовах недостатньої видимості (дощ, туман, сніг) вмикайте ближнє світло (L).',
  },
  HIGH_BEAM_CITY: {
    severity: 'minor', title: 'Дальнє світло на освітленій вулиці', ref: 'Розділ 19',
    tip: 'Дальнє світло засліплює зустрічних водіїв і пішоходів. Перемкніть на ближнє (B).',
  },
  HORN_CITY: {
    severity: 'minor', title: 'Звуковий сигнал без потреби в населеному пункті', ref: 'Розділ 9',
    tip: 'У населених пунктах звуковий сигнал можна подавати лише для запобігання ДТП.',
  },
  SIDEWALK: {
    severity: 'major', title: 'Рух по тротуару', ref: 'Розділ 11; розділ 15',
    tip: 'Рух тротуарами заборонено.',
  },
  COLLISION_MINOR: {
    severity: 'major', title: 'Зіткнення з перешкодою', ref: 'Розділ 2',
    tip: 'Оцінюйте габарити автомобіля і тримайте дистанцію.',
  },
  COLLISION_CAR: {
    severity: 'critical', title: 'ДТП: зіткнення з транспортним засобом', ref: 'Розділ 13 (дистанція), розділ 2',
    tip: 'Тримайте безпечну дистанцію та інтервал.',
  },
  ILLEGAL_PARKING: {
    severity: 'medium', title: 'Стоянка в забороненому місці', ref: 'Розділ 15, п. 15.9',
    tip: 'Не ставте авто на переходах, ближче 10 м від них і на перехрестях.',
  },
  HANDBRAKE_DRIVE: {
    severity: 'minor', title: 'Рух із затягнутим стоянковим гальмом', ref: 'Технічна помилка',
    tip: 'Відпустіть стоянкове гальмо (Space) перед початком руху.',
  },
  STALL: {
    severity: 'minor', title: 'Заглух двигун', ref: 'Технічна помилка',
    tip: 'Плавно відпускайте зчеплення (Shift) і додавайте газ.',
  },
  ROUTE_MISSED: {
    severity: 'minor', title: 'Не виконано вказівку екзаменатора', ref: 'Порядок іспиту',
    tip: 'Слухайте вказівки та стежте за стрілкою маршруту.',
  },
  STOP_LINE: {
    severity: 'minor', title: 'Зупинка за стоп-лінією', ref: 'Розділ 8; розділ 34 (розмітка 1.12)',
    tip: 'Зупиняйтеся перед стоп-лінією, а не на ній.',
  },
};

// Ukrainian 12-point school scale, used as a friendly grade next to the score.
const SCALE = [[97, 12], [93, 11], [90, 10], [85, 9], [80, 8], [75, 7], [70, 6], [65, 5], [60, 4], [50, 3], [40, 2], [0, 1]];

export function to12(score) {
  for (const [min, g] of SCALE) if (score >= min) return g;
  return 1;
}

export const PASS_RULES = {
  practical: { minScore: 80, maxMajor: 2, label: 'Не менше 80 балів, без критичних помилок, не більше 2 грубих' },
  theory: { questions: 20, minutes: 20, maxMistakes: 2, label: '20 питань, 20 хвилин, не більше 2 помилок' },
  technical: { minScore: 80, label: 'Знайдено всі несправності, з якими експлуатацію заборонено' },
  drill: { minScore: 70, label: 'Не менше 70 балів у вправі' },
};

export function gradePractical(log) {
  let score = 100;
  let major = 0;
  let critical = 0;
  for (const v of log) {
    score -= SEVERITY[v.severity].points;
    if (v.severity === 'major') major++;
    if (v.severity === 'critical') critical++;
  }
  score = Math.max(0, score);
  const passed = critical === 0 && major <= PASS_RULES.practical.maxMajor && score >= PASS_RULES.practical.minScore;
  return { score, grade12: to12(score), passed, major, critical };
}
