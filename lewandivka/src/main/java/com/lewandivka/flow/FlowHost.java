package com.lewandivka.flow;

import com.lewandivka.LewandivkaMod;
import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.flow.Flow;
import com.lewandivka.core.flow.dungeon.AquaparkFlow;
import com.lewandivka.core.flow.dungeon.BaseFlow;
import com.lewandivka.core.flow.dungeon.Garage0Flow;
import com.lewandivka.core.flow.dungeon.PostgameFlow;
import com.lewandivka.core.flow.dungeon.Garage13Flow;
import com.lewandivka.core.flow.dungeon.LastTramFlow;
import com.lewandivka.core.flow.dungeon.RainbowGarageFlow;
import com.lewandivka.core.flow.dungeon.ShelterFlow;
import com.lewandivka.core.flow.dungeon.SkyAscentFlow;
import com.lewandivka.core.flow.dungeon.SkyDepotFlow;
import com.lewandivka.core.flow.dungeon.TowerApproachFlow;
import com.lewandivka.core.flow.dungeon.TowerFlow;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Owns the running dungeon flows. A flow is created the first time somebody gets near its structure, brought up to date
 * with the saved progress ({@code rebuild}), ticked while players are inside and reset when everybody has been away for
 * a while (solved puzzles stay solved, volatile parts start over).
 */
public final class FlowHost {

    private static final class Slot {
        final GameEnv env;
        final Flow flow;
        final Set<UUID> inside = new HashSet<>();
        long emptySince = -1;
        boolean occupied;

        Slot(GameEnv env, Flow flow) {
            this.env = env;
            this.flow = flow;
        }
    }

    private static final Map<String, Function<com.lewandivka.core.flow.FlowEnv, Flow>> FACTORIES = new HashMap<>();
    private static final Map<String, Integer> MARGINS = new HashMap<>();
    private static final Map<String, Slot> SLOTS = new HashMap<>();
    private static final int RESET_AFTER_TICKS = 20 * 20;
    /** The whole district is one "structure" for the free-play flow; small props of it are routed to it by id. */
    public static final String WORLD_DISTRICT = "district";

    static {
        FACTORIES.put("garage13", Garage13Flow::new);
        FACTORIES.put("tram_stop", LastTramFlow::new);
        FACTORIES.put("base", BaseFlow::new);
        FACTORIES.put("rainbow_garage", RainbowGarageFlow::new);
        FACTORIES.put("shelter", ShelterFlow::new);
        FACTORIES.put("aquapark", AquaparkFlow::new);
        FACTORIES.put("sky_ascent", SkyAscentFlow::new);
        FACTORIES.put("sky_depot", SkyDepotFlow::new);
        FACTORIES.put("tower_approach", TowerApproachFlow::new);
        FACTORIES.put("tower", TowerFlow::new);
        FACTORIES.put("garage0", Garage0Flow::new);
        FACTORIES.put(WORLD_DISTRICT, PostgameFlow::new);
        MARGINS.put("tram_stop", 70);
        MARGINS.put("sky_ascent", 400);            // the sky tram leaves the structure on its two minute ride
    }

