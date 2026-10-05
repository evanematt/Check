package ua.lewandivka.logic;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Простий відкладений запуск дій на серверному потоці. */
public final class Scheduler {
    private record Task(long due, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    public static void after(MinecraftServer server, int ticks, Runnable action) {
        TASKS.add(new Task(now + ticks, action));
    }

    static void tick(MinecraftServer server) {
        now++;
        if (TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.due() <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) {
            try {
                t.action().run();
            } catch (Exception e) {
                ua.lewandivka.Lewandivka.LOG.error("Scheduled task failed", e);
            }
        }
    }

    static void clear() {
        TASKS.clear();
    }

    private Scheduler() {
    }
}
