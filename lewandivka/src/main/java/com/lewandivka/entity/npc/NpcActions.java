package com.lewandivka.entity.npc;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;

/** What happens when a player talks to a story npc; the quest services register a handler per npc id. */
public final class NpcActions {

    @FunctionalInterface
    public interface Interact {
        ActionResult interact(NpcEntity npc, ServerPlayerEntity player, Hand hand);
    }

    private static final Map<String, Interact> HANDLERS = new HashMap<>();

    private NpcActions() {
    }

    public static void on(String npcId, Interact handler) {
        HANDLERS.put(npcId, handler);
    }

    static ActionResult interact(NpcEntity npc, ServerPlayerEntity player, Hand hand) {
        Interact h = HANDLERS.get(npc.spec().id);
        return h == null ? ActionResult.PASS : h.interact(npc, player, hand);
    }
}
