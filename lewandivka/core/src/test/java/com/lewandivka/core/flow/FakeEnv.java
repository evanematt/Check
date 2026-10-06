package com.lewandivka.core.flow;

import com.lewandivka.core.campaign.EncounterRecord;
import com.lewandivka.core.scale.PartyScale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Recording world for flow tests. */
public class FakeEnv implements FlowEnv {

    public final String structure;
    public long now = 1000;
    public PartyScale party;
    public final EncounterRecord record = new EncounterRecord();
    public final Map<String, String> stations = new HashMap<>();
    public final Map<String, String> blocks = new HashMap<>();
    public final Map<String, Boolean> gates = new HashMap<>();
    public final Map<String, Boolean> floods = new HashMap<>();
    public final Map<String, Boolean> lifts = new HashMap<>();
    public final List<String> log = new ArrayList<>();
    public final Map<String, Integer> spawned = new HashMap<>();
    public final Map<String, Integer> given = new HashMap<>();
    public int progress;
    public int checkpoint = -1;
    public final List<String> dialogues = new ArrayList<>();
    public final List<String> sounds = new ArrayList<>();
    public final List<String> messages = new ArrayList<>();

    public FakeEnv(String structure, int party) {
        this.structure = structure;
        this.party = PartyScale.of(party);
    }

    public String station(String marker, String property) {
        return stations.get(marker + "." + property);
    }

    public boolean gateOpen(String gate) {
        return gates.getOrDefault(gate, false);
    }

    public void advance(long ticks) {
        now += ticks;
    }

    @Override
    public String structure() {
        return structure;
    }

    @Override
    public long now() {
        return now;
    }

    @Override
    public PartyScale party() {
        return party;
    }

    @Override
    public EncounterRecord record() {
        return record;
    }

    @Override
    public void station(String marker, String property, String value) {
        stations.put(marker + "." + property, value);
    }

    @Override
    public void block(String marker, String blockKey) {
        blocks.put(marker, blockKey);
    }

    @Override
    public void gate(String gate, boolean open) {
        gates.put(gate, open);
        log.add("gate " + gate + " " + open);
    }

    @Override
    public void moveLift(String lift, boolean up) {
        lifts.put(lift, up);
    }

    @Override
    public void flood(String region, boolean fill) {
        floods.put(region, fill);
    }

    @Override
    public void say(UUID player, String langKey, Object... args) {
        messages.add(langKey);
    }

    @Override
    public void sound(String marker, String soundId) {
        sounds.add(soundId);
    }

    @Override
    public void soundAt(UUID player, String soundId) {
        sounds.add(soundId);
    }

    @Override
    public void fx(String marker, String effect) {
        log.add("fx " + marker + " " + effect);
    }

    @Override
    public void spawn(String entity, String marker, int count, String tag) {
        spawned.merge(tag + ":" + entity, count, Integer::sum);
    }

    @Override
    public void dropItem(String marker, String item, int count, String tag) {
        spawned.merge(tag + ":item:" + item, count, Integer::sum);
    }

    @Override
    public void despawn(String tag) {
        spawned.keySet().removeIf(k -> k.startsWith(tag + ":"));
    }

    @Override
    public void attract(String tag, UUID target) {
        log.add("attract " + tag);
    }

    @Override
    public void give(UUID player, String item, int count) {
        given.merge(item, count, Integer::sum);
    }

    @Override
    public void stepProgress(int delta) {
        progress += delta;
    }

    @Override
    public void checkpoint(int index) {
        checkpoint = index;
    }

    @Override
    public void dialogue(String scriptId) {
        dialogues.add(scriptId);
    }
}
