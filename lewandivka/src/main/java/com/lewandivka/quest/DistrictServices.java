package com.lewandivka.quest;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.campaign.PartyService;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.Reputation;
import com.lewandivka.core.puzzle.FleePlanner;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;
import com.lewandivka.core.world.gen.DistrictPlan;
import com.lewandivka.entity.npc.NpcActions;
import com.lewandivka.entity.npc.NpcEntity;
import com.lewandivka.util.Scheduler;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.Structures;
import com.lewandivka.world.structure.Structures.Marker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The first act in the district: exploring, Mr. Shlahbaum, the Debtor and his chase. */
public final class DistrictServices {

    private static final Random RNG = new Random(0xD3B7L);
    private static FleePlanner planner;
    private static int target = -1;
    private static int previous = -1;
    private static long stumbleUntil = -1;
    private static long nextTaunt;
    private static int taunts;

    private DistrictServices() {
    }

    public static void register() {
        NpcActions.on("pan_shlahbaum", DistrictServices::talkShlahbaum);
        NpcActions.on("debtor", DistrictServices::talkDebtor);
        ServerTickEvents.END_SERVER_TICK.register(DistrictServices::tick);
    }

    // ------------------------------------------------------------------ Mr. Shlahbaum

    private static ActionResult talkShlahbaum(NpcEntity npc, ServerPlayerEntity player, Hand hand) {
        MinecraftServer server = player.getServer();
        List<ServerPlayerEntity> audience = listeners(npc);
        if (!audience.contains(player)) {
            audience.add(player);
        }
        switch (Campaign.step(server)) {
            case TALK_SHLAHBAUM -> {
                Dialogues.play(server, "shlahbaum_kiosk", audience, npc);
                Story.event(server, Events.SHLAHBAUM_MET);
            }
            case GARAGE_DELIVER -> {
                if (QuestInventory.take(player, QuestItems.PACKAGE, 1)) {
                    Campaign.world(server).clearFlag("package.placed");
                    Story.event(server, Events.PACKAGE_DELIVERED);
                } else {
                    player.sendMessage(Text.translatable("message.lewandivka.shlahbaum.need_package"), true);
                }
            }
            case TRAM_REPORT -> Story.event(server, Events.TRAM_REPORTED);
            case EPI_MORNING -> Story.event(server, Events.EPILOGUE_DONE);
            default -> Dialogues.play(server, "shlahbaum_idle_" + (1 + RNG.nextInt(4)), List.of(player), npc);
        }
        return ActionResult.SUCCESS;
    }

