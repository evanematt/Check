package com.lewandivka.core.puzzle;

import java.util.EnumSet;
import java.util.List;

/**
 * Rainbow Garage, Paint Workshop: three primary taps are toggled into the mixer, a release lever
 * pours the mixture into the pipe that matches a target colour. Wrong mixtures give grey sludge and
 * simply drain (recoverable, no damage).
 */
public final class PaintPuzzle {

    public enum Paint {
        RED, YELLOW, BLUE
    }

    public enum Mix {
        NONE, RED, YELLOW, BLUE, ORANGE, GREEN, PURPLE, SLUDGE
    }

    public enum Result {
        /** Not enough in the mixer to release anything. */
        EMPTY,
        /** Correct colour poured; the next target is shown (or the puzzle is done). */
        CORRECT,
        /** Wrong colour: sludge, mixer drained. */
        WRONG,
        /** Everything was already solved. */
        ALREADY_DONE
    }

    private final List<Mix> targets;
    private final EnumSet<Paint> mixer = EnumSet.noneOf(Paint.class);
    private int index;
    private int mistakes;

    public PaintPuzzle(List<Mix> targets) {
        this.targets = List.copyOf(targets);
    }

    public static Mix mix(EnumSet<Paint> taps) {
        boolean r = taps.contains(Paint.RED);
        boolean y = taps.contains(Paint.YELLOW);
        boolean b = taps.contains(Paint.BLUE);
        int n = (r ? 1 : 0) + (y ? 1 : 0) + (b ? 1 : 0);
        if (n == 0) {
            return Mix.NONE;
        }
        if (n == 1) {
            return r ? Mix.RED : y ? Mix.YELLOW : Mix.BLUE;
        }
        if (n == 2) {
            if (r && y) {
                return Mix.ORANGE;
            }
            if (y) {
                return Mix.GREEN;
            }
            return Mix.PURPLE;
        }
        return Mix.SLUDGE;
    }

    public Mix current() {
        return mix(mixer);
    }

    /** Toggles a tap on/off. */
    public void toggle(Paint p) {
        if (done()) {
            return;
        }
        if (!mixer.remove(p)) {
            mixer.add(p);
        }
    }

    public Result release() {
        if (done()) {
            return Result.ALREADY_DONE;
        }
        Mix m = current();
        if (m == Mix.NONE) {
            return Result.EMPTY;
        }
        boolean ok = m == targets.get(index);
        mixer.clear();
        if (ok) {
            index++;
            return Result.CORRECT;
        }
        mistakes++;
        return Result.WRONG;
    }

    public boolean done() {
        return index >= targets.size();
    }

    public int solved() {
        return index;
    }

    public int total() {
        return targets.size();
    }

    public int mistakes() {
        return mistakes;
    }

    /** Colour the player has to produce next, or {@link Mix#NONE} when done. */
    public Mix target() {
        return done() ? Mix.NONE : targets.get(index);
    }

    public EnumSet<Paint> mixer() {
        return EnumSet.copyOf(mixer.isEmpty() ? EnumSet.noneOf(Paint.class) : mixer);
    }

    public void reset() {
        mixer.clear();
        index = 0;
        mistakes = 0;
    }

    public void restore(int solved) {
        mixer.clear();
        index = Math.max(0, Math.min(targets.size(), solved));
    }
}
