package ua.lewandivka.logic;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ua.lewandivka.registry.ModItems;

import java.util.List;
import java.util.function.Predicate;

/**
 * Захист квестових предметів: якщо предмет «побачений» (got_*), ще не зданий (delivered)
 * і його немає в жодного онлайн-гравця — він повертається. Перевірка обмежена: раз на 30 с + при респавні/вході.
 */
public final class QuestItems {
    private record Rule(String got, Item item, Predicate<LewState> delivered, String name) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("got_kettle", ModItems.MAGIC_KETTLE, st -> st.has("lvl3"), "Магічний чайник"),
            new Rule("got_parcel", ModItems.GARAGE_PARCEL, st -> st.has("lvl4"), "Посилка"),
            new Rule("got_compostor", ModItems.TRAM_COMPOSTOR, st -> st.has("chroma_started") || st.arrived, "Компостер"));

    private static long lastRestore = -10000;

    public static void ensure(MinecraftServer server) {
        LewState st = LewState.get(server);
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) {
            return;
        }
        long now = server.getOverworld().getTime();
        if (now - lastRestore < 600) {
            return;
        }
        for (Rule r : RULES) {
            if (!st.has(r.got()) || r.delivered().test(st)) {
                continue;
            }
            boolean found = false;
            for (ServerPlayerEntity p : players) {
                if (p.getInventory().contains(new ItemStack(r.item())) || p.getInventory().count(r.item()) > 0) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                ServerPlayerEntity first = players.get(0);
                Travel.give(first, new ItemStack(r.item()));
                first.sendMessage(Text.literal("Квестовий предмет повернувся: " + r.name()).formatted(Formatting.YELLOW), false);
                lastRestore = now;
            }
        }
    }

    private QuestItems() {
    }
}
