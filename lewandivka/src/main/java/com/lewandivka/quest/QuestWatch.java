package com.lewandivka.quest;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.campaign.PartyService;
import com.lewandivka.core.quest.QuestItemLedger;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.item.SpecItem;
import com.lewandivka.network.Net;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The safety net of the story: every two seconds the quest-item ledger says what the party must hold at the current
 * step and whatever went missing (death, lava, {@code /clear}, a disconnect) is handed out again.
 */
public final class QuestWatch {

    private static final UUID PACKAGE_SLOW = UUID.fromString("5d2a8a52-3d4f-4b7a-9a3b-2d1c6b0f7e10");

    private QuestWatch() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(QuestWatch::tick);
    }

    private static void tick(MinecraftServer server) {
        long t = server.getTicks();
        if (t % 40 == 7) {
            restore(server);
        }
        if (t % 10 == 3) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                carrierSlowness(p);
            }
        }
    }

    /** Whoever carries the package is 15 % slower (it is heavy and it growls); dropping it removes the penalty. */
    private static void carrierSlowness(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean carrying = QuestInventory.has(player, QuestItems.PACKAGE);
        boolean slowed = speed.getModifier(PACKAGE_SLOW) != null;
        if (carrying && !slowed) {
            speed.addTemporaryModifier(new EntityAttributeModifier(PACKAGE_SLOW, "lewandivka.package", -0.15, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!carrying && slowed) {
            speed.removeModifier(PACKAGE_SLOW);
        }
    }

    private static void restore(MinecraftServer server) {
        if (!Campaign.world(server).started() || ChromaService.running()) {
            return;
        }
        Map<UUID, Map<String, Integer>> inventories = new LinkedHashMap<>();
        for (ServerPlayerEntity p : PartyService.players(server)) {
            if (!Campaign.player(p).participating()) {
                continue;
            }
            Map<String, Integer> counts = QuestInventory.counts(p);
            // an item lying on the ground next to the player is not lost yet
            for (ItemEntity drop : p.getServerWorld().getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(6.0), e -> e.getStack().getItem() instanceof SpecItem)) {
                counts.merge(((SpecItem) drop.getStack().getItem()).spec.id, drop.getStack().getCount(), Integer::sum);
            }
            inventories.put(p.getUuid(), counts);
        }
        List<QuestItemLedger.Grant> grants = QuestItemLedger.missing(QuestItemLedger.needs(Campaign.world(server)), inventories);
        for (QuestItemLedger.Grant g : grants) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(g.player());
            if (p == null) {
                continue;
            }
            QuestInventory.give(p, g.item(), g.count());
            p.sendMessage(Text.translatable("message.lewandivka.restored", QuestInventory.item(g.item()).getName()), true);
        }
        if (!grants.isEmpty()) {
            for (ServerPlayerEntity p : PartyService.players(server)) {
                Net.sendNotebook(p);
            }
        }
    }
}
