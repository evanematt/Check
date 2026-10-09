package com.lewandivka.core.tools;

import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.story.CampaignDirector;
import com.lewandivka.core.story.CampaignDirector.Rule;
import com.lewandivka.core.text.QuestTexts;
import com.lewandivka.core.text.QuestTexts.StepText;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code gradle -p core dumpQuestFlow} fills the table of docs/QUEST_FLOW.md from the real story table: every rule of the
 * {@link CampaignDirector}, its step texts and what the rule hands out when it fires (the effects are recorded by running
 * them against a recording {@link CampaignDirector.Effects}). The prose of the document is docs/QUEST_FLOW.template.md.
 */
public final class QuestFlowDump {

    private QuestFlowDump() {
    }

    private static final class Recorder implements CampaignDirector.Effects {
        final List<String> out = new ArrayList<>();

        @Override
        public void entered(QuestStep step) {
        }

        @Override
        public void counter(int value, int max) {
        }

        @Override
        public void giveEveryone(String item, int count) {
            out.add("gives " + count + "x `" + item + "`");
        }

        @Override
        public void unlock(Ability ability) {
            out.add("unlocks ability **" + ability.key() + "**");
        }

        @Override
        public void ringFragment() {
            out.add("restores a ring fragment");
        }

        @Override
        public void dialogue(String scriptId) {
            out.add("dialogue `" + scriptId + "`");
        }

        @Override
        public void spawnNpc(String npc) {
            out.add("spawns `" + npc + "`");
        }

        @Override
        public void portal(boolean active) {
            out.add(active ? "opens the coloured portal" : "closes the portal");
        }

        @Override
        public void unlockStructure(String structure) {
            out.add("unlocks `" + structure + "`");
        }

        @Override
        public void cinematic(String id) {
            out.add("cinematic `" + id + "`");
        }

        @Override
        public void toast(String langKey) {
            out.add("message `" + langKey + "`");
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace("|", "/").replace("\n", " ");
    }

    public static void main(String[] args) throws Exception {
        File template = new File(System.getProperty("template", "../docs/QUEST_FLOW.template.md"));
        File out = new File(System.getProperty("out", "../docs/QUEST_FLOW.md"));
        StringBuilder table = new StringBuilder();
        table.append("| # | Step | Stage | Objective (uk) | Objective (en) | Needs | Completed by | Then |\n");
        table.append("|---|------|-------|----------------|----------------|-------|--------------|------|\n");
        int n = 0;
        for (QuestStep step : QuestStep.ordered()) {
            StepText t = QuestTexts.of(step);
            Rule rule = CampaignDirector.ruleFor(step);
            String completedBy = rule == null ? "(end of the campaign)" : "`" + rule.event() + "`" + (rule.counted() ? " x" + step.counterMax : "");
            Recorder rec = new Recorder();
            if (rule != null) {
                rule.effect().accept(rec);
            }
            String then = rule == null ? "" : "-> `" + rule.to().key() + "`" + (rec.out.isEmpty() ? "" : "; " + String.join("; ", rec.out));
            table.append(String.format("| %d | `%s` | %s | %s | %s | %s | %s | %s |%n", ++n, step.key(), step.stage.key(),
                    clean(t == null ? "" : t.objectiveUk()), clean(t == null ? "" : t.objectiveEn()),
                    step.counterMax > 0 ? String.valueOf(step.counterMax) : "", completedBy, clean(then)));
        }
        String text = Files.readString(template.toPath(), StandardCharsets.UTF_8).replace("{{TABLE}}", table.toString());
        out.getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8)) {
            w.print(text);
        }
        System.out.println("wrote " + out + " (" + n + " steps)");
    }
}
