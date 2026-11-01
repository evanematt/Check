package com.lewandivka.quest;

import com.lewandivka.core.world.gen.DistrictPlan;
import com.lewandivka.world.dimension.Dimensions;
import net.minecraft.entity.Entity;

/**
 * The city and its surroundings. Everything the story does with the weather of the evening (the held time of day) or with
 * the night (the roaming gopniks) belongs to the people who are in town; out in the open country the game is the ordinary
 * survival game: its own days and nights and its own monsters.
 */
public final class Town {

    /** How far beyond the city square the story still reaches. */
    public static final int MARGIN = 40;

    private Town() {
    }

    public static boolean contains(Entity entity) {
        return Dimensions.DISTRICT_ID.equals(Dimensions.idOf(entity.getWorld()))
                && Math.max(Math.abs(entity.getX()), Math.abs(entity.getZ())) <= DistrictPlan.CITY_EDGE + MARGIN;
    }
}
