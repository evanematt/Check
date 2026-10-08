package com.lewandivka.command;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.campaign.PartyService;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.CampaignStage;
import com.lewandivka.core.campaign.PlayerProgress;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.WorldProgress;
import com.lewandivka.flow.FlowHost;
import com.lewandivka.quest.Dialogues;
import com.lewandivka.quest.Travel;
import com.lewandivka.world.dimension.Dimensions;
import com.lewandivka.world.structure.StructureValidator;
import com.lewandivka.world.structure.Structures;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.BlockView;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * {@code /lewandivka ...}: the admin and recovery commands of the campaign. Everything needs permission level 2 because
 * it can change the story.
 */
public final class LewCommands {

    private LewCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(LewCommands::build);
    }

    private static void build(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access, CommandManager.RegistrationEnvironment environment) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("lewandivka").requires(s -> s.hasPermissionLevel(2));
        root.then(CommandManager.literal("status").executes(c -> status(c.getSource())));
        root.then(CommandManager.literal("setstage")
                .then(CommandManager.argument("stage", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (CampaignStage s : CampaignStage.values()) {
                                b.suggest(s.key());
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> setStage(c.getSource(), StringArgumentType.getString(c, "stage")))));
        root.then(CommandManager.literal("step")
                .then(CommandManager.argument("step", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (QuestStep s : QuestStep.ordered()) {
                                b.suggest(s.key());
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> setStep(c.getSource(), StringArgumentType.getString(c, "step")))));
        root.then(CommandManager.literal("party")
                .then(CommandManager.literal("auto").executes(c -> party(c.getSource(), 0)))
                .then(CommandManager.argument("size", IntegerArgumentType.integer(1, 3)).executes(c -> party(c.getSource(), IntegerArgumentType.getInteger(c, "size")))));
        root.then(CommandManager.literal("ability")
                .then(CommandManager.literal("grant").then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("ability", StringArgumentType.word()).executes(c -> ability(c.getSource(), EntityArgumentType.getPlayer(c, "player"), StringArgumentType.getString(c, "ability"), true)))))
                .then(CommandManager.literal("revoke").then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("ability", StringArgumentType.word()).executes(c -> ability(c.getSource(), EntityArgumentType.getPlayer(c, "player"), StringArgumentType.getString(c, "ability"), false))))));
        root.then(CommandManager.literal("teleport")
                .then(CommandManager.argument("structure", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (Structures.Site s : Structures.sites()) {
                                b.suggest(s.placement().id());
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> teleport(c.getSource(), StringArgumentType.getString(c, "structure"), false))
                        .then(CommandManager.literal("view").executes(c -> teleport(c.getSource(), StringArgumentType.getString(c, "structure"), true)))));
        root.then(CommandManager.literal("validate").executes(c -> validate(c.getSource())));
        root.then(CommandManager.literal("selftest").executes(c -> selftest(c.getSource())));
        root.then(CommandManager.literal("probe")
                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                        .then(CommandManager.argument("y1", IntegerArgumentType.integer())
                                .then(CommandManager.argument("y2", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> probe(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y1"),
                                                        IntegerArgumentType.getInteger(c, "y2"), IntegerArgumentType.getInteger(c, "z"))))))));
        // development tools: what a client is sent when it joins, how teleports and chunk watching behave, the server's view of a flight
        root.then(CommandManager.literal("joinreplay")
                .then(CommandManager.literal("start")
                        .then(CommandManager.argument("label", StringArgumentType.word())
                                .then(CommandManager.argument("delay", IntegerArgumentType.integer(-1, 100))
                                        .executes(c -> feedback(c.getSource(), JoinReplay.start(c.getSource().getServer(), StringArgumentType.getString(c, "label"), IntegerArgumentType.getInteger(c, "delay")))))))
                .then(CommandManager.literal("report").executes(c -> joinReport(c.getSource())))
                .then(CommandManager.literal("torture").executes(c -> feedback(c.getSource(), JoinReplay.torture(c.getSource().getServer()))))
                .then(CommandManager.literal("result").executes(c -> feedback(c.getSource(), JoinReplay.tortureResult()))));
        root.then(CommandManager.literal("trace")
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .then(CommandManager.argument("ticks", IntegerArgumentType.integer(1, 2400))
                                .executes(c -> feedback(c.getSource(), ServerTrace.start(c.getSource().getServer(), StringArgumentType.getString(c, "player"), IntegerArgumentType.getInteger(c, "ticks")))))));
        root.then(CommandManager.literal("creatures").executes(c -> creatures(c.getSource())));
        // development tools for the open country: count what the generator made around a point, stand (or hover) somewhere out there
        root.then(CommandManager.literal("survey")
                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                        .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(0, 8))
                                        .executes(c -> {
                                            ServerWorld world = Dimensions.district(c.getSource().getServer());
                                            return feedback(c.getSource(), world == null ? "the district is not loaded"
                                                    : Survey.count(world, IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"),
                                                    IntegerArgumentType.getInteger(c, "radius")));
                                        })))));
        root.then(CommandManager.literal("sweep")
                .then(CommandManager.literal("start")
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 4000))
                                .then(CommandManager.argument("range", IntegerArgumentType.integer(1, 2000))
                                        .executes(c -> {
                                            ServerWorld world = Dimensions.district(c.getSource().getServer());
                                            return feedback(c.getSource(), world == null ? "the district is not loaded"
                                                    : Sweep.start(world, IntegerArgumentType.getInteger(c, "count"), IntegerArgumentType.getInteger(c, "range")));
                                        }))))
                .then(CommandManager.literal("status").executes(c -> {
                    ServerWorld world = Dimensions.district(c.getSource().getServer());
                    return feedback(c.getSource(), world == null ? "the district is not loaded" : Sweep.status(world));
                }))
                .then(CommandManager.literal("end").executes(c -> {
                    ServerWorld world = Dimensions.district(c.getSource().getServer());
                    return feedback(c.getSource(), world == null ? "the district is not loaded" : Sweep.end(world));
                })));
        root.then(CommandManager.literal("wild")
                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                        .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                .executes(c -> wild(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), false))
                                .then(CommandManager.literal("view")
                                        .executes(c -> wild(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"), true))))));
        root.then(CommandManager.literal("checkpoint").executes(c -> checkpoint(c.getSource())));
        root.then(CommandManager.literal("reset")
                .then(CommandManager.literal("encounter")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String n : FlowHost.structures()) {
                                        b.suggest(n);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(c -> resetEncounter(c.getSource(), StringArgumentType.getString(c, "name"), false))
                                .then(CommandManager.literal("full").executes(c -> resetEncounter(c.getSource(), StringArgumentType.getString(c, "name"), true))))));
        dispatcher.register(root);
        // clicked from the chat by the player who is in a dialogue: no permission needed, the dialogue validates the choice
        dispatcher.register(CommandManager.literal("lewandivka_choice")
                .then(CommandManager.argument("choice", StringArgumentType.word()).executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayer();
                    return p != null && Dialogues.choose(p, StringArgumentType.getString(c, "choice")) ? 1 : 0;
                })));
    }

    // ------------------------------------------------------------------ recovery

    private static int checkpoint(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("only a player can use this"));
            return 0;
        }
        String structure = Structures.structureAt(Dimensions.idOf(player.getWorld()), player.getBlockPos());
        if (structure == null) {
            source.sendError(Text.literal("you are not inside a quest structure"));
            return 0;
        }
        int cp = Campaign.world(source.getServer()).encounter(structure).checkpoint();
        String id = structure + ":cp_" + Math.max(cp, 0);
        if (!Structures.has(id)) {
            id = structure + ":spawn";
        }
        if (!Structures.has(id) || !Travel.toMarker(player, id)) {
            source.sendError(Text.literal("no checkpoint marker in " + structure));
            return 0;
        }
        source.sendFeedback(() -> Text.translatable("command.lewandivka.checkpoint.teleported"), false);
        return 1;
    }

    private static int resetEncounter(ServerCommandSource source, String name, boolean full) {
        boolean ok = full ? FlowHost.resetAll(source.getServer(), name) : FlowHost.reset(source.getServer(), name);
        if (!ok) {
            source.sendError(Text.translatable("command.lewandivka.reset.unknown", name));
            return 0;
        }
        source.sendFeedback(() -> Text.translatable("command.lewandivka.reset", name), true);
        return 1;
    }

    // ------------------------------------------------------------------ status and story

    private static int status(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        WorldProgress w = Campaign.world(server);
        StringBuilder sb = new StringBuilder();
        sb.append("stage=").append(w.stage().key()).append(" step=").append(w.step().key())
                .append(" counter=").append(w.stepCounter()).append('/').append(w.step().counterMax)
                .append(" rings=").append(w.ringFragments()).append("/4")
                .append(" portal=").append(w.portalActive())
                .append(" bosses=").append(w.bosses())
                .append(" party=").append(PartyService.override() == 0 ? "auto(" + PartyService.scale(server).size() + ")" : PartyService.override());
        for (PlayerProgress p : Campaign.model(server).allPlayers()) {
            sb.append("\n  ").append(p.lastName()).append(": abilities=")
                    .append(p.abilities().stream().map(Ability::key).collect(Collectors.joining(",")))
                    .append(" rep=").append(p.repPoints()).append(" secrets=").append(p.secrets().size()).append(" cats=").append(p.collectibles().size());
        }
        String text = sb.toString();
        source.sendFeedback(() -> Text.literal(text), false);
        return 1;
    }

    private static int setStage(ServerCommandSource source, String key) {
        CampaignStage stage = CampaignStage.byKey(key);
        if (stage == null) {
            source.sendError(Text.literal("unknown stage " + key));
            return 0;
        }
        QuestStep first = QuestStep.firstOf(stage);
        Campaign.force(source.getServer(), first);
        source.sendFeedback(() -> Text.literal("stage " + stage.key() + " (step " + first.key() + ")"), true);
        return 1;
    }

    private static int setStep(ServerCommandSource source, String key) {
        QuestStep step = QuestStep.byKey(key);
        if (step == null) {
            source.sendError(Text.literal("unknown step " + key));
            return 0;
        }
        Campaign.force(source.getServer(), step);
        source.sendFeedback(() -> Text.literal("step " + step.key()), true);
        return 1;
    }

    private static int party(ServerCommandSource source, int size) {
        PartyService.setOverride(size);
        source.sendFeedback(() -> Text.literal(size == 0 ? "party size: automatic" : "party size fixed to " + size), true);
        return 1;
    }

    private static int ability(ServerCommandSource source, ServerPlayerEntity player, String key, boolean grant) {
        Ability a = Ability.byKey(key.toLowerCase(Locale.ROOT));
        if (a == null) {
            source.sendError(Text.literal("unknown ability " + key));
            return 0;
        }
        PlayerProgress p = Campaign.player(player);
        boolean changed = grant ? p.grant(a) : p.revoke(a);
        Campaign.dirty(source.getServer());
        com.lewandivka.network.Net.sendCampaign(player);                       // the HUD and the glider key read the mask
        source.sendFeedback(() -> Text.literal((grant ? "granted " : "revoked ") + a.key() + (changed ? "" : " (no change)")), true);
        return 1;
    }

    // ------------------------------------------------------------------ development tools

    /** Development tool: to the entrance of a structure, or ({@code view}) to an aerial vantage point looking at it. */
    private static int teleport(ServerCommandSource source, String structure, boolean view) {
        try {
            return teleportUnchecked(source, structure, view);
        } catch (RuntimeException e) {
            // the chat only says "an unexpected error occurred": the log must say which
            com.lewandivka.LewandivkaMod.LOGGER.error("/lewandivka teleport {} failed", structure, e);
            source.sendError(Text.literal("teleport failed: " + e));
            return 0;
        }
    }

    private static int teleportUnchecked(ServerCommandSource source, String structure, boolean view) {
        ServerPlayerEntity player = source.getPlayer();
        Structures.Site site = Structures.site(structure);
        if (player == null || site == null) {
            source.sendError(Text.literal("unknown structure " + structure));
            return 0;
        }
        ServerWorld world = Dimensions.world(source.getServer(), site.dimension());
        if (world == null) {
            source.sendError(Text.literal("dimension " + site.dimension() + " is not loaded"));
            return 0;
        }
        String spot = "entrance";
        Structures.Marker m = Structures.marker(structure + ":" + spot);
        if (m == null) {
            var list = Structures.markersOf(structure);
            m = list.isEmpty() ? null : list.get(0);
        }
        double x = m != null ? m.x() + 0.5 : site.placement().x();
        double y = m != null ? m.y() : site.placement().y();
        double z = m != null ? m.z() + 0.5 : site.placement().z();
        if (view) {
            // above the roof line, south of the structure, looking at its lower middle (never inside a wall)
            net.minecraft.util.math.Box b = Structures.bounds(structure);
            double cx = (b.minX + b.maxX) / 2;
            double cz = (b.minZ + b.maxZ) / 2;
            double cy = b.minY + (b.maxY - b.minY) * 0.35;
            double back = Math.max(16, Math.max(b.getXLength(), b.getZLength()) * 0.9 + 8);
            double eyeY = b.maxY + 6 + back * 0.35;
            // never inside the terrain or a neighbouring building: at least eight blocks above whatever stands at the camera
            int top = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, (int) Math.floor(cx), (int) Math.floor(cz + back));
            eyeY = Math.max(eyeY, top + 8);
            double dy = cy - eyeY;
            float yaw = 180.0f;
            float pitch = (float) Math.toDegrees(Math.atan2(-dy, back));
            player.teleport(world, cx, eyeY - 1.62, cz + back, yaw, pitch);
            if (player.getAbilities().allowFlying) {
                player.getAbilities().flying = true;
                player.sendAbilitiesUpdate();
            }
        } else {
            player.teleport(world, x, y, z, player.getYaw(), player.getPitch());
        }
        source.sendFeedback(() -> Text.literal("teleported to " + structure), true);
        return 1;
    }

    /** The blocks of a column of the world the command runs in ({@code execute in <dimension> run lewandivka probe ...}). */
    private static int probe(ServerCommandSource source, int x, int y1, int y2, int z) {
        String text = SelfTest.probe(source.getWorld(), x, Math.min(y1, y2), Math.max(y1, y2), z);
        source.sendFeedback(() -> Text.literal(text), false);
        return 1;
    }

    private static int wild(ServerCommandSource source, int x, int z, boolean view) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("only a player can use this"));
            return 0;
        }
        return feedback(source, Survey.visit(player, x, z, view));
    }

    /** Development tool: every creature of the mod that lives in any world, with its position (the server's view, not the client's). */
    private static int creatures(ServerCommandSource source) {
        StringBuilder sb = new StringBuilder();
        for (ServerWorld world : source.getServer().getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
                if (id.getNamespace().equals("lewandivka")) {
                    sb.append(Dimensions.idOf(world)).append(' ').append(id.getPath())
                            .append(String.format(Locale.ROOT, " %.0f %.0f %.0f", e.getX(), e.getY(), e.getZ())).append(e.isAlive() ? "" : " dead").append("; ");
                }
            }
        }
        String text = sb.length() == 0 ? "no creatures of the mod" : sb.toString();
        com.lewandivka.LewandivkaMod.LOGGER.info("[creatures] {}", text);
        return feedback(source, text);
    }

    private static int feedback(ServerCommandSource source, String text) {
        String shown = text.length() > 3500 ? text.substring(0, 3500) + " ..." : text;
        source.sendFeedback(() -> Text.literal(shown), false);
        return 1;
    }

    private static int joinReport(ServerCommandSource source) {
        SelfTest.Report report = JoinReplay.report(source.getServer());
        for (String note : report.notes()) {
            com.lewandivka.LewandivkaMod.LOGGER.info("[joinreplay] {}", note);
        }
        for (String problem : report.problems()) {
            com.lewandivka.LewandivkaMod.LOGGER.warn("[joinreplay] {}", problem);
        }
        String text = "joinreplay: " + (report.ok() ? "OK" : "PROBLEMS") + " - " + String.join(" ; ", report.ok() ? report.notes() : report.problems());
        return feedback(source, text) * (report.ok() ? 1 : 0);
    }

    /** Checks only a running server with the real dimensions can do (see {@link SelfTest}). */
    private static int selftest(ServerCommandSource source) {
        SelfTest.Report report = SelfTest.run(source.getServer());
        for (String note : report.notes()) {
            com.lewandivka.LewandivkaMod.LOGGER.info("[selftest] {}", note);
        }
        for (String problem : report.problems()) {
            com.lewandivka.LewandivkaMod.LOGGER.warn("[selftest] {}", problem);
        }
        String text = "selftest: " + (report.ok() ? "OK" : "PROBLEMS") + " - " + String.join(" ; ", report.ok() ? report.notes() : report.problems());
        if (text.length() > 3500) {
            text = text.substring(0, 3500) + " ...";
        }
        String shown = text;
        source.sendFeedback(() -> Text.literal(shown), false);
        return report.ok() ? 1 : 0;
    }

    private static int validate(ServerCommandSource source) {
        MinecraftServer server = source.getServer();
        StructureValidator.Report report = StructureValidator.validate(id -> {
            ServerWorld w = Dimensions.world(server, id);
            return (BlockView) w;
        }, 40);
        String text = "validate: " + (report.ok() ? "OK" : "PROBLEMS") + " - " + report.summary();
        com.lewandivka.LewandivkaMod.LOGGER.info("[validate] {}", text);
        for (String problem : report.problems()) {
            com.lewandivka.LewandivkaMod.LOGGER.warn("[validate] {}", problem);
        }
        source.sendFeedback(() -> Text.literal(text), false);
        return report.ok() ? 1 : 0;
    }
}
