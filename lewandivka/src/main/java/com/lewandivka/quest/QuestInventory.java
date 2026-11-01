package com.lewandivka.quest;

import com.lewandivka.item.GameItems;
import com.lewandivka.item.SpecItem;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Counting, giving and taking items by catalog id ({@code semky}) or vanilla id ({@code minecraft:oak_planks}). */
public final class QuestInventory {

    private QuestInventory() {
    }

    public static Item item(String id) {
        if (id.indexOf(':') >= 0) {
            return Registries.ITEM.get(new Identifier(id));
        }
        return GameItems.get(id);
    }

    private static boolean matches(ItemStack stack, String id) {
        if (stack.isEmpty()) {
            return false;
        }
        if (id.indexOf(':') >= 0) {
            return Registries.ITEM.getId(stack.getItem()).toString().equals(id);
        }
        return stack.getItem() instanceof SpecItem s && s.spec.id.equals(id);
    }

    public static int count(ServerPlayerEntity p, String id) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (matches(st, id)) {
                n += st.getCount();
            }
        }
        return n;
    }

    public static boolean has(ServerPlayerEntity p, String id) {
        return count(p, id) > 0;
    }

    /** Removes the items; nothing is taken when the player has fewer. */
    public static boolean take(ServerPlayerEntity p, String id, int n) {
        if (count(p, id) < n) {
            return false;
        }
        int left = n;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack st = inv.getStack(i);
            if (matches(st, id)) {
                int take = Math.min(left, st.getCount());
                st.decrement(take);
                left -= take;
            }
        }
        return true;
    }

    /** Gives items; whatever does not fit lands at the player's feet (and is protected by the quest-item ledger). */
    public static void give(ServerPlayerEntity p, String id, int n) {
        Item item = item(id);
        int left = n;
        while (left > 0) {
            int c = Math.min(left, item.getMaxCount());
            ItemStack st = new ItemStack(item, c);
            if (!p.getInventory().insertStack(st)) {
                ItemEntity drop = new ItemEntity(p.getWorld(), p.getX(), p.getY() + 0.5, p.getZ(), st);
                drop.setPickupDelay(0);
                p.getWorld().spawnEntity(drop);
            }
            left -= c;
        }
    }

    public static Map<String, Integer> counts(ServerPlayerEntity p) {
        Map<String, Integer> out = new HashMap<>();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.getItem() instanceof SpecItem s) {
                out.merge(s.spec.id, st.getCount(), Integer::sum);
            }
        }
        return out;
    }
}
