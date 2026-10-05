package ua.lewandivka.logic;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import ua.lewandivka.ability.Abilities;
import ua.lewandivka.campaign.Campaign;
import ua.lewandivka.campaign.Stage;
import ua.lewandivka.registry.ModWorldgen;
import ua.lewandivka.world.Sites;

import java.util.Arrays;
import java.util.UUID;

/**
 * Команди. Для всіх: status, compass. Для операторів (рівень 2): setstage, checkpoint, reset encounter,
 * ability grant/revoke, party, go, home, reset.
 */
public final class ModCommands {
    private static final String[] ENCOUNTERS = {"garage", "aqua", "depot", "shelter", "tower"};

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("lewandivka")
                        .then(CommandManager.literal("status").executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            LewState st = LewState.get(server);
                            StringBuilder sb = new StringBuilder("Етап: " + Campaign.stage(st) + " (" + Campaign.stage(st).title + ")");
                            sb.append("\nГрупа: ").append(Campaign.partySize(server)).append(Campaign.partyOverride > 0 ? " (перевизначено)" : "");
                            sb.append("\nХромандівка: ").append(st.arrived ? "відкрита" : "ще ні");
                            sb.append("\nВерсія даних: ").append(LewState.DATA_VERSION);
                            ServerPlayerEntity p = ctx.getSource().getPlayer();
                            if (p != null) {
                                sb.append("\nЗдібності: ");
                                for (String a : new String[]{Abilities.DASH, Abilities.JUMP, Abilities.GLIDE}) {
                                    if (st.hasAbility(p.getUuid(), a)) {
                                        sb.append(a).append(' ');
                                    }
                                }
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("compass").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            ServerWorld chroma = p.getServer().getWorld(ModWorldgen.CHROMA);
                            if (chroma != null) {
                                Travel.give(p, Sites.compassTo(chroma, Travel.nextObjective(LewState.get(p.getServer()))));
                            }
                            return 1;
                        }))
                        .then(CommandManager.literal("setstage").requires(ModCommands::op)
                                .then(CommandManager.argument("stage", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            Arrays.stream(Stage.values()).forEach(s -> b.suggest(s.name()));
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "stage");
                                            Stage target;
                                            try {
                                                target = Stage.valueOf(name.toUpperCase());
                                            } catch (IllegalArgumentException e) {
                                                ctx.getSource().sendError(Text.literal("Невідомий етап: " + name));
                                                return 0;
                                            }
                                            Campaign.setStage(ctx.getSource().getServer(), target);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Етап виставлено: " + target), true);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("checkpoint").requires(ModCommands::op).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            LewState st = LewState.get(p.getServer());
                            if (st.arrived) {
                                Travel.enterChroma(p);
                            } else if (st.point("city", "spawn") != null) {
                                var sp = st.point("city", "spawn");
                                p.teleport(p.getServer().getOverworld(), sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0, 0);
                            }
                            return 1;
                        }))
                        .then(CommandManager.literal("reset").requires(ModCommands::op)
                                .then(CommandManager.literal("encounter")
                                        .then(CommandManager.argument("name", StringArgumentType.word())
                                                .suggests((c, b) -> {
                                                    Arrays.stream(ENCOUNTERS).forEach(b::suggest);
                                                    return b.buildFuture();
                                                })
                                                .executes(ctx -> resetEncounter(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                                .then(CommandManager.literal("all").executes(ctx -> {
                                    LewState.get(ctx.getSource().getServer()).clearAll();
                                    ctx.getSource().sendFeedback(() -> Text.literal("Прогрес Левандівки скинуто (збудовані локації лишаються в світі)."), true);
                                    return 1;
                                })))
                        .then(CommandManager.literal("ability").requires(ModCommands::op)
                                .then(CommandManager.argument("mode", StringArgumentType.word())
                                        .suggests((c, b) -> b.suggest("grant").suggest("revoke").buildFuture())
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .then(CommandManager.argument("ability", StringArgumentType.word())
                                                        .suggests((c, b) -> b.suggest(Abilities.DASH).suggest(Abilities.JUMP).suggest(Abilities.GLIDE).buildFuture())
                                                        .executes(ctx -> {
                                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                            LewState st = LewState.get(ctx.getSource().getServer());
                                                            String a = StringArgumentType.getString(ctx, "ability");
                                                            if (StringArgumentType.getString(ctx, "mode").equals("grant")) {
                                                                st.grantAbility(target.getUuid(), a);
                                                                Abilities.announce(target, a);
                                                            } else {
                                                                st.revokeAbility(target.getUuid(), a);
                                                            }
                                                            return 1;
                                                        })))))
                        .then(CommandManager.literal("party").requires(ModCommands::op)
                                .then(CommandManager.argument("size", IntegerArgumentType.integer(0, 3)).executes(ctx -> {
                                    Campaign.partyOverride = IntegerArgumentType.getInteger(ctx, "size");
                                    ctx.getSource().sendFeedback(() -> Text.literal("Розмір групи: "
                                            + (Campaign.partyOverride == 0 ? "за онлайном" : Campaign.partyOverride)), true);
                                    return 1;
                                })))
                        .then(CommandManager.literal("go").requires(ModCommands::op).executes(ctx -> {
                            Travel.enterChroma(ctx.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(CommandManager.literal("home").requires(ModCommands::op).executes(ctx -> {
                            Travel.sendHome(ctx.getSource().getPlayerOrThrow());
                            return 1;
                        }))));
    }

    private static int resetEncounter(ServerCommandSource src, String name) {
        MinecraftServer server = src.getServer();
        LewState st = LewState.get(server);
        if (!Arrays.asList(ENCOUNTERS).contains(name)) {
            src.sendError(Text.literal("Невідома зустріч: " + name));
            return 0;
        }
        UUID id = st.boss(name);
        if (id != null) {
            for (ServerWorld w : server.getWorlds()) {
                Entity e = w.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
            st.setBoss(name, null);
        }
        for (var pos : st.nodes(name)) {
            for (ServerWorld w : server.getWorlds()) {
                if (w.isChunkLoaded(pos)) {
                    var bs = w.getBlockState(pos);
                    if (bs.getBlock() instanceof ua.lewandivka.block.ShieldNodeBlock && bs.get(ua.lewandivka.block.ShieldNodeBlock.ACTIVE)) {
                        w.setBlockState(pos, bs.with(ua.lewandivka.block.ShieldNodeBlock.ACTIVE, false));
                    }
                }
            }
        }
        src.sendFeedback(() -> Text.literal("Зустріч «" + name + "» скинуто. Вівтар можна клацнути знову."), true);
        return 1;
    }

    private static boolean op(ServerCommandSource source) {
        return source.hasPermissionLevel(2);
    }

    private ModCommands() {
    }
}
