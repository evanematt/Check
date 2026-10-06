package com.lewandivka.core.campaign;

import com.lewandivka.core.data.DataStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * World-wide (shared) campaign state: the current step, bosses defeated, unlocked structures,
 * restored ring fragments, portal state, seen cinematics, NPC states and encounter records.
 */
public final class WorldProgress {

    public static final int MAX_RING_FRAGMENTS = 4;

    private QuestStep step = QuestStep.EXPLORE_DISTRICT;
    private boolean started;
    private final Set<String> flags = new LinkedHashSet<>();
    private final Map<String, Integer> counters = new LinkedHashMap<>();
    private final Set<String> bosses = new LinkedHashSet<>();
    private final Set<String> structures = new LinkedHashSet<>();
    private final Set<String> cinematics = new LinkedHashSet<>();
    private final Map<String, String> npcStates = new LinkedHashMap<>();
    private final Map<String, EncounterRecord> encounters = new LinkedHashMap<>();
    private int ringFragments;
    private boolean portalActive;

    // ---- step / stage ----

    public QuestStep step() {
        return step;
    }

    public CampaignStage stage() {
        return step.stage;
    }

    public boolean started() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    /**
     * Moves the campaign forward. Never moves backwards, so late or duplicated events (two players
     * finishing the same objective in the same tick, a reconnect replaying a packet ...) are harmless.
     *
     * @return true when the step actually changed
     */
    public boolean advanceTo(QuestStep target) {
        if (target == null || !target.isAfter(step)) {
            return false;
        }
        applyStep(target);
        return true;
    }

    /** Admin override that may also move backwards. Resets the counter of the target step. */
    public void forceStep(QuestStep target) {
        applyStep(target);
    }

    private void applyStep(QuestStep target) {
        step = target;
        counters.remove(stepCounterKey(target));
    }

    private static String stepCounterKey(QuestStep s) {
        return "step." + s.key();
    }

    /** Progress counter of the current step (e.g. clues found 3/5). */
    public int stepCounter() {
        return counter(stepCounterKey(step));
    }

    public int addStepCounter(int delta) {
        return addCounter(stepCounterKey(step), delta);
    }

    public void setStepCounter(int value) {
        setCounter(stepCounterKey(step), value);
    }

    // ---- flags & counters ----

    public boolean flag(String f) {
        return flags.contains(f);
    }

    public boolean setFlag(String f) {
        return flags.add(f);
    }

    public boolean clearFlag(String f) {
        return flags.remove(f);
    }

    public Set<String> flags() {
        return Collections.unmodifiableSet(flags);
    }

    public int counter(String key) {
        return counters.getOrDefault(key, 0);
    }

    public void setCounter(String key, int value) {
        counters.put(key, value);
    }

    public int addCounter(String key, int delta) {
        int v = Math.max(0, counter(key) + delta);
        counters.put(key, v);
        return v;
    }

    // ---- bosses, structures, cinematics ----

    public boolean defeatBoss(String id) {
        return bosses.add(id);
    }

    public boolean bossDefeated(String id) {
        return bosses.contains(id);
    }

    public Set<String> bosses() {
        return Collections.unmodifiableSet(bosses);
    }

    public boolean unlockStructure(String id) {
        return structures.add(id);
    }

    public boolean structureUnlocked(String id) {
        return structures.contains(id);
    }

    public boolean markCinematicSeen(String id) {
        return cinematics.add(id);
    }

    public boolean cinematicSeen(String id) {
        return cinematics.contains(id);
    }

    // ---- ring & portal ----

    public int ringFragments() {
        return ringFragments;
    }

    /** @return the new count; never exceeds {@link #MAX_RING_FRAGMENTS}. */
    public int restoreRingFragment() {
        ringFragments = Math.min(MAX_RING_FRAGMENTS, ringFragments + 1);
        return ringFragments;
    }

    public void setRingFragments(int n) {
        ringFragments = Math.max(0, Math.min(MAX_RING_FRAGMENTS, n));
    }

    public boolean portalActive() {
        return portalActive;
    }

    public void setPortalActive(boolean active) {
        this.portalActive = active;
    }

    // ---- NPC states ----

    public String npcState(String npc, String def) {
        return npcStates.getOrDefault(npc, def);
    }

    public void setNpcState(String npc, String state) {
        npcStates.put(npc, state);
    }

    // ---- encounters ----

    public EncounterRecord encounter(String id) {
        return encounters.computeIfAbsent(id, k -> new EncounterRecord());
    }

    public boolean hasEncounter(String id) {
        return encounters.containsKey(id);
    }

    public Set<String> encounterIds() {
        return Collections.unmodifiableSet(encounters.keySet());
    }

    // ---- persistence ----

    public void write(DataStore s) {
        s.putInt("step", step.id);
        s.putBool("started", started);
        s.putStrings("flags", flags);
        DataStore c = s.child("counters");
        for (String k : new ArrayList<>(c.keys())) {
            c.remove(k);
        }
        for (Map.Entry<String, Integer> e : counters.entrySet()) {
            c.putInt(e.getKey(), e.getValue());
        }
        s.putStrings("bosses", bosses);
        s.putStrings("structures", structures);
        s.putStrings("cinematics", cinematics);
        s.putInt("ring", ringFragments);
        s.putBool("portal", portalActive);
        DataStore n = s.child("npc");
        for (String k : new ArrayList<>(n.keys())) {
            n.remove(k);
        }
        for (Map.Entry<String, String> e : npcStates.entrySet()) {
            n.putString(e.getKey(), e.getValue());
        }
        DataStore enc = s.child("enc");
        for (String k : new ArrayList<>(enc.keys())) {
            enc.remove(k);
        }
        for (Map.Entry<String, EncounterRecord> e : encounters.entrySet()) {
            e.getValue().write(enc.child(e.getKey()));
        }
    }

    public static WorldProgress read(DataStore s) {
        WorldProgress w = new WorldProgress();
        w.step = QuestStep.byId(s.getInt("step", QuestStep.EXPLORE_DISTRICT.id));
        w.started = s.getBool("started", false);
        w.flags.addAll(s.getStrings("flags"));
        if (s.hasChild("counters")) {
            DataStore c = s.child("counters");
            for (String k : c.keys()) {
                w.counters.put(k, c.getInt(k, 0));
            }
        }
        w.bosses.addAll(s.getStrings("bosses"));
        w.structures.addAll(s.getStrings("structures"));
        w.cinematics.addAll(s.getStrings("cinematics"));
        w.ringFragments = Math.max(0, Math.min(MAX_RING_FRAGMENTS, s.getInt("ring", 0)));
        w.portalActive = s.getBool("portal", false);
        if (s.hasChild("npc")) {
            DataStore n = s.child("npc");
            for (String k : n.keys()) {
                w.npcStates.put(k, n.getString(k, ""));
            }
        }
        if (s.hasChild("enc")) {
            DataStore enc = s.child("enc");
            for (String k : enc.childKeys()) {
                w.encounters.put(k, EncounterRecord.read(enc.child(k)));
            }
        }
        return w;
    }
}
