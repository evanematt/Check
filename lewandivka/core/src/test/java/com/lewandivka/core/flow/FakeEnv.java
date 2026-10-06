package com.lewandivka.core.flow;

import com.lewandivka.core.campaign.EncounterRecord;
import com.lewandivka.core.campaign.WorldProgress;
import com.lewandivka.core.registry.BlockSpec;
import com.lewandivka.core.registry.ModBlocks;
import com.lewandivka.core.registry.ModEntities;
import com.lewandivka.core.registry.ModItems;
import com.lewandivka.core.registry.ModSounds;
import com.lewandivka.core.scale.PartyScale;
import com.lewandivka.core.text.DialogueBook;

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
    public final WorldProgress world = new WorldProgress();
    public final List<UUID> inside = new ArrayList<>();
    public final Map<String, Integer> inventory = new HashMap<>();
    public final List<String> bosses = new ArrayList<>();
    public final Map<String, String> stations = new HashMap<>();
    public final Map<String, String> blocks = new HashMap<>();
    public final Map<String, Boolean> gates = new HashMap<>();
    public final Map<String, Boolean> floods = new HashMap<>();
    public final Map<String, Boolean> lifts = new HashMap<>();
    public final List<String> log = new ArrayList<>();
    public final Map<String, Integer> spawned = new HashMap<>();
    public final Map<String, Integer> given = new HashMap<>();
    public int progress;
    public final List<String> events = new ArrayList<>();
    public int checkpoint = -1;
    public final List<String> dialogues = new ArrayList<>();
    public final List<String> sounds = new ArrayList<>();
    public final List<String> messages = new ArrayList<>();

    public FakeEnv(String structure, int party) {
        this.structure = structure;
        this.party = PartyScale.of(party);
    }

    private static final java.util.Set<String> VANILLA_BLOCKS_OK = java.util.Set.of("minecraft:redstone_lamp", "minecraft:air");

    /** Typos in ids must fail the flow tests, not the first playtest. */
    private static void sound(String id) {
        if (ModSounds.byId(id) == null) {
            throw new IllegalArgumentException("unknown sound '" + id + "'");
        }
    }

    private static void entity(String id) {
        if (ModEntities.byId(id) == null && !id.equals("minecraft:pig")) {
            throw new IllegalArgumentException("unknown entity '" + id + "'");
        }
    }

    private static void item(String id) {
        if (ModItems.byId(id) == null) {
            throw new IllegalArgumentException("unknown item '" + id + "'");
        }
    }

    private static void blockKey(String key) {
        if (key.startsWith("lewandivka:")) {
            int br = key.indexOf('[');
            String id = key.substring("lewandivka:".length(), br < 0 ? key.length() : br);
            BlockSpec spec = ModBlocks.byId(id);
            if (spec == null) {
                throw new IllegalArgumentException("unknown block '" + key + "'");
            }
            String problem = spec.check(br < 0 ? "" : key.substring(br + 1, key.length() - 1));
            if (problem != null) {
                throw new IllegalArgumentException("bad block key '" + key + "': " + problem);
            }
        }
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
    public WorldProgress world() {
        return world;
    }

    @Override
    public List<UUID> players() {
        return inside;
    }

    @Override
    public int alive(String tag) {
        int n = 0;
        for (Map.Entry<String, Integer> e : spawned.entrySet()) {
            if (e.getKey().startsWith(tag + ":")) {
                n += e.getValue();
            }
        }
        return n;
    }

    /** Kills one tagged enemy (the test plays the player's part). */
    public boolean kill(String tag) {
        for (Map.Entry<String, Integer> e : spawned.entrySet()) {
            if (e.getKey().startsWith(tag + ":") && e.getValue() > 0) {
                e.setValue(e.getValue() - 1);
                return true;
            }
        }
        return false;
    }

    @Override
    public void spawnBoss(String entity, String marker) {
        entity(entity);
        if (!bosses.contains(entity)) {
            bosses.add(entity);
        }
    }

    @Override
    public void despawnBoss(String entity) {
        bosses.remove(entity);
    }

    @Override
    public boolean take(UUID player, String item, int count) {
        int have = inventory.getOrDefault(item, 0);
        if (have < count) {
            return false;
        }
        inventory.put(item, have - count);
        return true;
    }

    @Override
    public boolean has(UUID player, String item) {
        return inventory.getOrDefault(item, 0) > 0;
    }

    @Override
    public void station(String marker, String property, String value) {
        stations.put(marker + "." + property, value);
    }

    @Override
    public void block(String marker, String blockKey) {
        blockKey(blockKey);
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
        sound(soundId);
        sounds.add(soundId);
    }

    @Override
    public void soundAt(UUID player, String soundId) {
        sound(soundId);
        sounds.add(soundId);
    }

    @Override
    public void fx(String marker, String effect) {
        log.add("fx " + marker + " " + effect);
    }

    @Override
    public void spawn(String entity, String marker, int count, String tag) {
        entity(entity);
        spawned.merge(tag + ":" + entity, count, Integer::sum);
    }

    @Override
    public void dropItem(String marker, String item, int count, String tag) {
        item(item);
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
        item(item);
        given.merge(item, count, Integer::sum);
    }

    public final Map<String, List<UUID>> playersAtMarker = new HashMap<>();
    public final Map<String, Double> distances = new HashMap<>();
    public final Map<String, String> npcPlace = new HashMap<>();
    public final Map<String, String> npcNames = new HashMap<>();
    public final Map<String, Integer> npcCrowd = new HashMap<>();
    public final List<String> npcAnims = new ArrayList<>();
    public final List<String> volumes = new ArrayList<>();

    @Override
    public List<UUID> playersAt(String marker, double radius) {
        return playersAtMarker.getOrDefault(marker, List.of());
    }

    @Override
    public double distance(UUID player, String marker) {
        return distances.getOrDefault(marker, 1000.0);
    }

    @Override
    public void soundAt(UUID player, String soundId, float volume) {
        sound(soundId);
        volumes.add(soundId + "@" + String.format(java.util.Locale.ROOT, "%.2f", volume));
    }

    @Override
    public void npcMove(String npc, String marker) {
        npcPlace.put(npc, marker);
        log.add("move " + npc + " " + marker);
    }

    @Override
    public boolean npcAt(String npc, String marker, double radius) {
        return marker.equals(npcPlace.get(npc)) && !npcMoving.contains(npc);
    }

    public final java.util.Set<String> npcMoving = new java.util.HashSet<>();

    @Override
    public void npcAnim(String npc, String anim) {
        npcAnims.add(npc + ":" + anim);
    }

    @Override
    public void npcName(String npc, String name) {
        npcNames.put(npc, name);
    }

    @Override
    public int playersNear(String npc, double radius) {
        return npcCrowd.getOrDefault(npc, 0);
    }

    public boolean night = true;
    public boolean forcedNight;
    public final Map<String, Double> resistance = new HashMap<>();
    public final Map<String, Integer> nearCounts = new HashMap<>();
    public final List<String> stationCalls = new ArrayList<>();
    public final List<String> cinematics = new ArrayList<>();

    @Override
    public boolean night() {
        return night;
    }

    @Override
    public void forceNight(boolean on) {
        forcedNight = on;
    }

    @Override
    public int near(String tag, String marker, double radius) {
        return nearCounts.getOrDefault(tag, 0);
    }

    @Override
    public void resistance(String entity, double factor) {
        resistance.put(entity, factor);
    }

    public boolean bossAccepts = true;

    @Override
    public boolean bossStation(String entity, String action, int index, UUID player) {
        stationCalls.add(entity + ":" + action + ":" + index);
        return bossAccepts;
    }

    public final Map<String, List<String>> tramRoutes = new HashMap<>();
    public final Map<String, Long> tramBusyUntil = new HashMap<>();
    public final Map<String, List<UUID>> tramRiders = new HashMap<>();
    public final List<String> tramMobs = new ArrayList<>();
    public final List<String> tramCleared = new ArrayList<>();
    public double tramSpeed;
    public long tramTicks = 100;

    @Override
    public void tramDrive(String tag, List<String> markers, double speed) {
        tramRoutes.put(tag, List.copyOf(markers));
        tramSpeed = speed;
        tramBusyUntil.put(tag, now + tramTicks);
    }

    @Override
    public boolean tramBusy(String tag) {
        return tramBusyUntil.getOrDefault(tag, 0L) > now;
    }

    @Override
    public void tramBoard(String tag, List<UUID> players) {
        tramRiders.put(tag, List.copyOf(players));
    }

    @Override
    public void tramBoardMobs(String tag, String entity, int count, String mobTag) {
        entity(entity);
        tramMobs.add(entity + "x" + count);
        spawned.merge(mobTag + ":" + entity, count, Integer::sum);
    }

    @Override
    public void tramClear(String tag, String dismountMarker) {
        tramCleared.add(tag + "->" + dismountMarker);
        tramBusyUntil.remove(tag);
        tramRiders.remove(tag);
    }

    @Override
    public void cinematic(String id) {
        cinematics.add(id);
    }

    @Override
    public void event(String id) {
        events.add(id);
    }

    @Override
    public void stepCounter(int value) {
        progress = value;
    }

    public long eventCount(String id) {
        return events.stream().filter(id::equals).count();
    }

    @Override
    public void checkpoint(int index) {
        checkpoint = index;
    }

    @Override
    public void dialogue(String scriptId) {
        if (DialogueBook.get(scriptId) == null) {
            throw new IllegalArgumentException("unknown dialogue '" + scriptId + "'");
        }
        dialogues.add(scriptId);
    }
}
