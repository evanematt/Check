package com.lewandivka.core.quest;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.WorldProgress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decides which quest-critical items every participant (or the party as a whole) must hold at the
 * current campaign step, and what has to be handed out again when something went missing.
 *
 * <p>This is the "never unwinnable" safety net: death, lava, despawn, {@code /clear}, a
 * disconnect or a dimension change can all lose an item, and the glue code simply asks the
 * ledger what to restore. The ledger itself is pure and unit tested.</p>
 */
public final class QuestItemLedger {

    private QuestItemLedger() {
    }

    /**
     * @param item      registry name without namespace
     * @param count     how many are needed
     * @param perPlayer true: every participant needs {@code count}; false: the party needs {@code count} in total
     */
    public record Need(String item, int count, boolean perPlayer) {
        static Need each(String item, int count) {
            return new Need(item, count, true);
        }

        static Need party(String item, int count) {
            return new Need(item, count, false);
        }
    }

    public record Grant(UUID player, String item, int count) {
    }

    public static final String FLAG_KETTLE_PLACED = "kettle.placed";
    public static final String FLAG_PACKAGE_PLACED = "package.placed";
    public static final String FLAG_PEDESTAL_KETTLE = "pedestal.kettle";
    public static final String FLAG_PEDESTAL_PACKAGE = "pedestal.package";
    public static final String FLAG_PEDESTAL_COMPOSTER = "pedestal.composter";
    public static final String FLAG_PEDESTAL_TOKEN = "pedestal.token";
    public static final String COUNTER_WINGS_DONE = "rg.wings_done";
    public static final String COUNTER_BATTERIES_INSTALLED = "rg.batteries_installed";

    /** What the party must have at the current step of the given world. */
    public static List<Need> needs(WorldProgress w) {
        QuestStep s = w.step();
        List<Need> out = new ArrayList<>();
        out.add(Need.each(QuestItems.NOTEBOOK, 1));

        // --- paper trail of Act 1 ---
        if (s == QuestStep.DEBTOR_NOTE) {
            out.add(Need.party(QuestItems.DEBTOR_NOTE, 1));
        }
        if (s == QuestStep.GARAGE_FIND) {
            out.add(Need.party(QuestItems.GARAGE_NOTE, 1));
        }
        if (s == QuestStep.TRAM_WAIT) {
            out.add(Need.party(QuestItems.TRAM_NOTE, 1));
        }

        // --- magic kettle: from its reward until it sits on the base pedestal ---
        if (s.isAtLeast(QuestStep.KETTLE_TEST) && !s.isAfter(QuestStep.BASE_PEDESTALS)
                && !w.flag(FLAG_KETTLE_PLACED) && !w.flag(FLAG_PEDESTAL_KETTLE)) {
            out.add(Need.party(QuestItems.KETTLE, 1));
        }

        // --- package: carried during the escape, symbolic copy for the base pedestal ---
        if (s == QuestStep.GARAGE_ESCAPE && !w.flag(FLAG_PACKAGE_PLACED)) {
            out.add(Need.party(QuestItems.PACKAGE, 1));
        }
        if (s.isAtLeast(QuestStep.BASE_WAKE) && !s.isAfter(QuestStep.BASE_PEDESTALS) && !w.flag(FLAG_PEDESTAL_PACKAGE)) {
            out.add(Need.party(QuestItems.PACKAGE, 1));
        }

        // --- composter: reward of the last tram, needed again at the sky depot ---
        if (s.isAtLeast(QuestStep.TRAM_REPORT) && !s.isAfter(QuestStep.SKY_BOSS)) {
            out.add(Need.each(QuestItems.COMPOSTER, 1));
        }

        // --- district token for the pedestal ---
        if (s.isAtLeast(QuestStep.BASE_WAKE) && !s.isAfter(QuestStep.BASE_PEDESTALS) && !w.flag(FLAG_PEDESTAL_TOKEN)) {
            out.add(Need.party(QuestItems.TOKEN, 1));
        }

        // --- chroma tablet: one per participant (consumed ones are filtered by the caller) ---
        if (s == QuestStep.CHROMA_TAKE) {
            out.add(Need.each(QuestItems.TABLET, 1));
        }

        // --- compass for the whole of Act 2 until the final fight ---
        if (s.isAtLeast(QuestStep.RG_TRAVEL) && !s.isAfter(QuestStep.FINAL_FIGHT)) {
            out.add(Need.each(QuestItems.COMPASS, 1));
        }

        // --- lift batteries: earned by wings, spent at the hub socket ---
        if (s == QuestStep.RG_WINGS || s == QuestStep.RG_BOSS) {
            int owed = w.counter(COUNTER_WINGS_DONE) - w.counter(COUNTER_BATTERIES_INSTALLED);
            if (owed > 0) {
                out.add(Need.party(QuestItems.BATTERY, owed));
            }
        }
        return out;
    }

    /**
     * Computes what to hand out.
     *
     * @param needs       from {@link #needs(WorldProgress)}
     * @param inventories participating online players mapped to the quest-item counts they currently hold,
     *                    in a stable order (the first player receives party-level restorations)
     */
    public static List<Grant> missing(List<Need> needs, Map<UUID, Map<String, Integer>> inventories) {
        List<Grant> grants = new ArrayList<>();
        if (inventories.isEmpty()) {
            return grants;
        }
        for (Need n : needs) {
            if (n.perPlayer()) {
                for (Map.Entry<UUID, Map<String, Integer>> e : inventories.entrySet()) {
                    int have = e.getValue().getOrDefault(n.item(), 0);
                    if (have < n.count()) {
                        grants.add(new Grant(e.getKey(), n.item(), n.count() - have));
                    }
                }
            } else {
                int total = 0;
                for (Map<String, Integer> inv : inventories.values()) {
                    total += inv.getOrDefault(n.item(), 0);
                }
                if (total < n.count()) {
                    UUID first = new LinkedHashMap<>(inventories).keySet().iterator().next();
                    grants.add(new Grant(first, n.item(), n.count() - total));
                }
            }
        }
        return grants;
    }
}
