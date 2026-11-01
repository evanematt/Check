package com.lewandivka.core.puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Final boss, phase 2: arena platforms lose colour in four stages
 * (0 coloured, 1 grey, 2 cracked, 3 collapsed).
 *
 * <p>Invariants (unit tested): anchor platforms never decay, and the number of platforms that are
 * still standing (stage &lt; 3) never drops below {@code minStanding}. The fight can therefore
 * never destroy every safe platform at once.</p>
 */
public final class PlatformDecay {

    public static final int COLOURED = 0;
    public static final int GREY = 1;
    public static final int CRACKED = 2;
    public static final int COLLAPSED = 3;

    private final int[] stage;
    private final boolean[] anchor;
    private final int minStanding;

    public PlatformDecay(int platforms, boolean[] anchors, int minStanding) {
        this.stage = new int[platforms];
        this.anchor = anchors.clone();
        this.minStanding = Math.max(1, minStanding);
    }

    public int count() {
        return stage.length;
    }

    public int stage(int platform) {
        return stage[platform];
    }

    public boolean isAnchor(int platform) {
        return anchor[platform];
    }

    public int standing() {
        int n = 0;
        for (int s : stage) {
            if (s < COLLAPSED) {
                n++;
            }
        }
        return n;
    }

    /** Platforms that can still be stood on without breaking this very tick (stage 0..2). */
    public List<Integer> standingPlatforms() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < stage.length; i++) {
            if (stage[i] < COLLAPSED) {
                out.add(i);
            }
        }
        return out;
    }

    /**
     * Advances one random eligible platform by one stage.
     *
     * @return the platform that advanced, or -1 when nothing may decay right now
     */
    public int advance(Random rng) {
        List<Integer> candidates = new ArrayList<>();
        int standing = standing();
        for (int i = 0; i < stage.length; i++) {
            if (anchor[i] || stage[i] >= COLLAPSED) {
                continue;
            }
            // Collapsing this platform must not push the number of standing ones below the minimum.
            if (stage[i] == CRACKED && standing - 1 < minStanding) {
                continue;
            }
            candidates.add(i);
        }
        if (candidates.isEmpty()) {
            return -1;
        }
        int pick = candidates.get(rng.nextInt(candidates.size()));
        stage[pick]++;
        return pick;
    }

    /** Colour returns to a platform (e.g. when a cat redirects the charge to it). */
    public void restore(int platform) {
        stage[platform] = COLOURED;
    }

    /** Collapsed platforms grow back as grey ones so the arena never empties during a long fight. */
    public int regrowOne() {
        for (int i = 0; i < stage.length; i++) {
            if (stage[i] == COLLAPSED) {
                stage[i] = GREY;
                return i;
            }
        }
        return -1;
    }

    public void reset() {
        java.util.Arrays.fill(stage, COLOURED);
    }
}
