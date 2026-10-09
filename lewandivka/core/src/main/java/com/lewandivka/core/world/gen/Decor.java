package com.lewandivka.core.world.gen;

/**
 * The one place that decides which block stands for a piece of furniture. The district is furnished with the blocks of the
 * ordinary game; a furniture mod installed alongside may later give a nicer model for the same piece, and the blocks named here
 * stay as the fallback when it is not there.
 */
final class Decor {

    private Decor() {
    }

    /** The block for the piece {@code what} (a bed, a sofa, a table ...); {@code vanilla} is what the game itself offers. */
    static String pick(String what, String vanilla) {
        return vanilla;
    }
}
