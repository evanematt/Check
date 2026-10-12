package com.lewandivka.core;

import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.CampaignModel;
import com.lewandivka.core.campaign.CampaignStage;
import com.lewandivka.core.campaign.EncounterRecord;
import com.lewandivka.core.campaign.PlayerProgress;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.Reputation;
import com.lewandivka.core.campaign.WorldProgress;
import com.lewandivka.core.data.MapStore;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignModelTest {

    @Test
    void stageIdsAreStableAndUnique() {
        Set<Integer> ids = new HashSet<>();
        for (CampaignStage s : CampaignStage.values()) {
            assertTrue(ids.add(s.id), "duplicate stage id " + s.id);
            assertEquals(s, CampaignStage.byId(s.id));
        }
        assertEquals(17, CampaignStage.values().length);
        assertEquals(0, CampaignStage.PROLOGUE.id);
        assertEquals(16, CampaignStage.POSTGAME.id);
    }

    @Test
    void everyStageHasAtLeastOneStepAndStepsAreOrdered() {
        for (CampaignStage s : CampaignStage.values()) {
            assertNotNull(QuestStep.firstOf(s), "no step for " + s);
        }
        List<QuestStep> ordered = QuestStep.ordered();
        for (int i = 1; i < ordered.size(); i++) {
            assertTrue(ordered.get(i - 1).order < ordered.get(i).order, "order must be strictly increasing");
            assertTrue(ordered.get(i - 1).stage.id <= ordered.get(i).stage.id, "stage must never decrease along the steps");
        }
        Set<Integer> ids = new HashSet<>();
        for (QuestStep s : QuestStep.values()) {
            assertTrue(ids.add(s.id), "duplicate step id " + s.id);
        }
    }

    @Test
    void stepCanOnlyAdvanceForward() {
        WorldProgress w = new WorldProgress();
        assertEquals(QuestStep.EXPLORE_DISTRICT, w.step());
        assertTrue(w.advanceTo(QuestStep.COLLECT_TOKENS));
        assertFalse(w.advanceTo(QuestStep.EXPLORE_DISTRICT), "must not go backwards");
        assertFalse(w.advanceTo(QuestStep.COLLECT_TOKENS), "same step is a no-op");
        assertEquals(CampaignStage.FIRST_NIGHT, w.stage());
        w.forceStep(QuestStep.EXPLORE_DISTRICT);
        assertEquals(QuestStep.EXPLORE_DISTRICT, w.step(), "admin override may go back");
    }

    @Test
    void stepCounterResetsWhenTheStepChanges() {
        WorldProgress w = new WorldProgress();
        w.addStepCounter(3);
        assertEquals(3, w.stepCounter());
        w.advanceTo(QuestStep.COLLECT_TOKENS);
        assertEquals(0, w.stepCounter());
    }

    @Test
    void fullRoundTripPreservesEverything() {
        CampaignModel m = new CampaignModel();
        WorldProgress w = m.world();
        w.setStarted(true);
        w.forceStep(QuestStep.SH_CHINAZIK);
        w.setStepCounter(2);
        w.setFlag("kettle.placed");
        w.defeatBoss("garage_king");
        w.unlockStructure("shelter");
        w.markCinematicSeen("chroma_transition");
        w.restoreRingFragment();
        w.setPortalActive(true);
        w.setNpcState("shlahbaum", "kiosk");
        EncounterRecord e = w.encounter("garage13");
        e.setStatus(EncounterRecord.Status.ACTIVE);
        e.setCheckpoint(2);
        e.setFlag("panel_a");

        UUID id = UUID.randomUUID();
        PlayerProgress p = m.player(id);
        p.setLastName("Eva");
        p.grant(Ability.DASH);
        p.addRepPoints(4);
        p.addTokens(7);
        p.discoverSecret("hidden_cat_3");
        p.collect("cat_07");
        p.completeTutorial("dash");
        p.setOptionalProgress("seeds_vanish", 2);
        p.addHistory("explore_district");
        p.setParticipating(false);

        MapStore store = new MapStore();
        m.write(store);
        CampaignModel r = CampaignModel.read(store);

        assertEquals(CampaignModel.CURRENT_VERSION, r.dataVersion());
        assertTrue(r.world().started());
        assertEquals(QuestStep.SH_CHINAZIK, r.world().step());
        assertEquals(2, r.world().stepCounter());
        assertTrue(r.world().flag("kettle.placed"));
        assertTrue(r.world().bossDefeated("garage_king"));
        assertTrue(r.world().structureUnlocked("shelter"));
        assertTrue(r.world().cinematicSeen("chroma_transition"));
        assertEquals(1, r.world().ringFragments());
        assertTrue(r.world().portalActive());
        assertEquals("kiosk", r.world().npcState("shlahbaum", ""));
        EncounterRecord re = r.world().encounter("garage13");
        assertEquals(EncounterRecord.Status.ACTIVE, re.status());
        assertEquals(2, re.checkpoint());
        assertTrue(re.flag("panel_a"));

        PlayerProgress rp = r.player(id);
        assertEquals("Eva", rp.lastName());
        assertTrue(rp.has(Ability.DASH));
        assertFalse(rp.has(Ability.GLIDER));
        assertEquals(4, rp.repPoints());
        assertEquals(7, rp.tokensTotal());
        assertTrue(rp.hasSecret("hidden_cat_3"));
        assertTrue(rp.hasCollectible("cat_07"));
        assertTrue(rp.tutorialDone("dash"));
        assertEquals(2, rp.optionalProgress("seeds_vanish"));
        assertEquals(List.of("explore_district"), rp.history());
        assertFalse(rp.participating());
    }

    @Test
    void abilitiesPersistAcrossReloadsAndSurviveRevokeOfOthers() {
        CampaignModel m = new CampaignModel();
        UUID id = UUID.randomUUID();
        PlayerProgress p = m.player(id);
        for (Ability a : Ability.values()) {
            assertTrue(p.grant(a));
            assertFalse(p.grant(a), "granting twice is a no-op");
        }
        p.revoke(Ability.SPRING_INSOLES);
        MapStore s = new MapStore();
        m.write(s);
        PlayerProgress r = CampaignModel.read(s).player(id);
        assertTrue(r.has(Ability.DASH));
        assertFalse(r.has(Ability.SPRING_INSOLES));
        assertTrue(r.has(Ability.GLIDER));
        assertEquals(Ability.DASH.bit() | Ability.GLIDER.bit(), r.abilityMask());
    }

    @Test
    void legacySaveWithoutVersionStillLoadsAndIsUpgraded() {
        MapStore legacy = new MapStore();
        legacy.child("world").putInt("step", QuestStep.GARAGE_PANELS.id);
        CampaignModel m = CampaignModel.read(legacy);
        assertEquals(QuestStep.GARAGE_PANELS, m.world().step());
        MapStore out = new MapStore();
        m.write(out);
        assertEquals(CampaignModel.CURRENT_VERSION, out.getInt("dataVersion", -1));
    }

    @Test
    void corruptedPlayerKeyDoesNotBreakTheCampaign() {
        MapStore s = new MapStore();
        s.putInt("dataVersion", 1);
        s.child("players").child("not-a-uuid").putInt("rep", 3);
        UUID ok = UUID.randomUUID();
        s.child("players").child(ok.toString()).putInt("rep", 5);
        CampaignModel m = CampaignModel.read(s);
        assertEquals(5, m.player(ok).repPoints());
    }

    @Test
    void ringFragmentsAreCapped() {
        WorldProgress w = new WorldProgress();
        for (int i = 0; i < 10; i++) {
            w.restoreRingFragment();
        }
        assertEquals(WorldProgress.MAX_RING_FRAGMENTS, w.ringFragments());
    }

    @Test
    void reputationFollowsTheStoryAndPointsNeverReachTheTopRank() {
        assertEquals(Reputation.STRANGER, Reputation.forStep(QuestStep.EXPLORE_DISTRICT));
        assertEquals(Reputation.ACQUAINTANCE, Reputation.forStep(QuestStep.PLACE_KIOSK));
        assertEquals(Reputation.LOCAL, Reputation.forStep(QuestStep.GARAGE_FIND));
        assertEquals(Reputation.DISTRICT, Reputation.forStep(QuestStep.GARAGE_DELIVER));
        assertEquals(Reputation.LOCAL, Reputation.fromPoints(1000), "points alone cap at LOCAL");
        assertEquals(Reputation.LOCAL, Reputation.effective(QuestStep.EXPLORE_DISTRICT, 9));
        assertEquals(Reputation.DISTRICT, Reputation.effective(QuestStep.POST_FREE, 0));
    }

    @Test
    void abilityLookups() {
        assertEquals(Ability.DASH, Ability.byBoss("garage_king"));
        assertEquals(Ability.SPRING_INSOLES, Ability.byBoss("lady_vortex"));
        assertEquals(Ability.GLIDER, Ability.byBoss("conductor"));
        assertEquals(Ability.GLIDER, Ability.byKey("GLIDER"));
    }

    @Test
    void encounterResetClearsEverything() {
        EncounterRecord e = new EncounterRecord();
        e.setStatus(EncounterRecord.Status.COMPLETE);
        e.setCheckpoint(4);
        e.setFlag("x");
        e.resetAll();
        assertEquals(EncounterRecord.Status.IDLE, e.status());
        assertEquals(0, e.checkpoint());
        assertTrue(e.flags().isEmpty());
    }
}
