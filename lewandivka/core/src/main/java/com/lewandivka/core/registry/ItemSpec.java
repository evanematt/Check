package com.lewandivka.core.registry;

/** Description of one mod item (the block items are derived from {@link BlockSpec}). */
public final class ItemSpec {

    /** What using the item does (selects the Java item class in the game glue). */
    public enum Use {
        PLAIN, NOTEBOOK, QUEST_NOTE, KETTLE, PACKAGE, COMPOSTER, CHROMA, COMPASS, FOOD, COLLAR, TICKET, RING, CHARGE, KIOSK, SOCK
    }

    public final String id;
    public final Use use;
    public String nameUk;
    public String nameEn;
    public String loreUk = "";
    public String loreEn = "";
    public int stack = 1;
    public String rarity = "common";
    public boolean glint;
    /** Protected by the quest-item ledger: restored automatically if it is ever lost. */
    public boolean quest;
    /** Shown in the creative tab. */
    public boolean inTab = true;
    /** Food: hunger points restored (0 = not food). */
    public int hunger;
    public float saturation;
    /** Eating time in ticks (food only). */
    public int eatTicks = 32;

    private ItemSpec(String id, Use use) {
        this.id = id;
        this.use = use;
    }

    public static ItemSpec of(String id, Use use) {
        return new ItemSpec(id, use);
    }

    public ItemSpec name(String uk, String en) {
        this.nameUk = uk;
        this.nameEn = en;
        return this;
    }

    public ItemSpec lore(String uk, String en) {
        this.loreUk = uk;
        this.loreEn = en;
        return this;
    }

    public ItemSpec stack(int n) {
        this.stack = n;
        return this;
    }

    public ItemSpec rarity(String r) {
        this.rarity = r;
        return this;
    }

    public ItemSpec glint() {
        this.glint = true;
        return this;
    }

    public ItemSpec quest() {
        this.quest = true;
        return this;
    }

    public ItemSpec hidden() {
        this.inTab = false;
        return this;
    }

    public ItemSpec food(int hunger, float saturation, int eatTicks) {
        this.hunger = hunger;
        this.saturation = saturation;
        this.eatTicks = eatTicks;
        return this;
    }

    public String nameKey() {
        return "item.lewandivka." + id;
    }

    public String loreKey() {
        return "item.lewandivka." + id + ".lore";
    }
}
