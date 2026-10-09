package com.lewandivka.core.registry;

import com.lewandivka.core.registry.EntitySpec.Group;
import com.lewandivka.core.registry.EntitySpec.Role;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catalog of every entity type of the mod. */
public final class ModEntities {

    private ModEntities() {
    }

    /** Animation names every humanoid NPC model provides. */
    public static final String[] HUMANOID_ANIMS = {"idle", "walk", "run", "attack", "hurt", "talk", "throw"};
    /** The animations the specification requires from both cats. */
    public static final String[] CAT_ANIMS = {"idle", "sit", "walk", "run", "look", "sniff", "eat", "scratch", "sleep", "alert", "point"};
    public static final String[] BOSS_ANIMS = {"idle", "walk", "telegraph", "attack", "slam", "shield", "stagger", "death", "phase"};

    public static final List<EntitySpec> ALL;
    private static final Map<String, EntitySpec> BY_ID = new LinkedHashMap<>();

    static {
        List<EntitySpec> l = new ArrayList<>();
        // ---- Act 1 ----
        l.add(EntitySpec.of("gopnik", Role.GOPNIK, Group.MONSTER).name("Гопник", "Gopnik").stats(20, 3, 0.30)
                .visual("humanoid", "gopnik").egg(0x1b1b1b, 0xe8e8e8).anims(ModEntities.HUMANOID_ANIMS));
        l.add(EntitySpec.of("seed_thrower", Role.SEED_THROWER, Group.MONSTER).name("Сємочнік", "Seed Thrower").stats(16, 2, 0.28)
                .visual("humanoid", "seed_thrower").egg(0x2d2d2d, 0xd6b24a).anims(ModEntities.HUMANOID_ANIMS));
        l.add(EntitySpec.of("senior_yard_gopnik", Role.SENIOR_GOPNIK, Group.MONSTER).name("Старший по двору", "Senior Yard Gopnik")
                .stats(44, 5, 0.26).armor(4, 0.3).size(0.7f, 2.0f).visual("humanoid", "senior_yard_gopnik").egg(0x101010, 0xb02020)
                .anims(ModEntities.HUMANOID_ANIMS).scale(1.1f));
        l.add(EntitySpec.of("debtor", Role.DEBTOR, Group.CREATURE).name("Боржник", "The Debtor").stats(20, 0, 0.36)
                .visual("humanoid", "debtor").egg(0x6b5a3a, 0x9bb0a0).anims(ModEntities.HUMANOID_ANIMS));
        l.add(EntitySpec.of("pan_shlahbaum", Role.SHLAHBAUM, Group.CREATURE).name("Пан Шлагбаум", "Mr. Shlahbaum").stats(60, 0, 0.0)
                .visual("humanoid", "pan_shlahbaum").egg(0x8a8a8a, 0xd0a020).anims(ModEntities.HUMANOID_ANIMS));
        l.add(EntitySpec.of("fare_dodger", Role.FARE_DODGER, Group.MONSTER).name("Безбілетник", "Fare Dodger").stats(18, 3, 0.31)
                .visual("humanoid", "fare_dodger").egg(0x4a5a2a, 0xdddddd).anims(ModEntities.HUMANOID_ANIMS));
        l.add(EntitySpec.of("fare_dodger_leader", Role.FARE_LEADER, Group.MONSTER).name("Ватажок безквиткових", "Fare Dodger Leader")
                .stats(140, 6, 0.28).armor(6, 0.6).size(0.8f, 2.2f).visual("humanoid", "fare_dodger_leader").egg(0x3a1a1a, 0xe0c040)
                .boss().anims(ModEntities.HUMANOID_ANIMS).scale(1.25f));
        // ---- cats ----
        l.add(EntitySpec.of("chinazik", Role.CAT, Group.CREATURE).name("Чіназік", "Chinazik").size(0.6f, 0.7f).stats(10, 0, 0.3)
                .visual("cat", "chinazik").egg(0x151515, 0x555555).anims(ModEntities.CAT_ANIMS));
        l.add(EntitySpec.of("metadonna", Role.CAT, Group.CREATURE).name("Метадонна", "Metadonna").size(0.6f, 0.7f).stats(10, 0, 0.32)
                .visual("cat", "metadonna").egg(0x8a8a8a, 0xf0e8d8).anims(ModEntities.CAT_ANIMS));
        // ---- the people of the district: citizens to talk to, vendors of the market to trade with ----
        for (Object[] c : new Object[][] {
                {"citizen_babushka", "Бабуся", "Old Woman", 1.0f, 0xb04a6a, 0xe8d8a0},
                {"citizen_grandpa", "Дідусь", "Old Man", 1.0f, 0x6a5a40, 0xd0d0c8},
                {"citizen_worker", "Робітник", "Worker", 1.0f, 0x3a5a8a, 0xe0a030},
                {"citizen_student", "Студентка", "Student", 1.0f, 0x6a3a7a, 0xe8c0d0},
                {"citizen_kid", "Дитина", "Kid", 0.62f, 0xd08a20, 0x5ab0d0},
                {"citizen_teacher", "Вчителька", "Teacher", 1.0f, 0x6a2a2a, 0xe0e0d0},
                {"citizen_yard_keeper", "Двірник", "Yard Keeper", 1.0f, 0x4a6a3a, 0xc8a040},
                {"citizen_neighbour", "Сусід", "Neighbour", 1.0f, 0x7a7a8a, 0xb04040}}) {
            float scale = (float) c[3];
            EntitySpec e = EntitySpec.of((String) c[0], Role.CITIZEN, Group.CREATURE).name((String) c[1], (String) c[2])
                    .stats(20, 0, 0.22).visual("humanoid", (String) c[0]).egg((int) c[4], (int) c[5]).anims(ModEntities.HUMANOID_ANIMS);
            if (scale < 1.0f) {
                e.size(0.4f, 1.25f).scale(scale);
            }
            l.add(e);
        }
        for (Object[] v : new Object[][] {
                {"vendor_baker", "Пекарка", "Baker", 0xe8e0d0, 0xb07a30},
                {"vendor_greengrocer", "Овочівниця", "Greengrocer", 0x4a8a3a, 0xd05a3a},
                {"vendor_butcher", "М'ясник", "Butcher", 0xa03a3a, 0xe8e0d8},
                {"vendor_handyman", "Майстер", "Handyman", 0x7a5a3a, 0x9aa0a8},
                {"vendor_flea", "Барахольник", "Flea Trader", 0x6a6a4a, 0xc07a9a},
                {"vendor_fishmonger", "Рибалка", "Fishmonger", 0x3a6a8a, 0xd8d070},
                {"vendor_gardener", "Садівник", "Gardener", 0x5a7a3a, 0xe0c0d0}}) {
            l.add(EntitySpec.of((String) v[0], Role.VENDOR, Group.CREATURE).name((String) v[1], (String) v[2])
                    .stats(30, 0, 0.2).visual("humanoid", (String) v[0]).egg((int) v[3], (int) v[4]).anims(ModEntities.HUMANOID_ANIMS));
        }
        // ---- bosses ----
        l.add(EntitySpec.of("garage_king", Role.BOSS, Group.MONSTER).name("Гаражний Король", "Garage King").size(2.6f, 3.4f).stats(300, 8, 0.22)
                .armor(8, 1.0).visual("garage_king", "garage_king").egg(0xb05a20, 0x404040).boss().anims(ModEntities.BOSS_ANIMS));
        l.add(EntitySpec.of("collar_collector", Role.BOSS, Group.MONSTER).name("Колекціонер Нашийників", "Collar Collector").size(1.0f, 2.8f).stats(260, 7, 0.26)
                .armor(4, 1.0).visual("collar_collector", "collar_collector").egg(0x30204a, 0xc0a040).boss().anims(ModEntities.BOSS_ANIMS));
        l.add(EntitySpec.of("lady_vortex", Role.BOSS, Group.MONSTER).name("Пані Вирва", "Lady Vortex").size(1.2f, 3.0f).stats(280, 7, 0.24)
                .armor(4, 1.0).visual("lady_vortex", "lady_vortex").egg(0x2080c0, 0xe0e040).boss().anims(ModEntities.BOSS_ANIMS));
        l.add(EntitySpec.of("conductor", Role.BOSS, Group.MONSTER).name("Кондуктор", "The Conductor").size(1.0f, 2.9f).stats(320, 8, 0.26)
                .armor(6, 1.0).visual("conductor", "conductor").egg(0x202a40, 0xd0b050).boss().anims(ModEntities.BOSS_ANIMS));
        l.add(EntitySpec.of("colorless_head", Role.BOSS, Group.MONSTER).name("Безбарвний Голова", "The Colorless Head").size(1.6f, 4.0f).stats(500, 10, 0.22)
                .armor(8, 1.0).visual("colorless_head", "colorless_head").egg(0x808080, 0xe8e8e8).boss().anims(ModEntities.BOSS_ANIMS));
        // ---- boss helpers ----
        l.add(EntitySpec.of("mechanic_minion", Role.MINION, Group.MONSTER).name("Механік-міньйон", "Mechanic Minion").size(0.6f, 0.8f).stats(10, 2, 0.34)
                .visual("minion", "mechanic_minion").egg(0xb05a20, 0xcccccc).anims("idle", "walk", "attack"));
        l.add(EntitySpec.of("leash_anchor", Role.MARKER, Group.MISC).name("Якір повідка", "Leash Anchor").size(0.8f, 1.2f).stats(30, 0, 0)
                .visual("leash_anchor", "leash_anchor").anims("idle", "break"));
        l.add(EntitySpec.of("arena_tram", Role.VEHICLE, Group.MISC).name("Арена-трамвай", "Arena Tram").size(3.0f, 3.0f).stats(1, 0, 0)
                .visual("tram", "arena_tram").anims("idle", "move"));
        l.add(EntitySpec.of("sky_tram", Role.VEHICLE, Group.MISC).name("Небесний трамвай", "Sky Tram").size(3.0f, 3.2f).stats(1, 0, 0)
                .visual("tram", "sky_tram").anims("idle", "move"));
        l.add(EntitySpec.of("seed_projectile", Role.PROJECTILE, Group.MISC).name("Сємка", "Seed").size(0.25f, 0.25f)
                .visual("projectile", "seed_projectile"));
        l.add(EntitySpec.of("wheel_projectile", Role.PROJECTILE, Group.MISC).name("Колесо", "Wheel").size(0.9f, 0.9f)
                .visual("wheel", "wheel_projectile"));
        ALL = List.copyOf(l);
        for (EntitySpec s : ALL) {
            if (BY_ID.put(s.id, s) != null) {
                throw new IllegalStateException("duplicate entity id " + s.id);
            }
        }
    }

    public static EntitySpec byId(String id) {
        return BY_ID.get(id);
    }
}
