package ua.lewandivka.logic;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import ua.lewandivka.registry.ModWorldgen;
import ua.lewandivka.world.Sites;

/** /lewandivka status | compass — для всіх; go | home | reset — для операторів (тест і порятунок). */
public final class ModCommands {
    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("lewandivka")
                        .then(CommandManager.literal("status").executes(ctx -> {
                            LewState st = LewState.get(ctx.getSource().getServer());
                            String[][] steps = {{"garage", "Веселковий гараж"}, {"shelter", "Притулок / коти"}, {"aqua", "Аквапарк"},
                                    {"depot", "Трамвайне депо"}, {"tower", "Голова району"}};
                            StringBuilder sb = new StringBuilder("Хромандівка: " + (st.arrived ? "відкрита" : "ще не відкрита"));
                            for (String[] s : steps) {
                                sb.append("\n ").append(st.has("done_" + s[0]) ? "✔ " : "✘ ").append(s[1]);
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
                        .then(CommandManager.literal("go").requires(ModCommands::op).executes(ctx -> {
                            Travel.enterChroma(ctx.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(CommandManager.literal("home").requires(ModCommands::op).executes(ctx -> {
                            Travel.sendHome(ctx.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(CommandManager.literal("reset").requires(ModCommands::op).executes(ctx -> {
                            LewState.get(ctx.getSource().getServer()).clearAll();
                            ctx.getSource().sendFeedback(() -> Text.literal("Прогрес Левандівки скинуто (збудовані локації лишаються в світі)."), true);
                            return 1;
                        }))));
    }

    private static boolean op(ServerCommandSource source) {
        return source.hasPermissionLevel(2);
    }

    private ModCommands() {
    }
}
