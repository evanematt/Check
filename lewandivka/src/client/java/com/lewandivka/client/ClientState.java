package com.lewandivka.client;

import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.QuestStep;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

/** Everything the client knows about the campaign. The server is authoritative: this is only a mirror for the screens. */
public final class ClientState {

    public static QuestStep step = QuestStep.EXPLORE_DISTRICT;
    public static int counter;
    public static int abilityMask;
    public static int reputation;
    public static int rings;
    public static boolean portal;
    public static boolean synced;
    public static BlockPos compassTarget;
    public static String compassDimension = "";
    public static List<String> history = List.of();
    public static List<Integer> notes = List.of();

    public static final long[] COOLDOWN_UNTIL = new long[Ability.values().length];
    public static final int[] COOLDOWN_TOTAL = new int[Ability.values().length];

    public static UUID chargeHolder;
    public static float chargeFraction;
    public static boolean chargeActive;

    public static boolean monochrome;
    public static float monochromeAlpha;

    public static String cinematic = "";
    public static int cinematicTicks;
    public static int cinematicLength;

    public static boolean gliding;
    public static long tick;

    private ClientState() {
    }

    public static boolean has(Ability a) {
        return (abilityMask & a.bit()) != 0;
    }

    public static int cooldownLeft(Ability a) {
        return (int) Math.max(0, COOLDOWN_UNTIL[a.ordinal()] - tick);
    }

    public static void reset() {
        step = QuestStep.EXPLORE_DISTRICT;
        counter = 0;
        abilityMask = 0;
        synced = false;
        compassTarget = null;
        chargeActive = false;
        chargeHolder = null;
        monochrome = false;
        monochromeAlpha = 0;
        cinematic = "";
        cinematicTicks = 0;
        gliding = false;
        for (int i = 0; i < COOLDOWN_UNTIL.length; i++) {
            COOLDOWN_UNTIL[i] = 0;
        }
    }
}
