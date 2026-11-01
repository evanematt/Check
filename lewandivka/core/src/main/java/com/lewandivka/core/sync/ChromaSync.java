package com.lewandivka.core.sync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Synchronisation of the Chroma tablet (end of Act 1).
 *
 * <p>The first player who takes the tablet starts a five-minute timer. Every <em>required</em>
 * player (everyone who was online and taking part at that moment: 3 -> all three, 2 -> both,
 * 1 -> the single player) must take it before the timer ends. If the time runs out the attempt is
 * reset without consuming anything; the glue code only removes tablets when
 * {@link Event#COMPLETED} is reported, so the quest item can never be lost to a failed attempt.</p>
 */
public final class ChromaSync {

    public static final int DEFAULT_TIME_LIMIT_TICKS = 5 * 60 * 20;

    public enum Phase {
        IDLE, RUNNING, COMPLETE
    }

    public enum Event {
        NONE, STARTED, CONSUMED, COMPLETED, EXPIRED, ABORTED
    }

    private final int timeLimitTicks;
    private Phase phase = Phase.IDLE;
    private final Set<UUID> required = new LinkedHashSet<>();
    private final Set<UUID> consumed = new LinkedHashSet<>();
    private long deadline;

    public ChromaSync() {
        this(DEFAULT_TIME_LIMIT_TICKS);
    }

    public ChromaSync(int timeLimitTicks) {
        this.timeLimitTicks = Math.max(20, timeLimitTicks);
    }

    /**
     * A player takes the tablet.
     *
     * @param online all currently online players that take part in the campaign (includes {@code player})
     */
    public Event consume(UUID player, Collection<UUID> online, long now) {
        if (phase == Phase.COMPLETE) {
            return Event.NONE;
        }
        if (phase == Phase.IDLE) {
            required.clear();
            consumed.clear();
            required.addAll(online);
            required.add(player);
            consumed.add(player);
            deadline = now + timeLimitTicks;
            phase = Phase.RUNNING;
            if (consumed.containsAll(required)) {
                phase = Phase.COMPLETE;
                return Event.COMPLETED;
            }
            return Event.STARTED;
        }
        if (!consumed.add(player)) {
            return Event.NONE;
        }
        if (consumed.containsAll(required)) {
            phase = Phase.COMPLETE;
            return Event.COMPLETED;
        }
        return Event.CONSUMED;
    }

    /**
     * Advances the timer and drops required players that went offline (they cannot block the party;
     * they are caught up when they reconnect).
     */
    public Event tick(Collection<UUID> online, long now) {
        if (phase != Phase.RUNNING) {
            return Event.NONE;
        }
        required.removeIf(id -> !online.contains(id));
        if (required.isEmpty()) {
            reset();
            return Event.ABORTED;
        }
        if (consumed.containsAll(required)) {
            phase = Phase.COMPLETE;
            return Event.COMPLETED;
        }
        if (now >= deadline) {
            reset();
            return Event.EXPIRED;
        }
        return Event.NONE;
    }

    public Phase phase() {
        return phase;
    }

    public boolean running() {
        return phase == Phase.RUNNING;
    }

    public long remainingTicks(long now) {
        return phase == Phase.RUNNING ? Math.max(0, deadline - now) : 0;
    }

    /** 1.0 = just started, 0.0 = expired. Used for the boss bar. */
    public float fraction(long now) {
        return phase == Phase.RUNNING ? Math.max(0f, Math.min(1f, remainingTicks(now) / (float) timeLimitTicks)) : 0f;
    }

    public Set<UUID> required() {
        return Collections.unmodifiableSet(required);
    }

    public Set<UUID> consumed() {
        return Collections.unmodifiableSet(consumed);
    }

    /** Everyone who has to be moved through the portal: required plus late volunteers. */
    public List<UUID> participants() {
        Set<UUID> all = new LinkedHashSet<>(required);
        all.addAll(consumed);
        return new ArrayList<>(all);
    }

    public int missing() {
        int n = 0;
        for (UUID id : required) {
            if (!consumed.contains(id)) {
                n++;
            }
        }
        return n;
    }

    public void reset() {
        phase = Phase.IDLE;
        required.clear();
        consumed.clear();
        deadline = 0;
    }
}
