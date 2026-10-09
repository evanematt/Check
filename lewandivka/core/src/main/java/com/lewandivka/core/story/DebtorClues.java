package com.lewandivka.core.story;

import java.util.List;

/**
 * The five traces of the Debtor, in the order of the notebook hint: tea package, bench, playground, rubbish, garage roof.
 * The ids are marker ids of the district plan; the index selects the language key of the "where is the last one" message.
 */
public final class DebtorClues {

    private DebtorClues() {
    }

    public static final List<String> MARKERS = List.of(
            "clue_tea:clue", "clue_bench:clue", "clue_playground:clue", "clue_trash:clue", "garage13:clue_roof");

    /** The world flag that records a found trace. */
    public static String flag(String markerId) {
        return "clue." + markerId;
    }

    /** Language key of the message that tells where the one remaining trace is. */
    public static String lastKey(int index) {
        return "message.lewandivka.debtor.last." + index;
    }
}
