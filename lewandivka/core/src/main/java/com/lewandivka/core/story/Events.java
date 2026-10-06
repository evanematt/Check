package com.lewandivka.core.story;

/**
 * Every story event of the game. Systems (flows, entities, services) only report that something happened; the
 * {@link CampaignDirector} decides what it means for the campaign. Ids are stable (they are stored as "pending" flags).
 */
public final class Events {

    private Events() {
    }

    // ---- act 1: the district
    public static final String POI_VISITED = "district.poi";
    public static final String TOKEN_COLLECTED = "district.token";
    public static final String KIOSK_CRAFTED = "kiosk.crafted";
    public static final String KIOSK_PLACED = "kiosk.placed";
    public static final String SHLAHBAUM_MET = "shlahbaum.met";
    public static final String DEBTOR_NOTE_USED = "debtor.note";
    public static final String DEBTOR_CLUE = "debtor.clue";
    public static final String DEBTOR_CAUGHT = "debtor.caught";
    public static final String DEBTOR_RESOLVED = "debtor.resolved";
    public static final String KETTLE_PLACED = "kettle.placed";
    public static final String G13_ENTERED = "g13.entered";
    public static final String G13_POWER = "g13.power";
    public static final String G13_PACKAGE_TAKEN = "g13.package_taken";
    public static final String G13_ESCAPED = "g13.escaped";
    public static final String PACKAGE_DELIVERED = "package.delivered";
    public static final String TRAM_STARTED = "tram.started";
    public static final String TRAM_WAVE = "tram.wave";
    public static final String TRAM_REPORTED = "tram.reported";
    public static final String TABLET_TAKEN = "chroma.tablet";
    public static final String CHROMA_SYNCED = "chroma.synced";
    public static final String TRANSITION_DONE = "transition.done";

    // ---- act 2: Chromandivka
    public static final String BASE_COMPASS = "base.compass";
    public static final String BASE_PEDESTAL = "base.pedestal";
    public static final String RG_ENTERED = "rg.entered";
    public static final String RG_WING = "rg.wing";
    public static final String SH_HALL_ENTERED = "sh.hall";
    public static final String SH_LEVERS_DONE = "sh.levers";
    public static final String SH_CHINAZIK_DONE = "sh.chinazik";
    public static final String SH_METADONNA_FOUND = "sh.metadonna";
    public static final String AQ_ENTERED = "aq.entered";
    public static final String AQ_PUMP = "aq.pump";
    public static final String SKY_STOP_REACHED = "sky.stop";
    public static final String SKY_ARRIVED = "sky.arrived";
    public static final String DEPOT_SWITCHES_SOLVED = "depot.switches";
    public static final String DEPOT_TICKET = "depot.ticket";
    public static final String RING_RESTORED = "ring.restored";
    public static final String APPROACH_DONE = "approach.done";
    public static final String TOWER_FLOOR = "tower.floor";
    public static final String EPILOGUE_BASE = "epilogue.base";
    public static final String EPILOGUE_PORTAL = "epilogue.portal";
    public static final String EPILOGUE_DONE = "epilogue.done";

    // ---- bosses (event id = "boss." + boss id)
    public static final String BOSS_FARE_LEADER = "boss.fare_dodger_leader";
    public static final String BOSS_GARAGE_KING = "boss.garage_king";
    public static final String BOSS_COLLAR_COLLECTOR = "boss.collar_collector";
    public static final String BOSS_LADY_VORTEX = "boss.lady_vortex";
    public static final String BOSS_CONDUCTOR = "boss.conductor";
    public static final String BOSS_COLORLESS_HEAD = "boss.colorless_head";

    public static String boss(String bossId) {
        return "boss." + bossId;
    }
}
