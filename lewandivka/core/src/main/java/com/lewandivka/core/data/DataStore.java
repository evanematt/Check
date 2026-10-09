package com.lewandivka.core.data;

import java.util.Collection;
import java.util.List;

/**
 * Tiny key/value tree abstraction used by every persistent model in the core.
 *
 * <p>The Minecraft side implements it on top of {@code NbtCompound}; the unit tests use {@link MapStore}.
 * Keeping persistence behind this interface means the whole campaign model (stages, abilities,
 * reputation, encounter flags ...) can be round-trip tested without a running game.</p>
 */
public interface DataStore {

    boolean has(String key);

    void remove(String key);

    /** All scalar and child keys currently stored. */
    Collection<String> keys();

    void putInt(String key, int value);

    int getInt(String key, int def);

    void putLong(String key, long value);

    long getLong(String key, long def);

    void putBool(String key, boolean value);

    boolean getBool(String key, boolean def);

    void putString(String key, String value);

    String getString(String key, String def);

    void putStrings(String key, Collection<String> values);

    /** Never returns null; unknown keys give an empty list. */
    List<String> getStrings(String key);

    /** Returns the nested store stored under {@code key}, creating it when missing. */
    DataStore child(String key);

    boolean hasChild(String key);

    /** Names of all nested stores directly below this one. */
    List<String> childKeys();
}
