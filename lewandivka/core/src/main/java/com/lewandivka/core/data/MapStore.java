package com.lewandivka.core.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory {@link DataStore}. Used by tests and by tooling that needs a JSON-like tree. */
public final class MapStore implements DataStore {

    private final Map<String, Object> map = new LinkedHashMap<>();

    @Override
    public boolean has(String key) {
        return map.containsKey(key);
    }

    @Override
    public void remove(String key) {
        map.remove(key);
    }

    @Override
    public Collection<String> keys() {
        return new ArrayList<>(map.keySet());
    }

    @Override
    public void putInt(String key, int value) {
        map.put(key, value);
    }

    @Override
    public int getInt(String key, int def) {
        Object o = map.get(key);
        return o instanceof Number n ? n.intValue() : def;
    }

    @Override
    public void putLong(String key, long value) {
        map.put(key, value);
    }

    @Override
    public long getLong(String key, long def) {
        Object o = map.get(key);
        return o instanceof Number n ? n.longValue() : def;
    }

    @Override
    public void putBool(String key, boolean value) {
        map.put(key, value);
    }

    @Override
    public boolean getBool(String key, boolean def) {
        Object o = map.get(key);
        return o instanceof Boolean b ? b : def;
    }

    @Override
    public void putString(String key, String value) {
        map.put(key, value);
    }

    @Override
    public String getString(String key, String def) {
        Object o = map.get(key);
        return o instanceof String s ? s : def;
    }

    @Override
    public void putStrings(String key, Collection<String> values) {
        map.put(key, new ArrayList<>(values));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getStrings(String key) {
        Object o = map.get(key);
        return o instanceof List<?> l ? new ArrayList<>((List<String>) l) : new ArrayList<>();
    }

    @Override
    public DataStore child(String key) {
        Object o = map.get(key);
        if (o instanceof MapStore m) {
            return m;
        }
        MapStore m = new MapStore();
        map.put(key, m);
        return m;
    }

    @Override
    public boolean hasChild(String key) {
        return map.get(key) instanceof MapStore;
    }

    @Override
    public List<String> childKeys() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getValue() instanceof MapStore) {
                out.add(e.getKey());
            }
        }
        return out;
    }
}
