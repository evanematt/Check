package ua.lewandivka.campaign;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ua.lewandivka.logic.LewState;

import java.util.List;

/**
 * Сервер-авторитетний стан кампанії. Етап виводиться з віх у LewState,
 * тому переживає рестарти і не може «розсинхронізуватись».
 */
public final class Campaign {
    /** Віхи, що відповідають етапам (індекс = Stage.ordinal()). Порожня — етап без власної віхи. */
    private static final String[] MILESTONE = {
            "", "joined_any", "lvl1", "lvl2", "lvl3", "lvl4", "lvl5", "chroma_started", "arrived",
            "done_garage", "done_shelter", "done_aqua", "done_depot", "tower_entered", "final_started", "done_tower", "postgame"};

    public static Stage stage(LewState st) {
        Stage result = Stage.PROLOGUE;
        for (Stage s : Stage.values()) {
            String m = MILESTONE[s.ordinal()];
            boolean reached = m.isEmpty() || (m.equals("arrived") ? st.arrived : st.has(m));
            if (s == Stage.CHROMA_TRANSITION && st.arrived) {
                reached = true;
            }
            if (!reached) {
                break;
            }
            result = s;
        }
        // Етапи після прибуття в Хромандівку йдуть за порядком босів: «активний» = наступний непройдений.
        if (st.arrived && result.ordinal() >= Stage.CHROMANDIVKA_BASE.ordinal() && result.ordinal() < Stage.EPILOGUE.ordinal()) {
            if (!st.has("done_garage")) {
                return st.has("base_left") ? Stage.RAINBOW_GARAGE : Stage.CHROMANDIVKA_BASE;
            }
            if (!st.has("done_shelter")) {
                return Stage.SHELTER;
            }
            if (!st.has("done_aqua")) {
                return Stage.AQUAPARK;
            }
            if (!st.has("done_depot")) {
                return Stage.SKY_DEPOT;
            }
            return st.has("final_started") ? Stage.FINAL_BOSS : Stage.TOWER;
        }
        return result;
    }

    /** Адмінська команда: виставляє всі віхи до потрібного етапу включно (і знімає пізніші). */
    public static void setStage(MinecraftServer server, Stage target) {
        LewState st = LewState.get(server);
        for (Stage s : Stage.values()) {
            String m = MILESTONE[s.ordinal()];
            if (m.isEmpty()) {
                continue;
            }
            boolean on = s.ordinal() <= target.ordinal();
            if (m.equals("arrived")) {
                st.arrived = on;
                st.markDirty();
            } else if (on) {
                st.set(m);
            } else {
                st.unset(m);
            }
        }
    }

    /** Розмір активної групи: онлайн-гравці (1..3). Механіки масштабуються від нього, а не від «рівно трьох». */
    public static int partySize(MinecraftServer server) {
        if (partyOverride > 0) {
            return partyOverride;
        }
        return Math.max(1, Math.min(3, server.getPlayerManager().getCurrentPlayerCount()));
    }

    /** Налагоджувальне перевизначення розміру групи (0 = вимкнено). Не зберігається. */
    public static int partyOverride = 0;

    /** Чисте масштабування вікна синхронізації (тіки) — окремо від сервера, щоб тестувати. */
    public static int windowFor(int party) {
        return switch (party) {
            case 1 -> 1200;
            case 2 -> 800;
            default -> 600;
        };
    }

    /** Вікно синхронізації перемикачів у тіках: 3 гравці — коротше, 1 — достатнє для послідовного проходу. */
    public static int syncWindowTicks(MinecraftServer server) {
        return windowFor(partySize(server));
    }

    public static List<Stage> history(LewState st) {
        Stage now = stage(st);
        return List.of(Stage.values()).subList(0, now.ordinal());
    }

    public static boolean isOnline(ServerPlayerEntity p) {
        return !p.isDisconnected();
    }

    private Campaign() {
    }
}
