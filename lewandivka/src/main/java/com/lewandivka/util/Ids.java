package com.lewandivka.util;

import net.minecraft.util.Identifier;

/** Identifier helpers: every id of the mod lives in the {@code lewandivka} namespace. */
public final class Ids {

    public static final String MOD_ID = "lewandivka";

    private Ids() {
    }

    public static Identifier of(String path) {
        return new Identifier(MOD_ID, path);
    }

    /** Parses {@code lewandivka:thing} or a bare path. */
    public static Identifier parse(String id) {
        return id.indexOf(':') >= 0 ? new Identifier(id) : of(id);
    }
}
