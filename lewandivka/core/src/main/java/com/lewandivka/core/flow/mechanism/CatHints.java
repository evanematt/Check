package com.lewandivka.core.flow.mechanism;

import com.lewandivka.core.flow.FlowEnv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cats as navigation helpers instead of waypoint beams: when players linger at a place with a hidden route, Chinazik
 * sits and stares at the wall, then Metadonna scratches it, and the wall opens. The helper cats are temporary copies
 * that vanish again; the story cats at the base are never moved.
 */
public final class CatHints {

    public static final String HELPERS = "cat.helper";
    private static final int LINGER_TICKS = 50;
    private static final int SCRATCH_TICKS = 110;
    private static final int OPEN_TICKS = 200;

    /**
     * @param id      stable id of the hidden route (persisted as {@code hint.<id>})
     * @param watch   marker the players have to stand near
     * @param stare   marker where Chinazik sits and stares
     * @param scratch marker where Metadonna scratches
     * @param gate    hidden gate that opens at the end
     */
    public record Site(String id, String watch, String stare, String scratch, String gate) {
    }

    private final FlowEnv env;
    private final List<Site> sites;
    private final Map<String, Long> since = new HashMap<>();
    private final Map<String, Long> helpersUntil = new HashMap<>();

    public CatHints(FlowEnv env, List<Site> sites) {
        this.env = env;
        this.sites = List.copyOf(sites);
    }

    /** True once the story cats are back: before that the walls simply stay walls. */
    private boolean catsAvailable() {
        return env.world().bossDefeated("collar_collector");
    }

    public boolean found(String siteId) {
        return env.record().flag("hint." + siteId);
    }

    public void tick() {
        long now = env.now();
        for (Site s : sites) {
            Long until = helpersUntil.get(s.id());
            if (until != null && now >= until) {
                helpersUntil.remove(s.id());
                env.despawn(HELPERS + "." + s.id());
            }
            if (found(s.id()) || !catsAvailable()) {
                continue;
            }
            if (env.playersAt(s.watch(), 3.5).isEmpty()) {
                since.remove(s.id());
                continue;
            }
            long start = since.computeIfAbsent(s.id(), k -> now);
            long elapsed = now - start;
            String tag = HELPERS + "." + s.id();
            if (elapsed >= LINGER_TICKS && !helpersUntil.containsKey(s.id())) {
                env.spawn("chinazik", s.stare(), 1, tag);
                env.spawn("metadonna", s.scratch(), 1, tag);
                env.sound(s.stare(), "cat.meow_low");
                helpersUntil.put(s.id(), Long.MAX_VALUE);
            }
            if (elapsed == LINGER_TICKS + 20) {
                env.npcAnim("chinazik", "sit");
            }
            if (elapsed >= env.party().deadlineTicks(SCRATCH_TICKS) && helpersUntil.get(s.id()) != null) {
                env.sound(s.scratch(), "cat.scratch");
                env.fx(s.gate(), "scratch");
            }
            if (elapsed >= env.party().deadlineTicks(SCRATCH_TICKS) + 50 && env.record().setFlag("hint." + s.id())) {
                env.gate(s.gate(), true);
                env.say(null, "message.lewandivka.cat.hidden_route");
                env.sound(s.gate(), "garage.door");
                helpersUntil.put(s.id(), now + OPEN_TICKS);
            }
        }
    }

    /** Re-opens every route that was found before. */
    public void rebuild() {
        for (Site s : sites) {
            if (found(s.id())) {
                env.gate(s.gate(), true);
            }
        }
    }

    public void reset() {
        since.clear();
        for (Site s : sites) {
            env.despawn(HELPERS + "." + s.id());
        }
        helpersUntil.clear();
    }
}