    private static List<ServerPlayerEntity> listeners(NpcEntity npc) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (ServerPlayerEntity p : ((ServerWorld) npc.getWorld()).getPlayers()) {
            if (p.squaredDistanceTo(npc) < 18 * 18 && !p.isSpectator()) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the Debtor

    private static ActionResult talkDebtor(NpcEntity npc, ServerPlayerEntity player, Hand hand) {
        MinecraftServer server = player.getServer();
        QuestStep step = Campaign.step(server);
        if (step == QuestStep.DEBTOR_RESOLVE) {
            Dialogues.play(server, "debtor_found", listeners(npc), npc, (who, choice) -> resolve(server, npc, who, choice));
            return ActionResult.SUCCESS;
        }
        if (step == QuestStep.DEBTOR_CHASE) {
            Story.event(server, Events.DEBTOR_CAUGHT);
            npc.getNavigation().stop();
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private static void resolve(MinecraftServer server, NpcEntity npc, ServerPlayerEntity who, String choice) {
        String done;
        switch (choice) {
            case "pay" -> {
                if (!QuestInventory.take(who, "minecraft:emerald", 3)) {
                    who.sendMessage(Text.translatable("message.lewandivka.debtor.need_emeralds"), true);
                    return;
                }
                done = "debtor_resolved_pay";
            }
            case "vouch" -> {
                boolean enough = QuestInventory.count(who, QuestItems.TOKEN) >= 2
                        || Reputation.fromPoints(Campaign.player(who).repPoints()).isAtLeast(Reputation.ACQUAINTANCE);
                if (!enough) {
                    who.sendMessage(Text.translatable("message.lewandivka.debtor.need_tokens"), true);
                    return;
                }
                done = "debtor_resolved_vouch";
            }
            case "seeds" -> {
                if (!QuestInventory.take(who, QuestItems.SEEDS, 8)) {
                    who.sendMessage(Text.translatable("message.lewandivka.debtor.need_seeds"), true);
                    return;
                }
                Campaign.player(who).addRepPoints(1);
                done = "debtor_resolved_seeds";
            }
            default -> {
                return;
            }
        }
        Dialogues.play(server, done, listeners(npc), npc);
        Story.event(server, Events.DEBTOR_RESOLVED);
        Scheduler.later(260, () -> {
            if (npc.isAlive()) {
                ((ServerWorld) npc.getWorld()).spawnParticles(ParticleTypes.POOF, npc.getX(), npc.getY() + 1, npc.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
                npc.discard();
            }
        });
    }

    // ------------------------------------------------------------------ the chase

    private static FleePlanner planner() {
        if (planner == null) {
            List<FleePlanner.Node> nodes = new ArrayList<>();
            for (int i = 0; i < DistrictPlan.DEBTOR_NODES.length; i++) {
                boolean dead = false;
                for (int d : DistrictPlan.DEBTOR_DEAD_ENDS) {
                    dead |= d == i;
                }
                nodes.add(new FleePlanner.Node(i, DistrictPlan.DEBTOR_NODES[i][0] + 0.5, DistrictPlan.DEBTOR_NODES[i][1] + 0.5, dead));
            }
            planner = new FleePlanner(nodes, List.of(DistrictPlan.DEBTOR_EDGES));
        }
        return planner;
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();
        if (now % 5 == 0 && Campaign.step(server) == QuestStep.DEBTOR_CHASE) {
            chase(server, now);
        }
        if (now % 20 == 0) {
            visits(server);
            kioskWatch(server);
        }
    }

    private static NpcEntity debtor(ServerWorld world) {
        for (NpcEntity e : world.getEntitiesByClass(NpcEntity.class, new Box(-400, -64, -400, 400, 320, 400), x -> x.isAlive() && x.spec().id.equals("debtor"))) {
            return e;
        }
        return null;
    }

    private static void chase(MinecraftServer server, long now) {
        ServerWorld world = Dimensions.district(server);
        if (world == null) {
            return;
        }
        NpcEntity debtor = debtor(world);
        if (debtor == null) {
            NpcSpawns.once(server, "debtor", "district:debtor_spawn");
            target = -1;
            return;
        }
        List<double[]> pursuers = new ArrayList<>();
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (!p.isSpectator() && p.isAlive() && p.squaredDistanceTo(debtor) < 90 * 90) {
                pursuers.add(new double[] {p.getX(), p.getZ()});
                if (p.squaredDistanceTo(debtor) < 2.6 * 2.6) {
                    debtor.getNavigation().stop();
                    Story.event(server, Events.DEBTOR_CAUGHT);
                    return;
                }
            }
        }
        if (pursuers.isEmpty()) {
            debtor.getNavigation().stop();
            return;
        }
        if (now < stumbleUntil) {
            debtor.getNavigation().stop();
            return;
        }
        FleePlanner fp = planner();
        boolean arrived = target < 0 || Math.hypot(debtor.getX() - fp.node(target).x(), debtor.getZ() - fp.node(target).z()) < 4.0;
        if (arrived) {
            int current = target < 0 ? fp.nearest(debtor.getX(), debtor.getZ()) : target;
            target = fp.choose(current, previous, pursuers, RNG);
            previous = current;
            if (FleePlanner.stumbles(PartyService.scale(server).fleeStumbleChance(), RNG)) {
                stumbleUntil = now + 60;
                for (ServerPlayerEntity p : world.getPlayers()) {
                    if (p.squaredDistanceTo(debtor) < 60 * 60) {
                        p.sendMessage(Text.translatable("message.lewandivka.debtor.trips"), true);
                    }
                }
                debtor.play("hurt");
            }
        }
        if (now >= nextTaunt) {
            nextTaunt = now + 400;
            Dialogues.play(server, "debtor_taunt_" + (1 + taunts++ % 3), world.getPlayers(), debtor);
        }
        FleePlanner.Node n = fp.node(target);
        double speed = pursuers.size() > 1 ? 0.72 : 0.62;
        debtor.getNavigation().startMovingTo(n.x(), debtor.getY(), n.z(), speed);
    }

    // ------------------------------------------------------------------ places and the kiosk

    private static void visits(MinecraftServer server) {
        if (Campaign.step(server) != QuestStep.EXPLORE_DISTRICT) {
            return;
        }
        ServerWorld world = Dimensions.district(server);
        if (world == null) {
            return;
        }
        for (Marker m : Structures.withPrefix("district", "poi_")) {
            int r = Integer.parseInt(m.data("r", "9"));
            for (ServerPlayerEntity p : world.getPlayers()) {
                double dx = p.getX() - m.x();
                double dz = p.getZ() - m.z();
                if (!p.isSpectator() && dx * dx + dz * dz <= r * r && Math.abs(p.getY() - m.y()) < 14) {
                    if (Campaign.world(server).setFlag("poi." + m.name())) {
                        Campaign.dirty(server);
                        Story.event(server, Events.POI_VISITED);
                    }
                    break;
                }
            }
        }
    }

    private static void kioskWatch(MinecraftServer server) {
        if (Campaign.step(server) != QuestStep.CRAFT_KIOSK) {
            return;
        }
        var kiosk = com.lewandivka.item.GameItems.blockItem("abandoned_kiosk");
        for (ServerPlayerEntity p : PartyService.players(server)) {
            if (p.getInventory().count(kiosk) > 0) {
                Story.event(server, Events.KIOSK_CRAFTED);
                return;
            }
        }
    }
}
