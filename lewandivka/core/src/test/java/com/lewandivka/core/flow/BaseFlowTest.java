package com.lewandivka.core.flow;

import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.flow.dungeon.BaseFlow;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.story.Events;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseFlowTest {

    private static final UUID A = new UUID(0, 1);

    @Test
    void wakingUpGivesTheCompassAfterTheWakeDialogue() {
        FakeEnv env = new FakeEnv("base", 2);
        env.world.forceStep(QuestStep.BASE_WAKE);
        BaseFlow f = new BaseFlow(env);
        f.playerEntered(A);
        assertTrue(env.dialogues.contains("base_wake"));
        f.tick();
        assertEquals(0, env.eventCount(Events.BASE_COMPASS));
        env.advance(150);
        f.tick();
        assertEquals(1, env.eventCount(Events.BASE_COMPASS));
        f.tick();
        assertEquals(1, env.eventCount(Events.BASE_COMPASS), "only once");
    }

    @Test
    void aPartyThatWasCarriedIntoTheBaseBeforeTheStepBeganStillWakesUp() {
        // the transition teleports everybody into the base and moves the story to the waking up 60 ticks later:
        // nobody "enters" after that, yet the compass must come
        FakeEnv env = new FakeEnv("base", 2);
        env.world.forceStep(QuestStep.TRANSITION);
        BaseFlow f = new BaseFlow(env);
        f.playerEntered(A);
        f.tick();
        assertTrue(env.dialogues.isEmpty(), "nothing to say while the transition is still running");
        env.advance(60);
        env.world.forceStep(QuestStep.BASE_WAKE);
        f.tick();
        assertTrue(env.dialogues.contains("base_wake"));
        assertEquals(0, env.eventCount(Events.BASE_COMPASS));
        env.advance(150);
        f.tick();
        assertEquals(1, env.eventCount(Events.BASE_COMPASS));
        f.tick();
        assertEquals(1, env.eventCount(Events.BASE_COMPASS), "only once");
        assertEquals(1, env.dialogues.stream().filter("base_wake"::equals).count(), "the dialogue is not repeated");
    }

    @Test
    void pedestalsTakeTheMatchingArtifactAndNothingElse() {
        FakeEnv env = new FakeEnv("base", 3);
        env.world.forceStep(QuestStep.BASE_PEDESTALS);
        BaseFlow f = new BaseFlow(env);
        env.inventory.put(QuestItems.KETTLE, 1);
        env.inventory.put(QuestItems.TOKEN, 1);
        f.useWith("pedestal_package", A, QuestItems.KETTLE);
        assertFalse(env.record.flag("pedestal.package"), "the kettle does not belong on the package pedestal");
        assertEquals(1, env.inventory.get(QuestItems.KETTLE), "nothing was taken");
        f.useWith("pedestal_kettle", A, QuestItems.KETTLE);
        assertEquals("true", env.station("pedestal_kettle", "filled"));
        assertEquals(0, env.inventory.get(QuestItems.KETTLE));
        f.useWith("pedestal_kettle", A, QuestItems.KETTLE);
        assertEquals(1, env.eventCount(Events.BASE_PEDESTAL), "a filled pedestal counts once");
        f.useWith("pedestal_token", A, QuestItems.TOKEN);
        assertEquals(2, f.filled());
        assertFalse(f.complete());
    }

    @Test
    void theFourthArtifactCompletesThePortalAndRebuildRestoresThePedestals() {
        FakeEnv env = new FakeEnv("base", 1);
        BaseFlow f = new BaseFlow(env);
        for (String kind : BaseFlow.KINDS) {
            String item = switch (kind) {
                case "kettle" -> QuestItems.KETTLE;
                case "package" -> QuestItems.PACKAGE;
                case "composter" -> QuestItems.COMPOSTER;
                default -> QuestItems.TOKEN;
            };
            env.inventory.put(item, 1);
            f.useWith("pedestal_" + kind, A, item);
        }
        assertTrue(f.complete());
        assertEquals(4, env.eventCount(Events.BASE_PEDESTAL));
        FakeEnv after = new FakeEnv("base", 1);
        after.record.setFlag("pedestal.package");
        new BaseFlow(after).rebuild();
        assertEquals("true", after.station("pedestal_package", "filled"));
        assertEquals(null, after.station("pedestal_kettle", "filled"));
    }

    @Test
    void theEpilogueArrivalSpeaksAndReportsOnce() {
        FakeEnv env = new FakeEnv("base", 3);
        env.world.forceStep(QuestStep.EPI_RETURN);
        BaseFlow f = new BaseFlow(env);
        f.playerEntered(A);
        f.playerEntered(new UUID(0, 2));
        assertEquals(1, env.dialogues.stream().filter("epilogue_cats"::equals).count());
        env.advance(250);
        f.tick();
        assertEquals(1, env.eventCount(Events.EPILOGUE_BASE));
    }
}
