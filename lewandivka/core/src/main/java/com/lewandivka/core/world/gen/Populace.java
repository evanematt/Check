package com.lewandivka.core.world.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * The people of the district and where they are: citizens who stand about on the sidewalks, in the courtyards and at the
 * playground, and a trader behind every stall of the market. The glue makes sure that somebody stands at every spot whenever
 * a player is near; the spots are markers of the plan (citizens) and of the stalls (traders).
 */
public final class Populace {

    private Populace() {
    }

    /** A place for one person: the marker where he stands (as the glue finds it) and the kind of creature that stands there. */
    public record Spot(String marker, String entity) {
    }

    /** x, z and the kind of the citizens (the entity id without a number). Everyone stays within a few blocks of the spot. */
    private static final Object[][] CITIZENS = {
            // the main street: both sidewalks, the plaza at the end of the tram line
            {-20, -7, "citizen_babushka"}, {14, -7, "citizen_grandpa"}, {36, -7, "citizen_worker"}, {82, -7, "citizen_neighbour"},
            {110, -7, "citizen_student"}, {-10, 7, "citizen_yard_keeper"}, {28, 7, "citizen_kid"},
            {-40, -8, "citizen_grandpa"}, {-36, 8, "citizen_teacher"},
            // the market: those who buy
            {66, 8, "citizen_babushka"}, {80, 8, "citizen_student"}, {94, 8, "citizen_worker"},
            // the streets across
            {-64, -30, "citizen_neighbour"}, {-78, 40, "citizen_yard_keeper"}, {-64, 30, "citizen_babushka"}, {42, -40, "citizen_student"},
            {56, 40, "citizen_worker"}, {-72, -100, "citizen_grandpa"}, {-72, 100, "citizen_neighbour"}, {49, -100, "citizen_babushka"},
            {49, 100, "citizen_kid"}, {-20, -60, "citizen_yard_keeper"}, {30, -60, "citizen_teacher"}, {-20, 60, "citizen_student"},
            {30, 60, "citizen_worker"},
            // the lanes of the houses, the playground, the courtyards
            {98, -40, "citizen_kid"}, {98, 40, "citizen_babushka"}, {-4, 36, "citizen_kid"}, {-24, 41, "citizen_teacher"},
            {-30, -33, "citizen_grandpa"}, {12, 10, "citizen_neighbour"}, {-100, 100, "citizen_yard_keeper"}};

    /** The vendor of every stall of the market, in the order of the stalls. */
    public static final String[] VENDORS = {"vendor_baker", "vendor_greengrocer", "vendor_butcher", "vendor_handyman", "vendor_flea",
            "vendor_fishmonger", "vendor_gardener"};

    public static int citizenCount() {
        return CITIZENS.length;
    }

    /** Where the citizen {@code i} (from 0) stands: x and z. */
    public static int[] citizenAt(int i) {
        return new int[] {(int) CITIZENS[i][0], (int) CITIZENS[i][1]};
    }

    /** The name of the marker of the citizen {@code i} in the plan of the district. */
    public static String citizenMarker(int i) {
        return "citizen_" + (i + 1);
    }

    /** The name of the stall {@code i} of the market (a prop of the plan with a marker {@code vendor}). */
    public static String stall(int i) {
        return "stall_" + i;
    }

    public static List<Spot> spots() {
        List<Spot> out = new ArrayList<>();
        for (int i = 0; i < CITIZENS.length; i++) {
            out.add(new Spot("district:" + citizenMarker(i), (String) CITIZENS[i][2]));
        }
        for (int i = 0; i < VENDORS.length; i++) {
            out.add(new Spot(stall(i) + ":vendor", VENDORS[i]));
        }
        return out;
    }
}
