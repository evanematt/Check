package com.lewandivka.core.text;

import com.lewandivka.core.campaign.CampaignStage;
import com.lewandivka.core.campaign.QuestStep;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Notebook texts of every quest step: the quest title (shared by the steps of one quest), the short objective and an
 * optional environmental clue. Ukrainian is canonical, English is the technical fallback. Counted steps put "%s/%s" at
 * the end of the objective (current / maximum).
 */
public final class QuestTexts {

    private QuestTexts() {
    }

    /** Texts of one step: {titleUk, titleEn, objectiveUk, objectiveEn, hintUk, hintEn}. */
    public record StepText(String titleUk, String titleEn, String objectiveUk, String objectiveEn, String hintUk, String hintEn) {
    }

    private static final Map<String, StepText> STEPS = new LinkedHashMap<>();
    private static final Map<String, String[]> STAGES = new LinkedHashMap<>();

    private static void step(QuestStep s, String titleUk, String titleEn, String objUk, String objEn, String hintUk, String hintEn) {
        STEPS.put(s.key(), new StepText(titleUk, titleEn, objUk, objEn, hintUk, hintEn));
    }

    private static void stage(CampaignStage s, String uk, String en) {
        STAGES.put(s.key(), new String[] {uk, en});
    }

    static {
        stage(CampaignStage.PROLOGUE, "Пролог", "Prologue");
        stage(CampaignStage.FIRST_NIGHT, "Перша ніч", "First Night");
        stage(CampaignStage.DISTRICT_REPUTATION, "Репутація", "Reputation");
        stage(CampaignStage.DEBTOR, "Боржник", "The Debtor");
        stage(CampaignStage.GARAGE_13, "Гараж №13", "Garage No. 13");
        stage(CampaignStage.LAST_TRAM, "Останній трамвай", "The Last Tram");
        stage(CampaignStage.CHROMA_READY, "Хрома", "Chroma");
        stage(CampaignStage.CHROMA_TRANSITION, "Перехід", "The Crossing");
        stage(CampaignStage.CHROMANDIVKA_BASE, "Хромандівка", "Chromandivka");
        stage(CampaignStage.RAINBOW_GARAGE, "Веселковий гараж", "Rainbow Garage");
        stage(CampaignStage.SHELTER, "Укриття втрачених імен", "Shelter of Lost Names");
        stage(CampaignStage.AQUAPARK, "Сухе озеро", "Dry Lake");
        stage(CampaignStage.SKY_DEPOT, "Депо над небом", "Depot Above the Sky");
        stage(CampaignStage.TOWER, "Вежа Голови району", "Head of District Tower");
        stage(CampaignStage.FINAL_BOSS, "Безбарвний Голова", "The Colorless Head");
        stage(CampaignStage.EPILOGUE, "Ранок", "Morning");
        stage(CampaignStage.POSTGAME, "Після всього", "After Everything");

        step(QuestStep.EXPLORE_DISTRICT, "Десь я не туди вийшов", "I Got Off Somewhere Wrong",
                "Огляньте район: двори, гаражі, магазин (%s/%s)", "Look around the district: courtyards, garages, the shop (%s/%s)",
                "Підійдіть до майданчика, лавки, смітників. Район сам покаже.", "Walk to the playground, the benches, the bins. The district will show you.");
        step(QuestStep.COLLECT_TOKENS, "Ніч на кінцевій", "Night at the Terminus",
                "Здобудьте жетони району (%s/%s)", "Collect district tokens (%s/%s)",
                "Гопники носять жетони в кишенях. Не всі зараз вороги.", "Gopniks carry tokens in their pockets. Not all of them are hostile yet.");
        step(QuestStep.CRAFT_KIOSK, "Закинутий кіоск", "The Abandoned Kiosk",
                "Скрафтьте «Закинутий кіоск»: 6 дощок, 2 залізні зливки, табличка", "Craft the Abandoned Kiosk: 6 planks, 2 iron ingots, 1 sign",
                "Дошки, залізо й табличку шукайте в сміттєвих баках і схованках двору.", "Look for planks, iron and a sign in the bins and caches of the yards.");
        step(QuestStep.PLACE_KIOSK, "Закинутий кіоск", "The Abandoned Kiosk",
                "Поставте кіоск на фундамент біля магазину", "Place the kiosk on the foundation near the shop",
                "Фундамент розмальований, його видно від тролейбусного кільця.", "The foundation is painted; it can be seen from the turning loop.");
        step(QuestStep.TALK_SHLAHBAUM, "Закинутий кіоск", "The Abandoned Kiosk",
                "Поговоріть із Паном Шлагбаумом", "Talk to Mr. Shlahbaum",
                "Він стоїть біля кіоска. Мовчки. Це нормально.", "He stands by the kiosk. Silently. That is normal.");
        step(QuestStep.DEBTOR_NOTE, "Боржник", "The Debtor",
                "Прочитайте записку «Боржник»", "Read the note \"The Debtor\"",
                "Записка в інвентарі. Клацніть нею.", "The note is in your inventory. Use it.");
        step(QuestStep.DEBTOR_CLUES, "Боржник", "The Debtor",
                "Знайдіть сліди Боржника (%s/%s)", "Find the Debtor's traces (%s/%s)",
                "Пакет від чаю, лавка, майданчик, смітник, дах гаражів.", "A tea package, a bench, the playground, the bins, a garage roof.");
        step(QuestStep.DEBTOR_CHASE, "Боржник", "The Debtor",
                "Наздожени Боржника", "Catch the Debtor",
                "Він швидкий, але знає лише свої двори. Відріжте йому шлях.", "He is fast, but only knows his own yards. Cut off his routes.");
        step(QuestStep.DEBTOR_RESOLVE, "Боржник", "The Debtor",
                "Вирішіть справу з Боржником", "Settle things with the Debtor",
                "Заплатити, поручитися або принести сємок. Бити не обов'язково.", "Pay, vouch for him or bring seeds. No fighting required.");
        step(QuestStep.KETTLE_TEST, "Боржник", "The Debtor",
                "Випробуйте чайник із водою", "Try the kettle with water in it",
                "Наповніть чайник водою і поставте на землю.", "Fill the kettle with water and set it on the ground.");
        step(QuestStep.GARAGE_FIND, "Гараж №13", "Garage No. 13",
                "Знайдіть гаражний кооператив", "Find the garage cooperative",
                "На заході району, де закінчується асфальт.", "In the west of the district, where the asphalt ends.");
        step(QuestStep.GARAGE_PANELS, "Гараж №13", "Garage No. 13",
                "Увімкніть три електричні точки (%s/%s)", "Restore the three power points (%s/%s)",
                "Автомати, перемикачі, важелі. Номери гаражів брешуть.", "Breakers, switches, levers. The garage numbers lie.");
        step(QuestStep.GARAGE_PACKAGE, "Гараж №13", "Garage No. 13",
                "Заберіть посилку з гаража №13", "Take the package from Garage No. 13",
                "Між дванадцятим і чотирнадцятим тепер є двері.", "Between twelve and fourteen there is now a door.");
        step(QuestStep.GARAGE_ESCAPE, "Гараж №13", "Garage No. 13",
                "Винесіть посилку: головні ворота зачинилися", "Carry the package out: the main gate has closed",
                "Підземні тунелі ведуть на люк за гаражами. Посилка бурчить.", "The tunnels lead to a manhole behind the garages. The package growls.");
        step(QuestStep.GARAGE_DELIVER, "Гараж №13", "Garage No. 13",
                "Віднесіть посилку Панові Шлагбауму", "Bring the package to Mr. Shlahbaum",
                "Не відкривайте. Не трусіть.", "Do not open it. Do not shake it.");
        step(QuestStep.TRAM_WAIT, "Останній трамвай", "The Last Tram",
                "Зачекайте на трамвайній зупинці", "Wait at the tram stop",
                "Коли стемніє, почується дзвінок.", "When it gets dark you will hear a bell.");
        step(QuestStep.TRAM_FIGHT, "Останній трамвай", "The Last Tram",
                "Відбийте безквиткових (хвиля %s/%s)", "Fend off the fare dodgers (wave %s/%s)",
                "Три хвилі. Старі компостери навколо трамвая щось та й роблять.", "Three waves. The old validators around the tram do something.");
        step(QuestStep.TRAM_REPORT, "Останній трамвай", "The Last Tram",
                "Поверніться до Пана Шлагбаума", "Return to Mr. Shlahbaum",
                "Компостер у вас. Він знатиме, що з ним робити.", "You have the composter. He will know what to do with it.");
        step(QuestStep.CHROMA_TAKE, "Таблетка «Хрома»", "The Chroma Tablet",
                "Проковтніть «Хрому» разом: п'ять хвилин на всіх", "Swallow Chroma together: five minutes for everyone",
                "Таймер піде, коли перший проковтне. Не розходьтесь.", "The timer starts when the first of you swallows. Stay together.");
        step(QuestStep.TRANSITION, "По той бік району", "The Other Side of the District",
                "Тримайтеся", "Hold on",
                "", "");
        step(QuestStep.BASE_WAKE, "Прокинутись у чужому", "Waking Up Somewhere Else",
                "Огляньте будинок і прокиньтесь", "Look around the house and wake up",
                "Три ліжка, коробки, порожні місця для котів.", "Three beds, boxes, empty places for cats.");
        step(QuestStep.BASE_PEDESTALS, "Прокинутись у чужому", "Waking Up Somewhere Else",
                "Покладіть на постаменти чайник, посилку, компостер і жетон (%s/%s)", "Put the kettle, package, composter and token on the pedestals (%s/%s)",
                "Чотири постаменти вздовж стіни. Предмети нікуди не втечуть.", "Four pedestals along the wall. The items will not run away.");
        step(QuestStep.RG_TRAVEL, "Кооператив «Веселковий гараж»", "Rainbow Garage Cooperative",
                "Дійдіть до «Веселкового гаража» за компасом", "Reach the Rainbow Garage with the compass",
                "Компас показує потрібне, а не північ. Дивіться на будівлі вдалині.", "The compass shows what you need, not north. Look at the buildings in the distance.");
        step(QuestStep.RG_WINGS, "Кооператив «Веселковий гараж»", "Rainbow Garage Cooperative",
                "Пройдіть три крила гаража й візьміть батареї (%s/%s)", "Clear the three wings and take the batteries (%s/%s)",
                "Фарбувальня, підйомна зала, промисловий прес.", "Paint workshop, lift hall, industrial press.");
        step(QuestStep.RG_BOSS, "Кооператив «Веселковий гараж»", "Rainbow Garage Cooperative",
                "Переможіть Гаражного Короля", "Defeat the Garage King",
                "Три батареї відкривають арену.", "Three batteries open the arena.");
        step(QuestStep.SH_DASH, "Двоє, які знають дорогу", "Two Who Know the Way",
                "Потрапте до Укриття втрачених імен: потрібен ривок", "Reach the Shelter of Lost Names: you need a dash",
                "Клавіша ривка — V. Двері швидше за вас не бувають.", "Dash with V. The doors are never faster than you.");
        step(QuestStep.SH_LEVERS, "Двоє, які знають дорогу", "Two Who Know the Way",
                "Потягніть три важелі разом (%s/%s)", "Pull the three levers together (%s/%s)",
                "Вікно синхронізації залежить від кількості гравців.", "The sync window depends on how many players there are.");
        step(QuestStep.SH_CHINAZIK, "Двоє, які знають дорогу", "Two Who Know the Way",
                "Виведіть чорного кота на килимок: кладіть їжу (%s/%s)", "Lead the black cat onto the rug: place food (%s/%s)",
                "Не підходьте прямо. Він це не любить.", "Do not walk straight at him. He does not like it.");
        step(QuestStep.SH_METADONNA, "Двоє, які знають дорогу", "Two Who Know the Way",
                "Знайдіть сіру кішку в коробках складу", "Find the grey cat among the warehouse boxes",
                "Слухайте муркотіння. Потрібна коробка трохи менша за інші.", "Listen for the purring. The right box is a little smaller than the rest.");
        step(QuestStep.SH_BOSS, "Двоє, які знають дорогу", "Two Who Know the Way",
                "Переможіть Колекціонера Нашийників", "Defeat the Collar Collector",
                "Коти знають, який нашийник справжній.", "The cats know which collar is real.");
        step(QuestStep.AQ_FIND, "Сухе озеро", "Dry Lake",
                "Знайдіть вхід в аквапарк: коти покажуть", "Find the aquapark entrance: the cats will show you",
                "Кіт сидить і дивиться на стіну — отже, це не стіна.", "A cat sitting and staring at a wall means it is not a wall.");
        step(QuestStep.AQ_PUMPS, "Сухе озеро", "Dry Lake",
                "Запустіть три насоси (%s/%s)", "Start the three pumps (%s/%s)",
                "Після кожного насоса частина басейнів наповнюється помаранчевим.", "After each pump, part of the pools fills with orange liquid.");
        step(QuestStep.AQ_BOSS, "Сухе озеро", "Dry Lake",
                "Переможіть Пані Вирву", "Defeat Lady Vortex",
                "Кожна чверть арени має колір і знак. Зважайте на обидва.", "Each quarter of the arena has a colour and a symbol. Heed both.");
        step(QuestStep.SKY_ASCENT, "Трамвайне депо над небом", "The Depot Above the Sky",
                "Піднімайтеся пружинами до небесної зупинки", "Bounce up to the sky tram stop",
                "Пружинна плита, острів, кришталевий виступ, тунель.", "Spring pad, island, crystal ledge, bounce tunnel.");
        step(QuestStep.SKY_RIDE, "Трамвайне депо над небом", "The Depot Above the Sky",
                "Сядьте в небесний трамвай", "Board the sky tram",
                "Дві-три хвилини панорами. Тримайтеся.", "Two or three minutes of panorama. Hold on.");
        step(QuestStep.SKY_SWITCHES, "Трамвайне депо над небом", "The Depot Above the Sky",
                "Налаштуйте стрілки, щоб вагон дійшов до каси", "Set the switches so the car reaches the ticket office",
                "У диспетчерській висять схеми. Помилка не смертельна.", "The dispatcher room has diagrams. A mistake is not fatal.");
        step(QuestStep.SKY_TICKETS, "Трамвайне депо над небом", "The Depot Above the Sky",
                "Отримайте квитки з різними знаками (%s/%s)", "Obtain tickets with different symbols (%s/%s)",
                "Хрестик, кружечок, трикутник, квадрат. Компостер знову знадобиться.", "Cross, circle, triangle, square. The composter will be needed again.");
        step(QuestStep.SKY_BOSS, "Трамвайне депо над небом", "The Depot Above the Sky",
                "Переможіть Кондуктора", "Defeat the Conductor",
                "Дивіться під ноги: дзвінок, лампи, звук, рух колії.", "Watch your feet: bell, lamps, sound, moving tracks.");
        step(QuestStep.TOWER_RING, "Голова району", "Head of the District",
                "Подивіться вгору: кільце ціле", "Look up: the ring is whole",
                "Недовго.", "Not for long.");
        step(QuestStep.TOWER_APPROACH, "Голова району", "Head of the District",
                "Пройдіть підхід до Вежі: ривок, прохід, пружини, планер", "Cross the approach to the Tower: dash, passage, springs, glider",
                "Потрібні всі три здібності та уважність котів.", "All three abilities and the cats' attention are required.");
        step(QuestStep.TOWER_CLIMB, "Голова району", "Head of the District",
                "Підніміться Вежею (поверх %s/%s)", "Climb the Tower (floor %s/%s)",
                "Архів, Кабінет 404, Відділ колірних порушень, Службова шахта, Обвалений поверх.", "Archive, Office 404, Department of Color Violations, Service Shaft, Collapsed Floor.");
        step(QuestStep.FINAL_FIGHT, "Безбарвний Голова", "The Colorless Head",
                "Переможіть Безбарвного Голову", "Defeat the Colorless Head",
                "Тільки той, хто тримає заряд, завдає шкоди. Передавайте його клавішею Q.", "Only the holder of the charge deals damage. Pass it with Q.");
        step(QuestStep.EPI_RETURN, "Район знову кольоровий", "The District Is Colorful Again",
                "Огляньте оновлену Хромандівку", "Look around the restored Chromandivka",
                "Дещо змінилося.", "Some things have changed.");
        step(QuestStep.EPI_PORTAL, "Район знову кольоровий", "The District Is Colorful Again",
                "Поверніться порталом до Левандівки", "Return through the portal to Lewandivka",
                "Портал у будинку, там, де були постаменти.", "The portal is in the house, where the pedestals were.");
        step(QuestStep.EPI_MORNING, "Район знову кольоровий", "The District Is Colorful Again",
                "Зустріньте ранок біля кіоска", "Meet the morning at the kiosk",
                "Пан Шлагбаум на місці.", "Mr. Shlahbaum is in his place.");
        step(QuestStep.POST_FREE, "Район відкритий", "The District Is Open",
                "Вільна гра: дванадцять котів, сємки, трамвай, гараж №0", "Free play: twelve cats, the seeds, the tram, Garage No. 0",
                "Блокнот підкаже, що ще лишилося.", "The notebook will tell you what is left.");
    }

    public static StepText of(QuestStep step) {
        return STEPS.get(step.key());
    }

    public static Map<String, StepText> steps() {
        return STEPS;
    }

    public static Map<String, String[]> stages() {
        return STAGES;
    }
}
