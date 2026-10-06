package com.lewandivka.quest;

import com.lewandivka.campaign.Campaign;
import com.lewandivka.campaign.PartyService;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;
import com.lewandivka.core.sync.ChromaSync;
import com.lewandivka.sound.GameSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Chroma tablet: the first player to swallow it starts five minutes in which everyone who takes part has to do the
 * same. Tablets are only removed when the whole party made it, so a failed attempt never costs the quest item.
 */
public final class ChromaService {

    private static final ChromaSync SYNC = new ChromaSync();
    private static ServerBossBar bar;
    private static long lastTick;

    private ChromaService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ChromaService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> reset());
    }

    public static boolean running() {
        return SYNC.running();
    }

    private static List<UUID> online(MinecraftServer server) {
        List<UUID> out = new ArrayList<>();
        for (ServerPlayerEntity p : PartyService.players(server)) {
            if (Campaign.player(p).participating()) {
                out.add(p.getUuid());
            }
        }
        return out;
    }

    public static TypedActionResult<ItemStack> use(ServerPlayerEntity player, Hand hand) {
        MinecraftServer server = player.getServer();
        ItemStack stack = player.getStackInHand(hand);
        if (Campaign.step(server) != QuestStep.CHROMA_TAKE) {
            player.sendMessage(Text.translatable("message.lewandivka.wrong_stage"), true);
            return TypedActionResult.fail(stack);
        }
        ChromaSync.Event e = SYNC.consume(player.getUuid(), online(server), server.getTicks());
        if (e == ChromaSync.Event.NONE) {
            player.sendMessage(Text.translatable("message.lewandivka.chroma.need_all"), true);
            return TypedActionResult.fail(stack);
        }
        player.getServerWorld().playSound(null, player.getBlockPos(), GameSounds.get("chroma.consume"), SoundCategory.PLAYERS, 1.0f, 1.0f);
        if (e == ChromaSync.Event.STARTED) {
            openBar(server);
            say(server, Text.translatable("message.lewandivka.chroma.start"));
        }
        say(server, Text.translatable("message.lewandivka.chroma.consumed", player.getName()));
        if (e == ChromaSync.Event.COMPLETED) {
            complete(server);
        } else {
            refreshBar(server);
        }
        return TypedActionResult.success(stack);
    }

    private static void tick(MinecraftServer server) {
        if (!SYNC.running()) {
            return;
        }
        long now = server.getTicks();
        ChromaSync.Event e = SYNC.tick(online(server), now);
        switch (e) {
            case COMPLETED -> complete(server);
            case EXPIRED, ABORTED -> {
                closeBar();
                say(server, Text.translatable("message.lewandivka.chroma.expired"));
                for (ServerPlayerEntity p : PartyService.players(server)) {
                    p.getServerWorld().playSound(null, p.getBlockPos(), GameSounds.get("chroma.fail"), SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
            }
            default -> {
                if (now - lastTick >= 10) {
                    lastTick = now;
                    refreshBar(server);
                    long left = SYNC.remainingTicks(now);
                    if (left < 200 && left % 20 < 10) {
                        for (ServerPlayerEntity p : PartyService.players(server)) {
                            p.getServerWorld().playSound(null, p.getBlockPos(), GameSounds.get("chroma.tick"), SoundCategory.PLAYERS, 0.5f, 1.0f);
                        }
                    }
                }
            }
        }
    }

    private static void complete(MinecraftServer server) {
        closeBar();
        for (UUID id : SYNC.participants()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) {
                QuestInventory.take(p, QuestItems.TABLET, 1);
            }
        }
        SYNC.reset();
        Story.event(server, Events.CHROMA_SYNCED);
    }

    private static void openBar(MinecraftServer server) {
        closeBar();
        bar = new ServerBossBar(Text.translatable("bossbar.lewandivka.chroma"), BossBar.Color.PINK, BossBar.Style.NOTCHED_20);
        bar.setPercent(1.0f);
        refreshBar(server);
    }

    private static void refreshBar(MinecraftServer server) {
        if (bar == null) {
            return;
        }
        bar.setPercent(SYNC.fraction(server.getTicks()));
        for (ServerPlayerEntity p : PartyService.players(server)) {
            bar.addPlayer(p);
        }
    }

    private static void closeBar() {
        if (bar != null) {
            bar.clearPlayers();
            bar.setVisible(false);
            bar = null;
        }
    }

    private static void reset() {
        closeBar();
        SYNC.reset();
    }

    private static void say(MinecraftServer server, Text text) {
        for (ServerPlayerEntity p : PartyService.players(server)) {
            p.sendMessage(text, false);
        }
    }
}
