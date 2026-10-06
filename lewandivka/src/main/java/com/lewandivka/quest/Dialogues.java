package com.lewandivka.quest;

import com.lewandivka.core.dialogue.DialogueChoice;
import com.lewandivka.core.dialogue.DialogueLine;
import com.lewandivka.core.dialogue.DialogueRunner;
import com.lewandivka.core.dialogue.DialogueScript;
import com.lewandivka.core.text.DialogueBook;
import com.lewandivka.sound.GameSounds;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Plays the short, dry dialogues of the game tick by tick (the server never waits for them). Lines are shown in the
 * chat with the speaker's name; choices are clickable lines that run {@code /lewandivka_choice <id>}, which any player
 * may use (the choice is validated against the running script of that player).
 */
public final class Dialogues {

    private static final class Active {
        final DialogueRunner runner;
        final List<UUID> audience = new ArrayList<>();
        final UUID speaker;
        final BiConsumer<ServerPlayerEntity, String> onChoice;
        boolean offered;

        Active(DialogueRunner runner, UUID speaker, BiConsumer<ServerPlayerEntity, String> onChoice) {
            this.runner = runner;
            this.speaker = speaker;
            this.onChoice = onChoice;
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Active> BY_PLAYER = new HashMap<>();

    private Dialogues() {
    }

    public static void play(MinecraftServer server, String scriptId, Collection<ServerPlayerEntity> audience, Entity speaker) {
        play(server, scriptId, audience, speaker, null);
    }

    public static synchronized void play(MinecraftServer server, String scriptId, Collection<ServerPlayerEntity> audience, Entity speaker,
                                         BiConsumer<ServerPlayerEntity, String> onChoice) {
        DialogueScript script = DialogueBook.get(scriptId);
        if (script == null) {
            com.lewandivka.LewandivkaMod.LOGGER.warn("Unknown dialogue script {}", scriptId);
            return;
        }
        Active a = new Active(new DialogueRunner(script, server.getTicks()), speaker == null ? null : speaker.getUuid(), onChoice);
        for (ServerPlayerEntity p : audience) {
            a.audience.add(p.getUuid());
            BY_PLAYER.put(p.getUuid(), a);
        }
        ACTIVE.add(a);
    }

    public static synchronized void tick(MinecraftServer server) {
        long now = server.getTicks();
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            for (DialogueLine line : a.runner.due(now)) {
                for (UUID id : a.audience) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                    if (p != null) {
                        p.sendMessage(lineText(line), false);
                        p.getServerWorld().playSound(null, p.getBlockPos(), GameSounds.get("npc.blip"), SoundCategory.NEUTRAL, 0.5f, 1.0f);
                    }
                }
            }
            if (a.runner.shouldOfferChoices() && !a.offered) {
                a.offered = true;
                for (UUID id : a.audience) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                    if (p != null) {
                        for (DialogueChoice c : a.runner.script().choices()) {
                            MutableText t = Text.literal("  > ").formatted(Formatting.GOLD)
                                    .append(Text.translatable("dialogue.lewandivka." + a.runner.script().id() + ".choice." + c.id()).formatted(Formatting.YELLOW));
                            t.styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lewandivka_choice " + c.id())));
                            p.sendMessage(t, false);
                        }
                    }
                }
            }
            if (a.runner.finished() || (a.offered && !a.runner.awaitingChoice())) {
                it.remove();
                for (UUID id : a.audience) {
                    BY_PLAYER.remove(id, a);
                }
            }
        }
    }

    /** A player clicked a choice. */
    public static synchronized boolean choose(ServerPlayerEntity player, String choiceId) {
        Active a = BY_PLAYER.get(player.getUuid());
        if (a == null || !a.runner.awaitingChoice()) {
            return false;
        }
        DialogueChoice c = a.runner.choose(choiceId);
        if (c == null) {
            return false;
        }
        if (a.onChoice != null) {
            a.onChoice.accept(player, c.id());
        }
        return true;
    }

    private static Text lineText(DialogueLine line) {
        MutableText text = Text.empty();
        if (!line.speaker().isEmpty()) {
            text.append(Text.literal("[").formatted(Formatting.DARK_GRAY))
                    .append(Text.translatable(line.speakerKey()).formatted(Formatting.GOLD))
                    .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
        }
        return text.append(Text.translatable(line.textKey()).formatted(Formatting.WHITE));
    }
}
