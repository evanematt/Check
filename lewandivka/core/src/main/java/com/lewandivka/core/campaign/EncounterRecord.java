package com.lewandivka.core.campaign;

import com.lewandivka.core.data.DataStore;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persistent part of a scripted encounter (dungeon, boss fight, puzzle room).
 *
 * <p>Only the logical state is persisted (status, last checkpoint, flags). Everything volatile
 * (spawned mobs, timers, scheduled tasks) is rebuilt from these flags after a restart, which is how
 * the dungeons "return to the last valid checkpoint" without any fragile world-scan.</p>
 */
public final class EncounterRecord {

    public enum Status {
        IDLE(0), ACTIVE(1), COMPLETE(2);

        public final int id;

        Status(int id) {
            this.id = id;
        }

        static Status byId(int id) {
            for (Status s : values()) {
                if (s.id == id) {
                    return s;
                }
            }
            return IDLE;
        }
    }

    private Status status = Status.IDLE;
    private int checkpoint;
    private final Set<String> flags = new LinkedHashSet<>();

    public Status status() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int checkpoint() {
        return checkpoint;
    }

    public void setCheckpoint(int checkpoint) {
        this.checkpoint = checkpoint;
    }

    public boolean flag(String f) {
        return flags.contains(f);
    }

    public boolean setFlag(String f) {
        return flags.add(f);
    }

    public boolean clearFlag(String f) {
        return flags.remove(f);
    }

    public Set<String> flags() {
        return Collections.unmodifiableSet(flags);
    }

    /** Back to the very beginning (used by the admin reset command). */
    public void resetAll() {
        status = Status.IDLE;
        checkpoint = 0;
        flags.clear();
    }

    public void write(DataStore s) {
        s.putInt("status", status.id);
        s.putInt("cp", checkpoint);
        s.putStrings("flags", flags);
    }

    public static EncounterRecord read(DataStore s) {
        EncounterRecord r = new EncounterRecord();
        r.status = Status.byId(s.getInt("status", 0));
        r.checkpoint = s.getInt("cp", 0);
        r.flags.addAll(s.getStrings("flags"));
        return r;
    }
}
