package com.lewandivka.core.campaign;

/**
 * Coarse campaign stages. The ids are stored in saves and must never be renumbered.
 *
 * <p>The finer-grained progression inside a stage is modelled by {@link QuestStep}.</p>
 */
public enum CampaignStage {
    PROLOGUE(0, Act.DISTRICT),
    FIRST_NIGHT(1, Act.DISTRICT),
    DISTRICT_REPUTATION(2, Act.DISTRICT),
    DEBTOR(3, Act.DISTRICT),
    GARAGE_13(4, Act.DISTRICT),
    LAST_TRAM(5, Act.DISTRICT),
    CHROMA_READY(6, Act.DISTRICT),
    CHROMA_TRANSITION(7, Act.DISTRICT),
    CHROMANDIVKA_BASE(8, Act.CHROMANDIVKA),
    RAINBOW_GARAGE(9, Act.CHROMANDIVKA),
    SHELTER(10, Act.CHROMANDIVKA),
    AQUAPARK(11, Act.CHROMANDIVKA),
    SKY_DEPOT(12, Act.CHROMANDIVKA),
    TOWER(13, Act.CHROMANDIVKA),
    FINAL_BOSS(14, Act.CHROMANDIVKA),
    EPILOGUE(15, Act.CHROMANDIVKA),
    POSTGAME(16, Act.FREE);

    /** Which dimension the stage is played in. */
    public enum Act {
        DISTRICT, CHROMANDIVKA, FREE
    }

    private static final CampaignStage[] BY_ID = new CampaignStage[17];

    static {
        for (CampaignStage s : values()) {
            BY_ID[s.id] = s;
        }
    }

    public final int id;
    public final Act act;

    CampaignStage(int id, Act act) {
        this.id = id;
        this.act = act;
    }

    /** Lower-case key used in commands, lang files and advancements. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static CampaignStage byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : PROLOGUE;
    }

    public static CampaignStage byKey(String key) {
        for (CampaignStage s : values()) {
            if (s.key().equalsIgnoreCase(key)) {
                return s;
            }
        }
        return null;
    }

    public CampaignStage next() {
        return this == POSTGAME ? POSTGAME : byId(id + 1);
    }

    public boolean isBefore(CampaignStage other) {
        return id < other.id;
    }

    public boolean isAtLeast(CampaignStage other) {
        return id >= other.id;
    }
}
