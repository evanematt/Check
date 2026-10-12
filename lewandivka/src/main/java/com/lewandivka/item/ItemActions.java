package com.lewandivka.item;

import com.lewandivka.core.registry.ItemSpec.Use;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

import java.util.EnumMap;
import java.util.Map;

/** Server-side behaviour of the mod items; the services register the handlers of the items they own. */
public final class ItemActions {

    @FunctionalInterface
    public interface UseHandler {
        /** @return the result, or null to fall back to the vanilla behaviour of the item */
        TypedActionResult<ItemStack> use(ServerPlayerEntity player, Hand hand, SpecItem item);
    }

    @FunctionalInterface
    public interface UseOnBlockHandler {
        /** @return the result, or null to fall back to the vanilla behaviour of the item */
        ActionResult useOnBlock(ServerPlayerEntity player, ItemUsageContext context, SpecItem item);
    }

    private static final Map<Use, UseHandler> USE = new EnumMap<>(Use.class);
    private static final Map<Use, UseOnBlockHandler> ON_BLOCK = new EnumMap<>(Use.class);

    private ItemActions() {
    }

    public static void onUse(Use use, UseHandler handler) {
        USE.put(use, handler);
    }

    public static void onUseOnBlock(Use use, UseOnBlockHandler handler) {
        ON_BLOCK.put(use, handler);
    }

    static TypedActionResult<ItemStack> use(ServerPlayerEntity player, Hand hand, SpecItem item) {
        UseHandler h = USE.get(item.spec.use);
        return h == null ? null : h.use(player, hand, item);
    }

    static ActionResult useOnBlock(ServerPlayerEntity player, ItemUsageContext context, SpecItem item) {
        UseOnBlockHandler h = ON_BLOCK.get(item.spec.use);
        return h == null ? null : h.useOnBlock(player, context, item);
    }
}
