package com.lewandivka.core.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog of the custom sound events. All sounds are original, synthesised by tools/make_sounds.py (no third-party
 * audio is shipped). Every sound has a subtitle so the information is never audio-only.
 */
public final class ModSounds {

    private ModSounds() {
    }

    public static final List<SoundSpec> ALL;
    private static final Map<String, SoundSpec> BY_ID = new LinkedHashMap<>();

    private static void add(List<SoundSpec> l, String id, String uk, String en, String category, int variants, boolean stream, float volume) {
        l.add(new SoundSpec(id, uk, en, category, variants, stream, volume));
    }

    static {
        List<SoundSpec> l = new ArrayList<>();
        // ambience and music (streamed)
        add(l, "ambient.district.loop", "Гуде район", "The district hums", "ambient", 1, true, 0.6f);
        add(l, "ambient.district.mood", "Десь далеко дзвонить трамвай", "A tram bell far away", "ambient", 2, false, 0.8f);
        add(l, "ambient.chroma.loop", "Повітря співає", "The air sings", "ambient", 1, true, 0.6f);
        add(l, "ambient.chroma.additions", "Дзвенять скляні дзвіночки", "Glass chimes", "ambient", 3, false, 0.7f);
        add(l, "ambient.garage.loop", "Гуде механізм", "Machinery hums", "ambient", 1, true, 0.5f);
        add(l, "ambient.tower.loop", "Гудуть лампи в офісі", "Office lights buzz", "ambient", 1, true, 0.5f);
        add(l, "ambient.aquapark", "Крапає вода", "Water drips", "ambient", 1, true, 0.5f);
        add(l, "music.district", "Тиха мелодія", "A quiet melody", "music", 1, true, 0.7f);
        add(l, "music.chroma", "Мрійлива мелодія", "A dreamy melody", "music", 1, true, 0.7f);
        add(l, "music.boss", "Напружена музика", "Tense music", "music", 1, true, 0.7f);
        add(l, "music.tower", "Канцелярська мелодія", "A bureaucratic melody", "music", 1, true, 0.6f);
        add(l, "music.credits", "Музика титрів", "Credits music", "music", 1, true, 0.8f);
        // tram
        add(l, "tram.bell", "Дзвонить трамвай", "A tram bell rings", "block", 2, false, 1.0f);
        add(l, "tram.arrive", "Наближається трамвай", "A tram approaches", "block", 1, false, 1.0f);
        add(l, "tram.horn", "Гудок трамвая", "A tram horn", "block", 1, false, 1.0f);
        add(l, "tram.rumble", "Гуркоче трамвай", "A tram rumbles", "block", 1, true, 0.8f);
        add(l, "tram.validate", "Квиток прозвучав", "A ticket chimes", "block", 2, false, 1.0f);
        add(l, "tram.compost", "Компостер клацає", "A composter clacks", "block", 2, false, 1.0f);
        // district life
        add(l, "kiosk.place", "Кіоск стає на місце", "The kiosk settles", "block", 1, false, 1.0f);
        add(l, "kiosk.open", "Кіоск відчиняється", "The kiosk opens", "block", 1, false, 1.0f);
        add(l, "npc.mutter", "Хтось бурмоче", "Someone mutters", "neutral", 3, false, 0.9f);
        add(l, "npc.blip", "Говорить", "Talking", "neutral", 3, false, 0.6f);
        add(l, "gopnik.ambient", "Гопник бурчить", "A gopnik grumbles", "hostile", 3, false, 0.9f);
        add(l, "gopnik.hurt", "Гопник скрикує", "A gopnik yelps", "hostile", 2, false, 1.0f);
        add(l, "gopnik.death", "Гопник падає", "A gopnik falls", "hostile", 1, false, 1.0f);
        add(l, "gopnik.throw", "Летить сємка", "A seed flies", "hostile", 2, false, 0.8f);
        add(l, "seed.crunch", "Хрускіт сємок", "Seeds crunch", "player", 3, false, 0.8f);
        add(l, "stash.open", "Шарудить у смітті", "Rummaging", "block", 2, false, 0.9f);
        add(l, "note.read", "Шурхіт паперу", "Paper rustles", "player", 2, false, 0.8f);
        add(l, "box.rustle", "Шарудить коробка", "A box rustles", "block", 3, false, 0.8f);
        add(l, "box.open", "Коробка відкрита", "A box opens", "block", 1, false, 0.9f);
        // package and kettle
        add(l, "package.growl", "Посилка бурчить", "The package growls", "neutral", 3, false, 1.0f);
        add(l, "package.place", "Посилку поставлено", "The package is set down", "block", 1, false, 0.9f);
        add(l, "package.pickup", "Посилку піднято", "The package is picked up", "player", 1, false, 0.9f);
        add(l, "kettle.pour", "Вода ллється", "Water pours", "player", 1, false, 0.9f);
        add(l, "kettle.pink", "Щось дивне з водою", "Something odd happens to the water", "block", 1, false, 1.0f);
        add(l, "kettle.place", "Чайник ставлять", "A kettle is set down", "block", 1, false, 0.9f);
        // chroma and portal
        add(l, "chroma.consume", "Ковток Хроми", "A gulp of Chroma", "player", 1, false, 1.0f);
        add(l, "chroma.tick", "Хрома відлічує", "Chroma counts down", "master", 1, false, 0.6f);
        add(l, "chroma.fail", "Хрома згасає", "Chroma fades", "master", 1, false, 1.0f);
        add(l, "chroma.transition", "Реальність вигинається", "Reality bends", "master", 1, false, 1.0f);
        add(l, "chroma.arrive", "Колір повертається", "Colour returns", "master", 1, false, 1.0f);
        add(l, "crystal.chime", "Дзвенить кристал", "A crystal chimes", "block", 4, false, 0.9f);
        add(l, "crystal.hum", "Кристал гуде", "A crystal hums", "block", 1, true, 0.5f);
        add(l, "portal.ambient", "Гуде портал", "A portal hums", "block", 1, true, 0.6f);
        add(l, "portal.activate", "Портал прокидається", "The portal awakens", "block", 1, false, 1.0f);
        add(l, "portal.enter", "Портал проковтує", "The portal swallows", "player", 1, false, 1.0f);
        // garage
        add(l, "garage.power_on", "Вмикається живлення", "Power comes on", "block", 1, false, 1.0f);
        add(l, "garage.power_off", "Живлення зникає", "Power cuts out", "block", 1, false, 1.0f);
        add(l, "garage.clack", "Клацає перемикач", "A switch clacks", "block", 3, false, 1.0f);
        add(l, "garage.door", "Важкі двері", "A heavy door moves", "block", 2, false, 1.0f);
        add(l, "garage.press", "Гупає прес", "A press thumps", "block", 2, false, 1.0f);
        add(l, "garage.lift", "Гуде підйомник", "A lift whirs", "block", 1, false, 0.9f);
        add(l, "lever.pull", "Клацає важіль", "A lever clunks", "block", 2, false, 1.0f);
        add(l, "switch.click", "Клацає стрілка", "A switch clicks", "block", 2, false, 0.9f);
        add(l, "tap.pour", "Тече фарба", "Paint flows", "block", 2, false, 0.8f);
        // aquapark
        add(l, "pump.start", "Запускається насос", "A pump starts", "block", 1, false, 1.0f);
        add(l, "pump.fill", "Басейн наповнюється", "A pool fills", "block", 1, false, 1.0f);
        add(l, "drain.open", "Відкривається злив", "A drain opens", "block", 1, false, 1.0f);
        add(l, "water.whoosh", "Шумить вода", "Water rushes", "hostile", 2, false, 1.0f);
        // bosses
        add(l, "boss.telegraph", "Бос готується", "The boss prepares an attack", "hostile", 2, false, 1.0f);
        add(l, "boss.slam", "Бос гупає", "The boss slams", "hostile", 2, false, 1.0f);
        add(l, "boss.phase", "Бос змінює фазу", "The boss changes phase", "hostile", 1, false, 1.0f);
        add(l, "boss.roar", "Бос ревіще", "The boss roars", "hostile", 2, false, 1.0f);
        add(l, "boss.shield_on", "Щит увімкнено", "The shield rises", "hostile", 1, false, 1.0f);
        add(l, "boss.shield_off", "Щит зник", "The shield drops", "hostile", 1, false, 1.0f);
        add(l, "boss.defeat", "Бос переможений", "The boss is defeated", "hostile", 1, false, 1.0f);
        add(l, "boss.spawn", "З'являється бос", "A boss appears", "hostile", 1, false, 1.0f);
        add(l, "boss.bell", "Дзвінок попередження", "A warning bell", "hostile", 1, false, 1.0f);
        // tower
        add(l, "tower.ticket", "Автомат видає талон", "The machine prints a ticket", "block", 1, false, 1.0f);
        add(l, "tower.bell", "Дзвонить табло черги", "The queue display chimes", "block", 1, false, 1.0f);
        add(l, "tower.door", "Скриплять двері", "A door creaks", "block", 1, false, 1.0f);
        // abilities and ui
        add(l, "ability.dash", "Ривок", "Dash", "player", 2, false, 1.0f);
        add(l, "ability.spring", "Підскок", "Spring", "player", 2, false, 1.0f);
        add(l, "ability.glide_start", "Розкривається планер", "The glider opens", "player", 1, false, 1.0f);
        add(l, "ability.glide_end", "Планер складається", "The glider closes", "player", 1, false, 0.8f);
        add(l, "ability.unlock", "Нова здібність", "A new ability", "player", 1, false, 1.0f);
        add(l, "ability.ready", "Здібність готова", "An ability is ready", "player", 1, false, 0.5f);
        add(l, "ui.notebook_open", "Відкривається блокнот", "The notebook opens", "player", 1, false, 1.0f);
        add(l, "ui.quest_update", "Нотатку оновлено", "The notebook is updated", "player", 1, false, 1.0f);
        add(l, "ui.checkpoint", "Контрольна точка", "Checkpoint", "player", 1, false, 1.0f);
        add(l, "ui.warning", "Попередження", "Warning", "master", 1, false, 0.8f);
        // cats
        add(l, "cat.purr", "Кіт муркоче", "A cat purrs", "neutral", 1, true, 0.7f);
        add(l, "cat.meow_low", "Кіт нявкає", "A cat meows", "neutral", 3, false, 1.0f);
        add(l, "cat.meow_high", "Кіт мявкає", "A cat mews", "neutral", 3, false, 1.0f);
        add(l, "cat.hiss", "Кіт шипить", "A cat hisses", "neutral", 1, false, 1.0f);
        add(l, "cat.scratch", "Кіт дряпає", "A cat scratches", "neutral", 2, false, 0.9f);
        add(l, "cat.eat", "Кіт їсть", "A cat eats", "neutral", 2, false, 0.8f);
        add(l, "cat.sniff", "Кіт нюхає", "A cat sniffs", "neutral", 2, false, 0.8f);
        // tower mechanics
        add(l, "relay.active", "Реле вмикається", "A relay activates", "block", 1, false, 1.0f);
        add(l, "relay.pass", "Заряд перелітає", "The charge flies over", "player", 1, false, 1.0f);
        add(l, "charge.overload", "Заряд перегрівається", "The charge overheats", "player", 1, false, 1.0f);
        add(l, "platform.crumble", "Платформа тріскається", "A platform cracks", "block", 2, false, 1.0f);
        add(l, "platform.collapse", "Платформа обвалюється", "A platform collapses", "block", 1, false, 1.0f);
        add(l, "spring.boing", "Пружина", "A spring boings", "block", 2, false, 1.0f);
        add(l, "checkpoint.set", "Лампа спалахує", "A lamp lights", "block", 1, false, 1.0f);
        add(l, "lift.move", "Рухається платформа", "A platform moves", "block", 1, false, 0.9f);
        add(l, "validator.ok", "Квиток дійсний", "The ticket is valid", "block", 1, false, 1.0f);
        add(l, "validator.fail", "Квиток недійсний", "The ticket is invalid", "block", 1, false, 1.0f);
        ALL = List.copyOf(l);
        for (SoundSpec s : ALL) {
            if (BY_ID.put(s.id(), s) != null) {
                throw new IllegalStateException("duplicate sound id " + s.id());
            }
        }
    }

    public static SoundSpec byId(String id) {
        return BY_ID.get(id);
    }
}
