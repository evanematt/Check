package com.lewandivka.campaign;

import com.lewandivka.core.data.DataStore;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** {@link DataStore} on top of an {@link NbtCompound}: the whole campaign model persists through it. */
public final class NbtStore implements DataStore {

    private final NbtCompound nbt;

    public NbtStore(NbtCompound nbt) {
        this.nbt = nbt;
    }

    public NbtCompound nbt() {
        return nbt;
    }

    @Override
    public boolean has(String key) {
        return nbt.contains(key);
    }

    @Override
    public void remove(String key) {
        nbt.remove(key);
    }

    @Override
    public Collection<String> keys() {
        return new ArrayList<>(nbt.getKeys());
    }

    @Override
    public void putInt(String key, int value) {
        nbt.putInt(key, value);
    }

    @Override
    public int getInt(String key, int def) {
        return nbt.contains(key) ? nbt.getInt(key) : def;
    }

    @Override
    public void putLong(String key, long value) {
        nbt.putLong(key, value);
    }

    @Override
    public long getLong(String key, long def) {
        return nbt.contains(key) ? nbt.getLong(key) : def;
    }

    @Override
    public void putBool(String key, boolean value) {
        nbt.putBoolean(key, value);
    }

    @Override
    public boolean getBool(String key, boolean def) {
        return nbt.contains(key) ? nbt.getBoolean(key) : def;
    }

    @Override
    public void putString(String key, String value) {
        nbt.putString(key, value);
    }

    @Override
    public String getString(String key, String def) {
        return nbt.contains(key, NbtElement.STRING_TYPE) ? nbt.getString(key) : def;
    }

    @Override
    public void putStrings(String key, Collection<String> values) {
        NbtList list = new NbtList();
        for (String v : values) {
            list.add(NbtString.of(v));
        }
        nbt.put(key, list);
    }

    @Override
    public List<String> getStrings(String key) {
        List<String> out = new ArrayList<>();
        if (nbt.contains(key, NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList(key, NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                out.add(list.getString(i));
            }
        }
        return out;
    }

    @Override
    public DataStore child(String key) {
        if (!nbt.contains(key, NbtElement.COMPOUND_TYPE)) {
            nbt.put(key, new NbtCompound());
        }
        return new NbtStore(nbt.getCompound(key));
    }

    @Override
    public boolean hasChild(String key) {
        return nbt.contains(key, NbtElement.COMPOUND_TYPE);
    }

    @Override
    public List<String> childKeys() {
        List<String> out = new ArrayList<>();
        for (String k : nbt.getKeys()) {
            if (nbt.getType(k) == NbtElement.COMPOUND_TYPE) {
                out.add(k);
            }
        }
        return out;
    }
}
