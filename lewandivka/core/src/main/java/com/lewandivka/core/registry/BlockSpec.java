package com.lewandivka.core.registry;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the game and the resource generator need to know about one mod block.
 *
 * <p>{@link #behaviour} selects the Java class in the game glue, {@link #model} selects the model template of the
 * resource generator, {@link #visual} lists the properties that change the texture.</p>
 */
public final class BlockSpec {

    /** What the block does when used or touched (selects the Java block class). */
    public enum Behaviour {
        /** No behaviour: decoration or level geometry. */
        DECOR,
        /** Right click is dispatched to the encounter that owns the marker at this position. */
        STATION,
        /** Quest gate: unbreakable, opened and closed by the gate service. */
        GATE,
        /** Gate that also opens when a dashing player hits it. */
        DASH_GATE,
        /** Walking on it launches the player (needs the Spring Insoles ability for the full effect). */
        SPRING,
        /** Lore note: right click reads it and records it in the notebook. */
        NOTE,
        /** Supply stash: right click gives the loot of the marker at this position once. */
        STASH,
        /** Platform that loses colour and collapses (stage property). */
        CRUMBLING,
        /** The coloured portal pane: entering it teleports the player. */
        PORTAL,
        /** Slow-flowing animated water-like pane. */
        FLOW,
        /** The player-placed kiosk. */
        KIOSK,
        /** The package placed on the ground (quiet while it lies there). */
        PACKAGE,
        /** The magic kettle placed on the ground. */
        KETTLE,
        /** Artifact pedestal in the base. */
        PEDESTAL,
        /** Checkpoint lamp: touching it stores the checkpoint. */
        CHECKPOINT,
        /** Hazard that is never solid and never breakable (grey void). */
        VOID
    }

    /** Model template of the resource generator. */
    public enum Model {
        CUBE, COLUMN, ORIENTABLE, PLATE, PAD, CARPET, CROSS, CLUSTER, ANIMATED, PORTAL, PEDESTAL, LAMP, SMALL
    }

    public final String id;
    public final Behaviour behaviour;
    public final Model model;
    public final List<PropSpec> props = new ArrayList<>();
    /** Names of the properties that select the texture (all of them are enumerated by the generator). */
    public final List<String> visual = new ArrayList<>();
    public String nameUk;
    public String nameEn;
    public int light;
    /** Name of a boolean property: the block only emits {@link #light} while it is true (null = always). */
    public String lightProp;
    public String sound = "stone";
    public boolean unbreakable = true;
    /** Players walk through the block (no collision shape). */
    public boolean passable;
    public boolean translucent;
    public boolean cutout;
    /** True for decoration that has a block item in the creative tab. */
    public boolean inTab = true;
    public String note = "";

    private BlockSpec(String id, Behaviour behaviour, Model model) {
        this.id = id;
        this.behaviour = behaviour;
        this.model = model;
    }

    public static BlockSpec of(String id, Behaviour behaviour, Model model) {
        return new BlockSpec(id, behaviour, model);
    }

    public BlockSpec name(String uk, String en) {
        this.nameUk = uk;
        this.nameEn = en;
        return this;
    }

    public BlockSpec prop(PropSpec p) {
        props.add(p);
        return this;
    }

    public BlockSpec visual(String... names) {
        visual.addAll(List.of(names));
        return this;
    }

    public BlockSpec light(int level) {
        this.light = level;
        return this;
    }

    public BlockSpec light(int level, String boolProp) {
        this.light = level;
        this.lightProp = boolProp;
        return this;
    }

    public BlockSpec sound(String group) {
        this.sound = group;
        return this;
    }

    public BlockSpec passable() {
        this.passable = true;
        return this;
    }

    public BlockSpec translucent() {
        this.translucent = true;
        return this;
    }

    public BlockSpec cutout() {
        this.cutout = true;
        return this;
    }

    public BlockSpec breakable() {
        this.unbreakable = false;
        return this;
    }

    public BlockSpec hiddenInTab() {
        this.inTab = false;
        return this;
    }

    public PropSpec prop(String name) {
        for (PropSpec p : props) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        return null;
    }

    /** Number of block states (product of all property value counts). */
    public int stateCount() {
        int n = 1;
        for (PropSpec p : props) {
            n *= p.count();
        }
        return n;
    }

    public String nameKey() {
        return "block.lewandivka." + id;
    }

    /**
     * Validates a block key such as {@code lewandivka:lore_note[note=3,facing=south]} against this spec.
     *
     * @return null if valid, otherwise the problem
     */
    public String check(String properties) {
        if (properties.isEmpty()) {
            return null;
        }
        for (String kv : properties.split(",")) {
            int eq = kv.indexOf('=');
            if (eq < 0) {
                return "malformed property '" + kv + "'";
            }
            PropSpec p = prop(kv.substring(0, eq));
            if (p == null) {
                return "unknown property '" + kv.substring(0, eq) + "'";
            }
            if (!p.allows(kv.substring(eq + 1))) {
                return "value '" + kv.substring(eq + 1) + "' is not allowed for '" + p.name() + "'";
            }
        }
        return null;
    }
}
