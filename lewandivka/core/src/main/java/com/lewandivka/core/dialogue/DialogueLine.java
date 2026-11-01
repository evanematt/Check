package com.lewandivka.core.dialogue;

/**
 * One timed line of a dialogue.
 *
 * @param speaker    speaker id, translated through {@code speaker.lewandivka.<id>}; empty for narration
 * @param textKey    language key of the (short, deadpan) line
 * @param delayTicks pause before this line appears, measured from the previous line's appearance
 * @param holdTicks  how long the line stays on screen
 */
public record DialogueLine(String speaker, String textKey, int delayTicks, int holdTicks) {

    public static final int DEFAULT_HOLD_TICKS = 70;

    public DialogueLine {
        delayTicks = Math.max(0, delayTicks);
        holdTicks = Math.max(20, holdTicks);
    }

    public String speakerKey() {
        return "speaker.lewandivka." + speaker;
    }
}
