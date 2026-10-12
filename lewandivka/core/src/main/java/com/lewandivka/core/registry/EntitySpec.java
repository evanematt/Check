package com.lewandivka.core.registry;

import java.util.ArrayList;
import java.util.List;

/** Description of one mod entity type (attributes, model, texture, animations). */
public final class EntitySpec {

    public enum Group { MONSTER, CREATURE, MISC }

    /** Selects the Java class in the game glue. */
    public enum Role {
        GOPNIK, SEED_THROWER, SENIOR_GOPNIK, DEBTOR, SHLAHBAUM, FARE_DODGER, FARE_LEADER, CAT, BOSS, MINION, PROJECTILE, VEHICLE, MARKER,
        /** The people of the district: they stand about and have a few words to say. */
        CITIZEN,
        /** The traders of the market: they have a counter and sell and buy things. */
        VENDOR
    }

    public final String id;
    public final Role role;
    public final Group group;
    public String nameUk;
    public String nameEn;
    public float width = 0.6f;
    public float height = 1.95f;
    public double health = 20;
    public double attack = 3;
    public double speed = 0.3;
    public double armor;
    public double knockbackResistance;
    public double followRange = 24;
    /** GeckoLib geometry file name (without extension) in {@code geo/entity}. */
    public String model;
    /** Texture name in {@code textures/entity}. */
    public String texture;
    public int eggPrimary = 0x5a5a5a;
    public int eggSecondary = 0xc8c8c8;
    public boolean boss;
    public boolean fireproof = true;
    /** Visual scale of the model. */
    public float scale = 1f;
    public final List<String> animations = new ArrayList<>();

    private EntitySpec(String id, Role role, Group group) {
        this.id = id;
        this.role = role;
        this.group = group;
    }

    public static EntitySpec of(String id, Role role, Group group) {
        return new EntitySpec(id, role, group);
    }

    public EntitySpec name(String uk, String en) {
        this.nameUk = uk;
        this.nameEn = en;
        return this;
    }

    public EntitySpec size(float w, float h) {
        this.width = w;
        this.height = h;
        return this;
    }

    public EntitySpec stats(double health, double attack, double speed) {
        this.health = health;
        this.attack = attack;
        this.speed = speed;
        return this;
    }

    public EntitySpec armor(double armor, double knockbackResistance) {
        this.armor = armor;
        this.knockbackResistance = knockbackResistance;
        return this;
    }

    public EntitySpec visual(String model, String texture) {
        this.model = model;
        this.texture = texture;
        return this;
    }

    public EntitySpec egg(int primary, int secondary) {
        this.eggPrimary = primary;
        this.eggSecondary = secondary;
        return this;
    }

    public EntitySpec boss() {
        this.boss = true;
        return this;
    }

    public EntitySpec scale(float s) {
        this.scale = s;
        return this;
    }

    public EntitySpec anims(String... names) {
        animations.addAll(List.of(names));
        return this;
    }

    public String nameKey() {
        return "entity.lewandivka." + id;
    }
}
