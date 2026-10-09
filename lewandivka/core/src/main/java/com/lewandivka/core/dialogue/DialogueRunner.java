package com.lewandivka.core.dialogue;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime cursor over a {@link DialogueScript}. Poll it every server tick; it returns the lines that
 * became due. It owns no threads and no blocking waits.
 */
public final class DialogueRunner {

    private final DialogueScript script;
    private final long startTick;
    private int next;
    private long nextDue;
    private boolean choiceOffered;
    private boolean closed;

    public DialogueRunner(DialogueScript script, long startTick) {
        this.script = script;
        this.startTick = startTick;
        this.nextDue = script.lines().isEmpty() ? startTick : startTick + script.lines().get(0).delayTicks();
    }

    public DialogueScript script() {
        return script;
    }

    public long startTick() {
        return startTick;
    }

    /** Lines whose time has come at {@code now}, in order. */
    public List<DialogueLine> due(long now) {
        List<DialogueLine> out = new ArrayList<>(1);
        while (!closed && next < script.lines().size() && now >= nextDue) {
            DialogueLine line = script.lines().get(next);
            out.add(line);
            next++;
            if (next < script.lines().size()) {
                nextDue += script.lines().get(next).delayTicks();
            }
        }
        return out;
    }

    /** True once every line has been emitted. */
    public boolean linesFinished() {
        return next >= script.lines().size();
    }

    /**
     * True exactly once, when all lines have been emitted and the script has choices that still have
     * to be shown to the player.
     */
    public boolean shouldOfferChoices() {
        if (!choiceOffered && linesFinished() && script.hasChoices() && !closed) {
            choiceOffered = true;
            return true;
        }
        return false;
    }

    public boolean awaitingChoice() {
        return choiceOffered && !closed;
    }

    /**
     * Validates a client-supplied choice id. Returns the choice, or null when the id is not offered
     * or the script is not currently waiting for an answer.
     */
    public DialogueChoice choose(String choiceId) {
        if (!awaitingChoice()) {
            return null;
        }
        DialogueChoice c = script.choice(choiceId);
        if (c != null) {
            closed = true;
        }
        return c;
    }

    /** Done when every line was shown and no answer is pending. */
    public boolean finished() {
        return closed || (linesFinished() && !script.hasChoices());
    }

    public void close() {
        closed = true;
    }
}
