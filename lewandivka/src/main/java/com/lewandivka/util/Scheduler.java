package com.lewandivka.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Runs small tasks a number of server ticks later (dialogue lines, delayed spawns). Cleared when the server stops. */
public final class Scheduler {

    private record Task(long at, Runnable run) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    private Scheduler() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Scheduler::tick);
    }

    public static long now() {
        return now;
    }

    public static synchronized void later(long ticks, Runnable run) {
        TASKS.add(new Task(now + Math.max(0, ticks), run));
    }

    private static void tick(MinecraftServer server) {
        List<Runnable> due = new ArrayList<>();
        synchronized (Scheduler.class) {
            now = server.getTicks();
            TASKS.removeIf(t -> {
                if (t.at() <= now) {
                    due.add(t.run());
                    return true;
                }
                return false;
            });
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (RuntimeException e) {
                com.lewandivka.LewandivkaMod.LOGGER.error("Scheduled task failed", e);
            }
        }
    }

    public static synchronized void clear() {
        TASKS.clear();
    }
}
