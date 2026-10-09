package com.lewandivka.core.dialogue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable dialogue: timed lines followed by optional choices. Scripts are plain data; the server
 * schedules them tick by tick through a {@link DialogueRunner}, so a dialogue never pauses the server.
 */
public final class DialogueScript {

    private final String id;
    private final List<DialogueLine> lines;
    private final List<DialogueChoice> choices;

    private DialogueScript(String id, List<DialogueLine> lines, List<DialogueChoice> choices) {
        this.id = id;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
    }

    public String id() {
        return id;
    }

    public List<DialogueLine> lines() {
        return lines;
    }

    public List<DialogueChoice> choices() {
        return choices;
    }

    public boolean hasChoices() {
        return !choices.isEmpty();
    }

    public DialogueChoice choice(String choiceId) {
        for (DialogueChoice c : choices) {
            if (c.id().equals(choiceId)) {
                return c;
            }
        }
        return null;
    }

    /** Total duration until the last line has been on screen for its hold time. */
    public int totalTicks() {
        int t = 0;
        int end = 0;
        for (DialogueLine l : lines) {
            t += l.delayTicks();
            end = Math.max(end, t + l.holdTicks());
        }
        return end;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private final List<DialogueLine> lines = new ArrayList<>();
        private final List<DialogueChoice> choices = new ArrayList<>();
        private int counter;

        private Builder(String id) {
            this.id = id;
        }

        /** Adds a line whose key is {@code dialogue.lewandivka.<script id>.<n>}, starting at 1. */
        public Builder say(String speaker, int delayTicks) {
            counter++;
            return say(speaker, "dialogue.lewandivka." + id + "." + counter, delayTicks, DialogueLine.DEFAULT_HOLD_TICKS);
        }

        public Builder say(String speaker, String textKey, int delayTicks, int holdTicks) {
            lines.add(new DialogueLine(speaker, textKey, delayTicks, holdTicks));
            return this;
        }

        /** Adds a choice button with key {@code dialogue.lewandivka.<script id>.choice.<choiceId>}. */
        public Builder choice(String choiceId) {
            choices.add(new DialogueChoice(choiceId, "dialogue.lewandivka." + id + ".choice." + choiceId));
            return this;
        }

        public DialogueScript build() {
            return new DialogueScript(id, lines, choices);
        }
    }
}
