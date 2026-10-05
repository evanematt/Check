# Порядок проходження

```
PROLOGUE → FIRST_NIGHT → DISTRICT_REPUTATION → DEBTOR → GARAGE_13 → LAST_TRAM
   → CHROMA_READY → CHROMA_TRANSITION → CHROMANDIVKA_BASE
   → RAINBOW_GARAGE → SHELTER → AQUAPARK → SKY_DEPOT → TOWER → FINAL_BOSS
   → EPILOGUE → POSTGAME
```

| Етап | Віха | Умова переходу |
|---|---|---|
| PROLOGUE | — | старт |
| FIRST_NIGHT | `joined_any` | перший вхід |
| DISTRICT_REPUTATION | `lvl1` | Шлагбаум рівня 1 |
| DEBTOR | `lvl2` | жетони обміняно |
| GARAGE_13 | `lvl3` | чайник зданий |
| LAST_TRAM | `lvl4` | посилка здана |
| CHROMA_READY | `lvl5` | компостер зданий |
| CHROMA_TRANSITION | `chroma_started` | хтось з'їв Хрому |
| CHROMANDIVKA_BASE | `arrived` | всі з'їли за 5 хв |
| RAINBOW_GARAGE | `base_left` | група вийшла з бази |
| SHELTER | `done_garage` | Гаражний Король переможений (ривок) |
| AQUAPARK | `done_shelter` | Колекціонер переможений, коти вдома |
| SKY_DEPOT | `done_aqua` | Пані Вирва переможена (устілки) |
| TOWER | `done_depot` | Кондуктор переможений (планер) |
| FINAL_BOSS | `final_started` | зайшли в бій із Головою |
| EPILOGUE | `done_tower` | Голова переможений |
| POSTGAME | `postgame` | епілог пройдено |

Стан фази 1: усе до CHROMANDIVKA включно і босів 1–5 реалізовано в попередніх версіях; суворі етапи (`tower_entered`,
`final_started`, `postgame`) і повний епілог додаються в пізніших фазах (див. `TEST_PLAN.md`).
