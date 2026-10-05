package ua.lewandivka;

import org.junit.jupiter.api.Test;
import ua.lewandivka.campaign.Campaign;
import ua.lewandivka.campaign.Stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignTest {
    @Test
    void stagesAreOrderedAndNamed() {
        assertEquals(17, Stage.values().length);
        assertEquals(Stage.PROLOGUE, Stage.values()[0]);
        assertEquals(Stage.POSTGAME, Stage.values()[Stage.values().length - 1]);
        for (Stage s : Stage.values()) {
            assertFalse(s.title.isBlank());
            assertFalse(s.objective.isBlank());
        }
    }

    @Test
    void syncWindowScalesWithPartySizeNeverFailsForSmallParties() {
        int solo = Campaign.windowFor(1);
        int duo = Campaign.windowFor(2);
        int trio = Campaign.windowFor(3);
        assertTrue(solo > duo && duo > trio, "менша група — більше часу");
        assertTrue(solo >= 1200, "соло має встигнути пройти послідовно");
        assertEquals(trio, Campaign.windowFor(5), "для груп > 3 беремо як для трьох");
    }
}
