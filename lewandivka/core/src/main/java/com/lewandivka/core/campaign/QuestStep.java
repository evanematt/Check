package com.lewandivka.core.campaign;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fine-grained, strictly ordered progression steps. This is the real campaign state machine;
 * {@link CampaignStage} is derived from the current step.
 *
 * <p>Ids are persisted and must never be renumbered. New steps can only be appended with new ids
 * (ordering is then given by {@link #order}), which keeps old saves loadable.</p>
 *
 * <p>Language keys: {@code quest.<key>.title}, {@code quest.<key>.objective}, {@code quest.<key>.hint}.</p>
 */
public enum QuestStep {
    // --- PROLOGUE ---
    EXPLORE_DISTRICT(0, 0, CampaignStage.PROLOGUE, 4, null),

    // --- FIRST_NIGHT ---
    COLLECT_TOKENS(1, 10, CampaignStage.FIRST_NIGHT, 4, null),

    // --- DISTRICT_REPUTATION (kiosk) ---
    CRAFT_KIOSK(2, 20, CampaignStage.DISTRICT_REPUTATION, 0, null),
    PLACE_KIOSK(3, 21, CampaignStage.DISTRICT_REPUTATION, 0, null),
    TALK_SHLAHBAUM(4, 22, CampaignStage.DISTRICT_REPUTATION, 0, null),

    // --- DEBTOR ---
    DEBTOR_NOTE(5, 30, CampaignStage.DEBTOR, 0, null),
    DEBTOR_CLUES(6, 31, CampaignStage.DEBTOR, 5, null),
    DEBTOR_CHASE(7, 32, CampaignStage.DEBTOR, 0, null),
    DEBTOR_RESOLVE(8, 33, CampaignStage.DEBTOR, 0, null),
    KETTLE_TEST(9, 34, CampaignStage.DEBTOR, 0, null),

    // --- GARAGE_13 ---
    GARAGE_FIND(10, 40, CampaignStage.GARAGE_13, 0, null),
    GARAGE_PANELS(11, 41, CampaignStage.GARAGE_13, 3, null),
    GARAGE_PACKAGE(12, 42, CampaignStage.GARAGE_13, 0, null),
    GARAGE_ESCAPE(13, 43, CampaignStage.GARAGE_13, 0, null),
    GARAGE_DELIVER(14, 44, CampaignStage.GARAGE_13, 0, null),

    // --- LAST_TRAM ---
    TRAM_WAIT(15, 50, CampaignStage.LAST_TRAM, 0, null),
    TRAM_FIGHT(16, 51, CampaignStage.LAST_TRAM, 3, null),
    TRAM_REPORT(17, 52, CampaignStage.LAST_TRAM, 0, null),

    // --- CHROMA_READY / CHROMA_TRANSITION ---
    CHROMA_TAKE(18, 60, CampaignStage.CHROMA_READY, 0, null),
    TRANSITION(19, 70, CampaignStage.CHROMA_TRANSITION, 0, null),

    // --- CHROMANDIVKA_BASE ---
    BASE_WAKE(20, 80, CampaignStage.CHROMANDIVKA_BASE, 0, null),
    BASE_PEDESTALS(21, 81, CampaignStage.CHROMANDIVKA_BASE, 4, null),

    // --- RAINBOW_GARAGE ---
    RG_TRAVEL(22, 90, CampaignStage.RAINBOW_GARAGE, 0, "rainbow_garage:entrance"),
    RG_WINGS(23, 91, CampaignStage.RAINBOW_GARAGE, 3, "rainbow_garage:hub"),
    RG_BOSS(24, 92, CampaignStage.RAINBOW_GARAGE, 0, "rainbow_garage:boss_door"),

    // --- SHELTER ---
    SH_DASH(25, 100, CampaignStage.SHELTER, 0, "shelter:entrance"),
    SH_LEVERS(26, 101, CampaignStage.SHELTER, 3, "shelter:lever_hall"),
    SH_CHINAZIK(27, 102, CampaignStage.SHELTER, 4, "shelter:cat_hall"),
    SH_METADONNA(28, 103, CampaignStage.SHELTER, 0, "shelter:box_warehouse"),
    SH_BOSS(29, 104, CampaignStage.SHELTER, 0, "shelter:boss_door"),

    // --- AQUAPARK ---
    AQ_FIND(30, 110, CampaignStage.AQUAPARK, 0, "aquapark:area"),
    AQ_PUMPS(31, 111, CampaignStage.AQUAPARK, 3, "aquapark:pump_room"),
    AQ_BOSS(32, 112, CampaignStage.AQUAPARK, 0, "aquapark:boss_door"),

    // --- SKY_DEPOT ---
    SKY_ASCENT(33, 120, CampaignStage.SKY_DEPOT, 0, "sky_ascent:ascent_start"),
    SKY_RIDE(34, 121, CampaignStage.SKY_DEPOT, 0, "sky_ascent:tram_stop_lower"),
    SKY_SWITCHES(35, 122, CampaignStage.SKY_DEPOT, 0, "sky_depot:dispatcher"),
    SKY_TICKETS(36, 123, CampaignStage.SKY_DEPOT, 3, "sky_depot:ticket_office"),
    SKY_BOSS(37, 124, CampaignStage.SKY_DEPOT, 0, "sky_depot:boss_door"),

    // --- TOWER ---
    TOWER_RING(38, 130, CampaignStage.TOWER, 0, null),
    TOWER_APPROACH(39, 131, CampaignStage.TOWER, 0, "tower_approach:approach_start"),
    TOWER_CLIMB(40, 132, CampaignStage.TOWER, 5, "tower:entrance"),

    // --- FINAL_BOSS ---
    FINAL_FIGHT(41, 140, CampaignStage.FINAL_BOSS, 0, "tower:boss_door"),

    // --- EPILOGUE ---
    EPI_RETURN(42, 150, CampaignStage.EPILOGUE, 0, null),
    EPI_PORTAL(43, 151, CampaignStage.EPILOGUE, 0, "base:portal"),
    EPI_MORNING(44, 152, CampaignStage.EPILOGUE, 0, null),

    // --- POSTGAME ---
    POST_FREE(45, 160, CampaignStage.POSTGAME, 0, null);

    public final int id;
    /** Sorting position. Gaps leave room for steps added by later versions without renumbering ids. */
    public final int order;
    public final CampaignStage stage;
    /** Number of sub-objectives shown as "(n/max)" in the notebook; 0 when not a counted step. */
    public final int counterMax;
    /** Marker id the Chromatic Compass points at during this step, or null. */
    public final String compassMarker;

    QuestStep(int id, int order, CampaignStage stage, int counterMax, String compassMarker) {
        this.id = id;
        this.order = order;
        this.stage = stage;
        this.counterMax = counterMax;
        this.compassMarker = compassMarker;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String titleKey() {
        return "quest." + key() + ".title";
    }

    public String objectiveKey() {
        return "quest." + key() + ".objective";
    }

    public String hintKey() {
        return "quest." + key() + ".hint";
    }

    public boolean isBefore(QuestStep other) {
        return order < other.order;
    }

    public boolean isAtLeast(QuestStep other) {
        return order >= other.order;
    }

    public boolean isAfter(QuestStep other) {
        return order > other.order;
    }

    /** The step after this one in campaign order, or this step when it is the last. */
    public QuestStep next() {
        QuestStep best = null;
        for (QuestStep s : values()) {
            if (s.order > order && (best == null || s.order < best.order)) {
                best = s;
            }
        }
        return best == null ? this : best;
    }

    public static QuestStep byId(int id) {
        for (QuestStep s : values()) {
            if (s.id == id) {
                return s;
            }
        }
        return EXPLORE_DISTRICT;
    }

    public static QuestStep byKey(String key) {
        for (QuestStep s : values()) {
            if (s.key().equalsIgnoreCase(key)) {
                return s;
            }
        }
        return null;
    }

    /** First step of a stage; used by {@code /lewandivka setstage}. */
    public static QuestStep firstOf(CampaignStage stage) {
        QuestStep best = null;
        for (QuestStep s : values()) {
            if (s.stage == stage && (best == null || s.order < best.order)) {
                best = s;
            }
        }
        return best;
    }

    /** Steps in campaign order. */
    public static List<QuestStep> ordered() {
        List<QuestStep> list = new ArrayList<>(List.of(values()));
        list.sort((a, b) -> Integer.compare(a.order, b.order));
        return list;
    }
}
