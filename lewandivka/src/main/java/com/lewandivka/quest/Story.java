package com.lewandivka.quest;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.PlayerProgress;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.story.CampaignDirector;
import com.lewandivka.network.Net;
import com.lewandivka.sound.GameSounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;

/**
 * Entry point of every story event. The pure {@link CampaignDirector} decides what an event means; this class carries
 * out the effects in the game and keeps the clients informed.
 */
public final class Story {

    private Story() {
    }

    public static boolean event(MinecraftServer server, String id) {
        boolean changed = new CampaignDirector(Campaign.world(server), new Effects(server)).event(id);
        if (changed) {
            Campaign.dirty(server);
            Net.broadcastCampaign(server);
        }
        return changed;
    }

    /** Abilities of already defeated bosses for a player who joins late. */
    public static void catchUp(ServerPlayerEntity player) {
        PlayerProgress p = Campaign.player(player);
        for (Ability a : Ability.values()) {
            if (Campaign.world(player.getServer()).bossDefeated(a.unlockBoss) && p.grant(a)) {
                player.sendMessage(Text.translatable("ability.lewandivka.unlocked", Text.translatable("ability.lewandivka." + a.key())), false);
            }
        }
    }

    private record Effects(MinecraftServer server) implements CampaignDirector.Effects {

        @Override
        public void entered(QuestStep step) {
            Campaign.announce(server, step, false);
        }

        @Override
        public void counter(int value, int max) {
            Text t = Text.translatable("message.lewandivka.quest_updated").append(Text.literal(" " + value + "/" + max));
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(t, true);
                p.getServerWorld().playSound(null, p.getBlockPos(), GameSounds.get("ui.quest_update"), SoundCategory.PLAYERS, 0.8f, 1.2f);
            }
        }

        @Override
        public void giveEveryone(String item, int count) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (Campaign.player(p).participating()) {
                    QuestInventory.give(p, item, count);
                }
            }
        }

        @Override
        public void unlock(Ability ability) {
            for (PlayerProgress p : Campaign.model(server).allPlayers()) {
                p.grant(ability);
            }
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(Text.translatable("ability.lewandivka.unlocked", Text.translatable("ability.lewandivka." + ability.key())), false);
                p.getServerWorld().playSound(null, p.getBlockPos(), GameSounds.get("ability.unlock"), SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        }

        @Override
        public void ringFragment() {
            Campaign.world(server).restoreRingFragment();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(Text.translatable("message.lewandivka.ring.fragment", Campaign.world(server).ringFragments(), 4), false);
            }
        }

        @Override
        public void dialogue(String scriptId) {
            Dialogues.play(server, scriptId, server.getPlayerManager().getPlayerList(), null);
        }

        @Override
        public void spawnNpc(String npc) {
            NpcSpawns.spawn(server, npc);
        }

        @Override
        public void portal(boolean active) {
            Campaign.world(server).setPortalActive(active);
            Portals.apply(server);
        }

        @Override
        public void unlockStructure(String structure) {
            Campaign.world(server).unlockStructure(structure);
        }

        @Override
        public void cinematic(String id) {
            Cinematics.play(server, id);
        }

        @Override
        public void toast(String langKey) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(Text.translatable(langKey), true);
            }
        }
    }
}