    private FlowHost() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FlowHost::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> SLOTS.clear());
    }

    public static void addFlow(String structure, Function<com.lewandivka.core.flow.FlowEnv, Flow> factory) {
        FACTORIES.put(structure, factory);
    }

    public static Iterable<String> structures() {
        return FACTORIES.keySet();
    }

    /** How far outside its box a structure still counts as "inside" (the sky tram ride, the last tram fight). */
    public static int margin(String structure) {
        return MARGINS.getOrDefault(structure, 10);
    }

    /** Structures that belong to another flow: the seed bowls and the validator post of the wrong tram stop. */
    public static String resolve(String structure) {
        if (structure.startsWith("seed_bowl_") || structure.equals("wrong_stop")) {
            return WORLD_DISTRICT;
        }
        return structure;
    }

    /** True for flows that cover a whole dimension instead of one structure. */
    public static boolean isWorldFlow(String structure) {
        return structure.equals(WORLD_DISTRICT);
    }

    /** The dimension id of a structure or world flow, or null. */
    public static String dimensionOf(String structure) {
        return isWorldFlow(structure) ? Dimensions.DISTRICT_ID : Structures.dimensionOf(structure);
    }

    /** The players who count as "inside" a structure (everybody in the dimension for a world flow). */
    public static java.util.List<ServerPlayerEntity> playersOf(MinecraftServer server, String structure) {
        if (isWorldFlow(structure)) {
            java.util.List<ServerPlayerEntity> out = new java.util.ArrayList<>();
            for (ServerPlayerEntity p : com.lewandivka.campaign.PartyService.players(server)) {
                if (Dimensions.DISTRICT_ID.equals(Dimensions.idOf(p.getWorld()))) {
                    out.add(p);
                }
            }
            return out;
        }
        return com.lewandivka.campaign.PartyService.inStructure(server, structure, Math.max(12, margin(structure)));
    }

    /** The flow of a structure, created and rebuilt on first use; null for structures without a flow. */
    public static Flow flow(MinecraftServer server, String structure) {
        Slot s = slot(server, resolve(structure));
        return s == null ? null : s.flow;
    }

    private static Slot slot(MinecraftServer server, String structure) {
        Slot s = SLOTS.get(structure);
        if (s != null) {
            return s;
        }
        Function<com.lewandivka.core.flow.FlowEnv, Flow> factory = FACTORIES.get(structure);
        if (factory == null || dimensionOf(structure) == null || (!isWorldFlow(structure) && Structures.site(structure) == null)
                || Dimensions.world(server, dimensionOf(structure)) == null) {
            return null;
        }
        GameEnv env = new GameEnv(server, structure);
        env.beginEncounter();
        Flow flow = factory.apply(env);
        try {
            flow.rebuild();
        } catch (RuntimeException e) {
            LewandivkaMod.LOGGER.error("Flow {} could not rebuild its state", structure, e);
        }
        s = new Slot(env, flow);
        SLOTS.put(structure, s);
        return s;
    }

    /** A boss of a structure died. */
    public static void bossDefeated(MinecraftServer server, String bossId) {
        for (String structure : FACTORIES.keySet()) {
            Slot s = SLOTS.get(structure);
            if (s == null) {
                s = slot(server, structure);
            }
            if (s != null) {
                s.flow.bossDefeated(bossId);
            }
        }
        Campaign.dirty(server);
    }

    /** Admin command: back to the last valid checkpoint. */
    public static boolean reset(MinecraftServer server, String structure) {
        Slot s = slot(server, structure);
        if (s == null) {
            return false;
        }
        s.flow.reset();
        s.flow.rebuild();
        Campaign.dirty(server);
        return true;
    }

    /** Admin command: forget everything about an encounter (not only the volatile part). */
    public static boolean resetAll(MinecraftServer server, String structure) {
        Slot s = slot(server, structure);
        if (s == null) {
            return false;
        }
        s.flow.reset();
        Campaign.world(server).encounter(structure).resetAll();
        SLOTS.remove(structure);
        slot(server, structure);
        Campaign.dirty(server);
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 2 != 0) {
            return;
        }
        long now = server.getTicks();
        for (String structure : FACTORIES.keySet()) {
            if (!isWorldFlow(structure) && Structures.site(structure) == null) {
                continue;
            }
            var players = playersOf(server, structure);
            Slot s = SLOTS.get(structure);
            if (players.isEmpty()) {
                if (s != null && s.occupied) {
                    if (s.emptySince < 0) {
                        s.emptySince = now;
                    } else if (now - s.emptySince > RESET_AFTER_TICKS) {
                        s.occupied = false;
                        s.inside.clear();
                        if (!s.flow.complete()) {
                            s.flow.reset();
                            Campaign.dirty(server);
                        }
                    }
                }
                continue;
            }
            if (s == null) {
                s = slot(server, structure);
                if (s == null) {
                    continue;
                }
            }
            if (!s.occupied) {
                s.occupied = true;
                s.env.beginEncounter();
            }
            s.emptySince = -1;
            for (ServerPlayerEntity p : players) {
                if (s.inside.add(p.getUuid())) {
                    s.flow.playerEntered(p.getUuid());
                }
            }
            s.inside.removeIf(id -> players.stream().noneMatch(p -> p.getUuid().equals(id)));
            try {
                s.flow.tick();
            } catch (RuntimeException e) {
                LewandivkaMod.LOGGER.error("Flow {} failed in its tick", structure, e);
            }
            if (now % 40 == 0) {
                Campaign.dirty(server);
            }
        }
    }
}
