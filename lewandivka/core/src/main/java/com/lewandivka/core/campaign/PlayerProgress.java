package com.lewandivka.core.campaign;

import com.lewandivka.core.data.DataStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything that belongs to one player rather than to the shared world:
 * abilities, reputation points, discovered secrets, collectibles, tutorials, optional quests
 * and the notebook history. Pure data; the server owns the single authoritative copy.
 */
public final class PlayerProgress {

    /** Maximum entries of the notebook history that are kept (oldest are dropped). */
    public static final int HISTORY_LIMIT = 80;

    public final UUID id;
    private String lastName = "";
    private final EnumSet<Ability> abilities = EnumSet.noneOf(Ability.class);
    private int repPoints;
    private int tokensTotal;
    private boolean participating = true;
    private final Set<String> secrets = new LinkedHashSet<>();
    private final Set<String> collectibles = new LinkedHashSet<>();
    private final Set<String> tutorials = new LinkedHashSet<>();
    private final Map<String, Integer> optional = new LinkedHashMap<>();
    private final List<String> history = new ArrayList<>();

    public PlayerProgress(UUID id) {
        this.id = id;
    }

    // ---- identity ----

    public String lastName() {
        return lastName;
    }

    public void setLastName(String name) {
        this.lastName = name == null ? "" : name;
    }

    /** Whether the player takes part in the campaign party (admins can switch a spectator off). */
    public boolean participating() {
        return participating;
    }

    public void setParticipating(boolean value) {
        this.participating = value;
    }

    // ---- abilities ----

    public boolean has(Ability a) {
        return abilities.contains(a);
    }

    /** @return true when the ability was newly granted. */
    public boolean grant(Ability a) {
        return abilities.add(a);
    }

    public boolean revoke(Ability a) {
        return abilities.remove(a);
    }

    public Set<Ability> abilities() {
        return Collections.unmodifiableSet(abilities);
    }

    public int abilityMask() {
        int mask = 0;
        for (Ability a : abilities) {
            mask |= a.bit();
        }
        return mask;
    }

    // ---- reputation ----

    public int repPoints() {
        return repPoints;
    }

    public void addRepPoints(int n) {
        repPoints = Math.max(0, repPoints + n);
    }

    public int tokensTotal() {
        return tokensTotal;
    }

    public void addTokens(int n) {
        tokensTotal = Math.max(0, tokensTotal + n);
    }

    // ---- sets ----

    public boolean discoverSecret(String id) {
        return secrets.add(id);
    }

    public boolean hasSecret(String id) {
        return secrets.contains(id);
    }

    public Set<String> secrets() {
        return Collections.unmodifiableSet(secrets);
    }

    public boolean collect(String id) {
        return collectibles.add(id);
    }

    public boolean hasCollectible(String id) {
        return collectibles.contains(id);
    }

    public Set<String> collectibles() {
        return Collections.unmodifiableSet(collectibles);
    }

    public boolean completeTutorial(String id) {
        return tutorials.add(id);
    }

    public boolean tutorialDone(String id) {
        return tutorials.contains(id);
    }

    // ---- optional quests ----

    public int optionalProgress(String quest) {
        return optional.getOrDefault(quest, 0);
    }

    public void setOptionalProgress(String quest, int value) {
        optional.put(quest, value);
    }

    public Map<String, Integer> optionalQuests() {
        return Collections.unmodifiableMap(optional);
    }

    // ---- history ----

    public void addHistory(String stepKey) {
        if (!history.contains(stepKey)) {
            history.add(stepKey);
            while (history.size() > HISTORY_LIMIT) {
                history.remove(0);
            }
        }
    }

    public List<String> history() {
        return Collections.unmodifiableList(history);
    }

    // ---- persistence ----

    public void write(DataStore s) {
        s.putString("name", lastName);
        s.putInt("abilities", abilityMask());
        s.putInt("rep", repPoints);
        s.putInt("tokens", tokensTotal);
        s.putBool("participating", participating);
        s.putStrings("secrets", secrets);
        s.putStrings("collectibles", collectibles);
        s.putStrings("tutorials", tutorials);
        s.putStrings("history", history);
        DataStore opt = s.child("optional");
        for (String k : new ArrayList<>(opt.keys())) {
            opt.remove(k);
        }
        for (Map.Entry<String, Integer> e : optional.entrySet()) {
            opt.putInt(e.getKey(), e.getValue());
        }
    }

    public static PlayerProgress read(UUID id, DataStore s) {
        PlayerProgress p = new PlayerProgress(id);
        p.lastName = s.getString("name", "");
        int mask = s.getInt("abilities", 0);
        for (Ability a : Ability.values()) {
            if ((mask & a.bit()) != 0) {
                p.abilities.add(a);
            }
        }
        p.repPoints = s.getInt("rep", 0);
        p.tokensTotal = s.getInt("tokens", 0);
        p.participating = s.getBool("participating", true);
        p.secrets.addAll(s.getStrings("secrets"));
        p.collectibles.addAll(s.getStrings("collectibles"));
        p.tutorials.addAll(s.getStrings("tutorials"));
        p.history.addAll(s.getStrings("history"));
        if (s.hasChild("optional")) {
            DataStore opt = s.child("optional");
            for (String k : opt.keys()) {
                p.optional.put(k, opt.getInt(k, 0));
            }
        }
        return p;
    }
}
