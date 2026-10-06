package com.lewandivka.core.flow;

import java.util.UUID;

/**
 * The logic of one dungeon or puzzle room. A flow is plain Java: it reads and writes the world only through
 * {@link FlowEnv}, keeps its permanent progress in the encounter record and can therefore be rebuilt after a restart.
 */
public interface Flow {

    /** Encounter id (stable, used by {@code /lewandivka reset encounter <id>}). */
    String id();

    /** Structure whose markers the flow uses. */
    String structure();

    /**
     * A player used a station (right click on a marked block).
     *
     * @return true when the flow handled the interaction
     */
    boolean use(String marker, UUID player);

    /** Called every server tick while players are inside the structure; must be cheap. */
    void tick();

    /** Re-applies the persisted progress to the world (server restart, chunk reload). */
    void rebuild();

    /** Back to the last valid checkpoint: solved puzzles stay solved, volatile parts start over. */
    void reset();

    /** True once the whole encounter was completed. */
    boolean complete();
}
