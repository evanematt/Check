package com.lewandivka.quest;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.story.Events;
import com.lewandivka.flow.FlowHost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/** Boss victories: recorded in the campaign (so they survive restarts), rewarded by the story and told to the flows. */
public final class Encounters {

    private Encounters() {
    }

    public static void bossDefeated(ServerWorld world, String bossId) {
        MinecraftServer server = world.getServer();
        Campaign.world(server).defeatBoss(bossId);
        Campaign.dirty(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.translatable("message.lewandivka.boss.defeated"), true);
        }
        FlowHost.bossDefeated(server, bossId);
        Story.event(server, Events.boss(bossId));
    }
}
