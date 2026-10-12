package com.lewandivka.core.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The advancement tab. Ids are stable; the root is {@code lewandivka:terminus}. */
public final class ModAdvancements {

    private ModAdvancements() {
    }

    public static final List<AdvancementSpec> ALL;
    private static final Map<String, AdvancementSpec> BY_ID = new LinkedHashMap<>();

    private static void add(List<AdvancementSpec> l, String id, String parent, String icon, String frame, boolean hidden,
                            String tUk, String tEn, String dUk, String dEn) {
        l.add(new AdvancementSpec(id, parent, icon, frame, hidden, tUk, tEn, dUk, dEn));
    }

    static {
        List<AdvancementSpec> l = new ArrayList<>();
        add(l, "terminus", null, "lewandivka:district_notebook", "task", false,
                "Кінцева", "Terminus", "Увійдіть до Левандівки.", "Enter Lewandivka.");
        add(l, "seeds", "terminus", "lewandivka:semky", "task", false,
                "Сємки є?", "Got Any Seeds?", "Вирішіть справу сємками, а не кулаками.", "Settle it with seeds instead of fists.");
        add(l, "kiosk", "terminus", "lewandivka:district_token", "task", false,
                "Кіоск працює", "The Kiosk Is Open", "Поставте закинутий кіоск на місце.", "Put the abandoned kiosk in its place.");
        add(l, "debtor", "kiosk", "lewandivka:debtor_note", "task", false,
                "Рахунок закрито", "The Bill Is Settled", "Наздожени Боржника й вирішіть справу.", "Catch the Debtor and settle things.");
        add(l, "local", "kiosk", "lewandivka:district_token", "goal", false,
                "Свій", "One of Us", "Підвищте репутацію до «Свій».", "Raise your reputation to \"Свій\".");
        add(l, "garage13", "debtor", "lewandivka:garage13_note", "goal", false,
                "Гараж №13", "Garage No. 13", "Знайдіть гараж, якого не існує.", "Find the garage that does not exist.");
        add(l, "last_tram", "garage13", "lewandivka:ticket_composter", "goal", false,
                "Останній трамвай", "The Last Tram", "Завершіть перший акт.", "Complete Act One.");
        add(l, "other_side", "last_tram", "lewandivka:chroma_tablet", "goal", false,
                "По той бік району", "The Other Side of the District", "Потрапте до Хромандівки.", "Reach Chromandivka.");
        add(l, "portal", "other_side", "lewandivka:portal_frame", "task", false,
                "Кольоровий портал", "The Colored Portal", "Запустіть портал назад до Левандівки.", "Start the portal back to Lewandivka.");
        add(l, "dash", "other_side", "minecraft:feather", "task", false,
                "Ривок", "Dash", "Здолайте Гаражного Короля.", "Defeat the Garage King.");
        add(l, "two_who_know", "dash", "lewandivka:cat_fish", "goal", false,
                "Двоє, які знають дорогу", "Two Who Know the Way", "Поверніть обох котів.", "Bring both cats back.");
        add(l, "spring", "two_who_know", "minecraft:slime_ball", "task", false,
                "Підскок", "Spring Insoles", "Здолайте Пані Вирву.", "Defeat Lady Vortex.");
        add(l, "ticket_ok", "spring", "lewandivka:ticket_circle", "goal", false,
                "Квиток є", "Ticket Is Valid", "Здолайте Кондуктора.", "Defeat the Conductor.");
        add(l, "glider", "ticket_ok", "minecraft:elytra", "task", false,
                "Планер", "Glider Ticket", "Отримайте здібність планера.", "Obtain the glider ability.");
        add(l, "ring", "ticket_ok", "lewandivka:ring_fragment_conductor", "goal", false,
                "Кільце ціле", "The Ring Is Whole", "Поверніть усі чотири уламки кільця.", "Restore all four ring fragments.");
        add(l, "district_colorful", "ring", "lewandivka:chromatic_compass", "challenge", false,
                "Район знову кольоровий", "The District Is Colorful Again", "Завершіть кампанію.", "Complete the campaign.");
        add(l, "cats12", "district_colorful", "lewandivka:collar_chinazik", "goal", true,
                "12 котів Левандівки", "Twelve Cats of Lewandivka", "Знайдіть усіх дванадцять котів.", "Find all twelve cats.");
        add(l, "garage0", "district_colorful", "lewandivka:guard_door", "goal", true,
                "Гараж №0", "Garage No. 0", "Пройдіть гараж, якого не було навіть у записках.", "Clear the garage that was not even in the notes.");
        add(l, "wrong_tram", "district_colorful", "lewandivka:tram_switch", "goal", true,
                "Не той трамвай", "The Wrong Tram", "Сядьте в не той трамвай.", "Board the wrong tram.");
        add(l, "better_not_ask", "district_colorful", "lewandivka:mundane_sock", "challenge", true,
                "Краще б не питав", "Better Not to Have Asked", "Дізнайтеся, що було в посилці.", "Find out what was in the package.");
        ALL = List.copyOf(l);
        for (AdvancementSpec a : ALL) {
            if (BY_ID.put(a.id(), a) != null) {
                throw new IllegalStateException("duplicate advancement " + a.id());
            }
        }
    }

    public static AdvancementSpec byId(String id) {
        return BY_ID.get(id);
    }
}
