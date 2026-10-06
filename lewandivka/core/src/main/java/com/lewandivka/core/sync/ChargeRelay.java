package com.lewandivka.core.sync;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The Chromatic Charge of the final boss as a controlled, server-side gameplay object.
 *
 * <p>Only the holder can hurt the boss. The charge overloads after {@link #overloadTicks} unless it
 * is passed to somebody else (pass resets the timer). With one player the charge is thrown into a
 * relay device and returns after a short delay with a fresh timer, so the passing rhythm exists
 * in solo play too. The charge is never an inventory item that can be lost: whatever happens to
 * the holder (death, disconnect), the next {@link #tick} re-assigns it.</p>
 */
public final class ChargeRelay {

    public static final int DEFAULT_OVERLOAD_TICKS = 12 * 20;
    public static final int DEFAULT_RELAY_DELAY_TICKS = 50;

    public enum Event {
        NONE, GRANTED, PASSED, RELAYED, RETURNED, OVERLOAD, REASSIGNED
    }

    public record Result(Event event, UUID player, int relay) {
        static final Result NONE = new Result(Event.NONE, null, -1);
    }

    private final int overloadTicks;
    private final int relayDelayTicks;

    private UUID holder;
    private long overloadAt;
    private int relayId = -1;
    private long returnAt;
    private int overloadCount;
    private long frozenUntil = Long.MIN_VALUE;

    public ChargeRelay() {
        this(DEFAULT_OVERLOAD_TICKS, DEFAULT_RELAY_DELAY_TICKS);
    }

    public ChargeRelay(int overloadTicks, int relayDelayTicks) {
        this.overloadTicks = Math.max(20, overloadTicks);
        this.relayDelayTicks = Math.max(1, relayDelayTicks);
    }

    public UUID holder() {
        return holder;
    }

    public boolean inRelay() {
        return relayId >= 0;
    }

    public int relayId() {
        return relayId;
    }

    public int overloadCount() {
        return overloadCount;
    }

    public int overloadTicks() {
        return overloadTicks;
    }

    public boolean isHolder(UUID player) {
        return holder != null && holder.equals(player);
    }

    /** Only the holder deals damage to the boss. */
    public boolean canDamageBoss(UUID attacker) {
        return isHolder(attacker);
    }

    /** Ticks left before the holder overloads (0 when nobody holds it). */
    public long remaining(long now) {
        if (holder == null) {
            return 0;
        }
        if (now < frozenUntil) {
            return overloadAt - now + (frozenUntil - now);
        }
        return Math.max(0, overloadAt - now);
    }

    public float fraction(long now) {
        return Math.min(1f, remaining(now) / (float) overloadTicks);
    }

    /** Gives the charge to a player and starts a fresh timer. */
    public Result grant(UUID player, long now) {
        holder = player;
        relayId = -1;
        overloadAt = now + overloadTicks;
        return new Result(Event.GRANTED, player, -1);
    }

    /** The holder passes the charge to another player. */
    public Result pass(UUID from, UUID to, long now) {
        if (!isHolder(from) || to == null || to.equals(from)) {
            return Result.NONE;
        }
        holder = to;
        overloadAt = now + overloadTicks;
        return new Result(Event.PASSED, to, -1);
    }

    /** The holder throws the charge into a relay device (solo play and optional in groups). */
    public Result throwToRelay(UUID from, int relay, long now) {
        if (!isHolder(from) || relay < 0) {
            return Result.NONE;
        }
        holder = null;
        relayId = relay;
        returnAt = now + relayDelayTicks;
        return new Result(Event.RELAYED, from, relay);
    }

    /** Stops the overload timer for {@code ticks} (cutscenes, boss monologues). */
    public void freeze(long now, int ticks) {
        long until = now + ticks;
        if (until > frozenUntil) {
            frozenUntil = until;
        }
    }

    /** Clears the object (boss reset / encounter end). */
    public void clear() {
        holder = null;
        relayId = -1;
        overloadCount = 0;
        frozenUntil = Long.MIN_VALUE;
    }

    /**
     * Advances the relay.
     *
     * @param eligible alive, in-arena participants, best receiver first (e.g. nearest to the relay)
     * @return events that happened this tick (usually empty)
     */
    public List<Result> tick(long now, Collection<UUID> eligible) {
        List<Result> out = new ArrayList<>(2);
        if (now < frozenUntil) {
            if (holder != null) {
                // Keep the timer from running while frozen.
                overloadAt++;
            }
            if (relayId >= 0) {
                returnAt++;
            }
            return out;
        }

        // Holder gone (death, disconnect, left the arena)? Re-assign instead of losing the charge.
        if (holder != null && !eligible.contains(holder)) {
            UUID next = firstOther(eligible, holder);
            if (next != null) {
                holder = next;
                overloadAt = now + overloadTicks;
                out.add(new Result(Event.REASSIGNED, next, -1));
            } else {
                holder = null;
            }
        }

        if (holder == null && relayId < 0 && !eligible.isEmpty()) {
            UUID first = eligible.iterator().next();
            out.add(grant(first, now));
            return out;
        }

        if (relayId >= 0 && now >= returnAt && !eligible.isEmpty()) {
            UUID target = eligible.iterator().next();
            int fromRelay = relayId;
            holder = target;
            relayId = -1;
            overloadAt = now + overloadTicks;
            out.add(new Result(Event.RETURNED, target, fromRelay));
            return out;
        }

        if (holder != null && now >= overloadAt) {
            overloadCount++;
            overloadAt = now + overloadTicks;
            out.add(new Result(Event.OVERLOAD, holder, -1));
        }
        return out;
    }

    private static UUID firstOther(Collection<UUID> eligible, UUID except) {
        for (UUID id : eligible) {
            if (!id.equals(except)) {
                return id;
            }
        }
        return null;
    }
}
