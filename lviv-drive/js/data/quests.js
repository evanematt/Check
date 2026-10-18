// Training quests ("Навчання"). A step completes when `check(game)` returns
// true, or when the rules engine has emitted `event` `count` times since the
// step started. `waypoint` puts a marker in the world and on the minimap; it
// names a feature type and the preferred spot on the schematic map, so quests
// also work on an imported OpenStreetMap network (nearest matching feature).

export const QUESTS = [
  {
    id: 'predrive',
    title: 'Підготовка до руху',
    intro: 'Перед виїздом водій готує себе й автомобіль. Виконайте все по черзі.',
    steps: [
      { text: 'Пристебніть ремінь безпеки', key: 'G', check: (g) => g.car.belt },
      { text: 'Заведіть двигун', key: 'I', check: (g) => g.car.engineOn },
      { text: 'Увімкніть ближнє світло', key: 'L', check: (g) => g.car.lights >= 2 },
      { text: 'Подивіться в ліве дзеркало (утримуйте ← на миші або клавішу 1)', key: '1', check: (g) => g.flags.lookedLeft },
      { text: 'Подивіться в праве дзеркало (клавіша 3)', key: '3', check: (g) => g.flags.lookedRight },
      { text: 'Увімкніть лівий покажчик повороту перед початком руху', key: 'Z', check: (g) => g.car.indicator === 'left' },
      { text: 'Відпустіть стоянкове гальмо', key: 'Space', check: (g) => !g.car.handbrake },
      { text: 'Рушайте і проїдьте 50 м', check: (g) => g.stats.distance > 50 },
    ],
  },
  {
    id: 'lights',
    title: 'Світлофор на проспекті Свободи',
    intro: 'Сигнали: зелений → миготливий зелений → жовтий → червоний → червоний із жовтим.',
    steps: [
      { text: 'Доїдьте до регульованого перехрестя (Свободи / Дорошенка)', waypoint: { feature: 'light', prefer: 'n11' }, check: (g) => g.atWaypoint(30) },
      { text: 'Зупиніться перед стоп-лінією на червоний сигнал (будь-який світлофор)', event: 'stopped_red', count: 1 },
      { text: 'Рушайте лише на зелений', event: 'green_go', count: 1 },
    ],
  },
  {
    id: 'pedestrians',
    title: 'Пішохідні переходи',
    intro: 'Водій зобов\'язаний пропустити пішоходів на нерегульованому переході (п. 18.1).',
    steps: [
      { text: 'Їдьте до нерегульованого переходу (проспект Свободи)', waypoint: { feature: 'crosswalk', prefer: 'n11-n12' }, check: (g) => g.atWaypoint(40) },
      { text: 'Зупиніться й пропустіть пішоходів (2 рази)', event: 'yield_ped', count: 2 },
    ],
  },
  {
    id: 'zone',
    title: 'Житлова зона Старого міста',
    intro: 'У житловій зоні (знак 5.31) — не більше 20 км/год, пішоходи мають перевагу. Площа Ринок — пішохідна зона.',
    steps: [
      { text: 'В\'їдьте в житлову зону (вул. Вірменська)', waypoint: { feature: 'residential', prefer: 'n11-n21' }, check: (g) => g.loc?.edge?.zone === 'residential' },
      { text: 'Проїдьте 150 м житловою зоною не швидше 20 км/год', event: 'zone_ok', count: 1 },
      { text: 'Проїдьте рівнозначне перехрестя за правилом «правої руки»', waypoint: { feature: 'equal', prefer: 'n21' }, event: 'equal_ok', count: 1 },
    ],
  },
  {
    id: 'stop',
    title: 'Знак «STOP»',
    intro: 'Перед знаком 2.2 зупинка обов\'язкова завжди, навіть якщо дорога вільна.',
    steps: [
      { text: 'Під\'їдьте до знака STOP (вул. Дорошенка → Городоцька)', waypoint: { feature: 'stop', prefer: 'n01' }, check: (g) => g.atWaypoint(45) },
      { text: 'Повністю зупиніться перед стоп-лінією і дайте дорогу', event: 'stop_ok', count: 1 },
    ],
  },
  {
    id: 'signals',
    title: 'Сигнали повороту',
    intro: 'Покажчик вмикається завчасно — до початку маневру — і вимикається після нього.',
    steps: [
      { text: 'Виконайте 3 повороти з правильно поданим сигналом', event: 'signal_ok', count: 3 },
    ],
  },
  {
    id: 'parking',
    title: 'Паркування на вул. Дорошенка',
    intro: 'Паркуйтеся в смузі для стоянки, не ближче 10 м від переходу. Двигун — вимкнути, стоянкове гальмо — затягнути.',
    steps: [
      { text: 'Доїдьте до смуги для стоянки (вул. Дорошенка)', waypoint: { feature: 'parking', prefer: 'n01-n11' }, check: (g) => g.atWaypoint(40) },
      { text: 'Припаркуйтеся в смузі стоянки праворуч', event: 'parked_ok', count: 1 },
    ],
  },
  {
    id: 'night',
    title: 'Нічний Львів (4D: час доби)',
    intro: 'Час перемикається на 22:00. Ближнє світло обов\'язкове, дальнє на освітлених вулицях — заборонено.',
    onStart: (g) => g.setTime(22),
    steps: [
      { text: 'Увімкніть ближнє світло', check: (g) => g.car.lights >= 2 },
      { text: 'Проїдьте 500 м без помилок зі світлом', check: (g) => g.stats.nightClean > 500 },
    ],
  },
];
