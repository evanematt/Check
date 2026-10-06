package com.lewandivka.core.puzzle;

/**
 * Chinazik's "two who know the way" route. He is not a follower: players have to place food at
 * the designated spots in order. He walks from point to point, retreats to the previous safe point
 * when players crowd him, and finally steps onto the old rug (the last point).
 *
 * <p>Points are numbered {@code 0..last}; point 0 is where he is first met, {@code last} is the rug.</p>
 */
public final class CatRoute {

    public enum State {
        WAITING, MOVING, EATING, DONE
    }

    private final int last;
    private int at;
    private int target = -1;
    private State state = State.WAITING;
    private int retreats;

    public CatRoute(int lastPointIndex) {
        if (lastPointIndex < 1) {
            throw new IllegalArgumentException("a route needs at least two points");
        }
        this.last = lastPointIndex;
    }

    public int at() {
        return at;
    }

    public int target() {
        return target;
    }

    public int last() {
        return last;
    }

    public State state() {
        return state;
    }

    public int retreats() {
        return retreats;
    }

    /** The point where food should be put next, or -1 when finished. */
    public int nextFoodPoint() {
        return state == State.DONE ? -1 : at + 1;
    }

    /** Food was placed at {@code point}. Accepted only for the next point of the route. */
    public boolean placeFood(int point) {
        if (state != State.WAITING || point != at + 1 || point > last) {
            return false;
        }
        target = point;
        state = State.MOVING;
        return true;
    }

    /** The cat reached its target point. */
    public void arrive() {
        if (state != State.MOVING) {
            return;
        }
        at = target;
        target = -1;
        if (at >= last) {
            state = State.DONE;
        } else {
            state = State.EATING;
        }
    }

    /** Finished eating; the cat looks around and waits for the next food. */
    public void finishEating() {
        if (state == State.EATING) {
            state = State.WAITING;
        }
    }

    /**
     * Players crowded the cat. While walking or eating he retreats to the previous safe point
     * (he never retreats past point 0).
     *
     * @return the point he runs to, or -1 if nothing changed
     */
    public int scare() {
        if (state == State.DONE || state == State.WAITING) {
            return -1;
        }
        retreats++;
        // Walking: he turns back to the point he came from. Eating: he runs one point further back.
        int safe = state == State.EATING ? Math.max(0, at - 1) : at;
        // He waits at the safe point; food has to be placed again for the point after it.
        at = safe;
        target = -1;
        state = State.WAITING;
        return safe;
    }

    public void reset() {
        at = 0;
        target = -1;
        state = State.WAITING;
        retreats = 0;
    }

    /** Restore after a server restart from the persisted point index. */
    public void restore(int atPoint) {
        reset();
        at = Math.max(0, Math.min(last, atPoint));
        if (at >= last) {
            state = State.DONE;
        }
    }
}
