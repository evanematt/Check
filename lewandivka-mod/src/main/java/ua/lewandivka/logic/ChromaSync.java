package ua.lewandivka.logic;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ua.lewandivka.registry.ModItems;

import java.util.Map;
import java.util.UUID;

/** Таймер синхронізації Хроми: 5 хвилин, боссбар «ХРОМА», при провалі таблетки повертаються. */
public final class ChromaSync {
    private static ServerBossBar bar;

    static void update(MinecraftServer server, int ready, int need) {
        LewState st = LewState.get(server);
        st.set("chroma_started");
        if (bar == null) {
            bar = new ServerBossBar(Text.literal("ХРОМА"), BossBar.Color.PINK, BossBar.Style.NOTCHED_10);
        }
        bar.setName(Text.literal("ХРОМА  " + ready + "/" + need).formatted(Formatting.LIGHT_PURPLE));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            bar.addPlayer(p);
        }
    }

    static void finish() {
        if (bar != null) {
            bar.clearPlayers();
            bar = null;
        }
    }

    static void tick(MinecraftServer server) {
        if (bar == null) {
            return;
        }
        LewState st = LewState.get(server);
        long now = server.getOverworld().getTime();
        long first = Long.MAX_VALUE;
        for (long t : st.pillEaters.values()) {
            first = Math.min(first, t);
        }
        if (first == Long.MAX_VALUE) {
            finish();
            return;
        }
        long left = Travel.PILL_WINDOW - (now - first);
        if (left > 0) {
            bar.setPercent(left / (float) Travel.PILL_WINDOW);
            return;
        }
        // Час вийшов: безпечне скидання, таблетки повертаються — квестовий предмет не згорає даремно.
        for (Map.Entry<UUID, Long> e : st.pillEaters.entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p != null) {
                Travel.give(p, new ItemStack(ModItems.CHROMA_PILL));
                p.sendMessage(Text.literal("Хрома розвіялась: не всі встигли. Таблетку повернено — спробуйте ще раз.").formatted(Formatting.GRAY), false);
            }
        }
        st.pillEaters.clear();
        st.unset("chroma_started");
        finish();
    }

    private ChromaSync() {
    }
}
