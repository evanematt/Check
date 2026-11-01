package com.lewandivka.world.dimension;

import com.lewandivka.util.Ids;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/** Keys and lookups of the two custom dimensions. */
public final class Dimensions {

    public static final String DISTRICT_ID = "lewandivka:district";
    public static final String CHROMA_ID = "lewandivka:chromandivka";
    public static final RegistryKey<World> DISTRICT = RegistryKey.of(RegistryKeys.WORLD, Ids.of("district"));
    public static final RegistryKey<World> CHROMA = RegistryKey.of(RegistryKeys.WORLD, Ids.of("chromandivka"));

    private Dimensions() {
    }

    public static ServerWorld district(MinecraftServer server) {
        return server.getWorld(DISTRICT);
    }

    public static ServerWorld chroma(MinecraftServer server) {
        return server.getWorld(CHROMA);
    }

    /** The world of a dimension id such as {@code lewandivka:district}, or null when it is not loaded. */
    public static ServerWorld world(MinecraftServer server, String dimensionId) {
        return switch (dimensionId) {
            case DISTRICT_ID -> district(server);
            case CHROMA_ID -> chroma(server);
            default -> null;
        };
    }

    public static String idOf(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    public static boolean isOurs(World world) {
        String id = idOf(world);
        return DISTRICT_ID.equals(id) || CHROMA_ID.equals(id);
    }
}
