package com.lewandivka.core;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.dialogue.DialogueScript;
import com.lewandivka.core.registry.AdvancementSpec;
import com.lewandivka.core.registry.EntitySpec;
import com.lewandivka.core.registry.ItemSpec;
import com.lewandivka.core.registry.ModAdvancements;
import com.lewandivka.core.registry.ModBlocks;
import com.lewandivka.core.registry.ModEntities;
import com.lewandivka.core.registry.ModItems;
import com.lewandivka.core.registry.ModSounds;
import com.lewandivka.core.registry.SoundSpec;
import com.lewandivka.core.structure.Blueprint;
import com.lewandivka.core.structure.Keys;
import com.lewandivka.core.text.DialogueBook;
import com.lewandivka.core.text.LoreNotes;
import com.lewandivka.core.text.QuestTexts;
import com.lewandivka.core.tools.SpecExport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Completeness checks of the registries and texts, and a guard that the exported JSON is up to date. */
class SpecTest {

    @Test
    void exportedSpecIsUpToDate() throws IOException {
        for (Map.Entry<String, String> e : SpecExport.render().entrySet()) {
            Path p = Path.of("..", "tools", "data", "spec", e.getKey());
            assertTrue(Files.exists(p), "missing " + p + " (run: gradle -p core exportSpec)");
            assertEquals(e.getValue() + "\n", Files.readString(p, StandardCharsets.UTF_8), p + " is stale (run: gradle -p core exportSpec)");
        }
    }

    @Test
    void everyQuestStepHasTexts() {
        for (QuestStep s : QuestStep.values()) {
            QuestTexts.StepText t = QuestTexts.of(s);
            assertTrue(t != null, "no text for " + s);
            assertTrue(!t.titleUk().isBlank() && !t.titleEn().isBlank() && !t.objectiveEn().isBlank(), s.toString());
            if (s.counterMax > 0) {
                assertTrue(t.objectiveUk().contains("%s/%s") && t.objectiveEn().contains("%s/%s"), s + " must show its counter");
            }
        }
    }

    @Test
    void everyPlacedNoteHasText() {
        Set<Integer> used = new HashSet<>();
        for (String key : RegistryTestAccess.allKeys()) {
            if (Keys.blockId(key).equals("lewandivka:lore_note")) {
                int a = key.indexOf("note=") + 5;
                int b = key.indexOf(',', a);
                used.add(Integer.parseInt(key.substring(a, b)));
            }
        }
        List<Integer> missing = new ArrayList<>();
        for (int i : used) {
            if (LoreNotes.of(i) == null) {
                missing.add(i);
            }
        }
        assertEquals(List.of(), missing);
        List<Integer> unused = new ArrayList<>();
        for (int i : LoreNotes.all().keySet()) {
            if (!used.contains(i)) {
                unused.add(i);
            }
        }
        assertEquals(List.of(), unused, "notes with text that no structure places");
    }

    @Test
    void dialogueScriptsAreWellFormed() {
        for (DialogueScript s : DialogueBook.scripts().values()) {
            assertTrue(!s.lines().isEmpty(), s.id());
            s.lines().forEach(l -> {
                assertTrue(DialogueBook.text().containsKey(l.textKey()), "missing text " + l.textKey());
                assertTrue(DialogueBook.text().containsKey(l.speakerKey()), "missing speaker " + l.speakerKey());
                assertTrue(l.textKey().length() < 100);
            });
            s.choices().forEach(c -> assertTrue(DialogueBook.text().containsKey(c.textKey())));
            for (DialogueBook.Line line : s.lines().stream().map(l -> DialogueBook.text().get(l.textKey())).toList()) {
                assertTrue(line.uk().length() <= 90, "Ukrainian line too long in " + s.id() + ": " + line.uk());
            }
        }
    }

    @Test
    void itemsEntitiesSoundsAndAdvancementsAreConsistent() throws IOException {
        Set<String> ids = new HashSet<>();
        for (ItemSpec i : ModItems.ALL) {
            assertTrue(ids.add(i.id), i.id);
            assertTrue(i.nameUk != null && i.nameEn != null);
        }
        Set<String> entityIds = new HashSet<>();
        for (EntitySpec e : ModEntities.ALL) {
            assertTrue(entityIds.add(e.id), e.id);
            assertTrue(e.nameUk != null && e.nameEn != null && (e.model != null || e.role == EntitySpec.Role.MARKER), e.id);
        }
        for (String cat : List.of("chinazik", "metadonna")) {
            EntitySpec e = ModEntities.byId(cat);
            for (String a : List.of("idle", "sit", "walk", "run", "look", "sniff", "eat", "scratch", "sleep", "alert", "point")) {
                assertTrue(e.animations.contains(a), cat + " lacks animation " + a);
            }
        }
        Set<String> sounds = new HashSet<>();
        for (SoundSpec s : ModSounds.ALL) {
            assertTrue(sounds.add(s.id()));
            assertTrue(s.variants() >= 1);
        }
        Path p = Path.of("..", "tools", "data", "mc1201-registry.json");
        JsonObject vanilla = new Gson().fromJson(Files.readString(p, StandardCharsets.UTF_8), JsonObject.class);
        Set<String> parents = new HashSet<>();
        for (AdvancementSpec a : ModAdvancements.ALL) {
            parents.add(a.id());
            String icon = a.icon();
            String name = icon.substring(icon.indexOf(':') + 1);
            boolean ok = icon.startsWith("lewandivka:")
                    ? ModItems.byId(name) != null || ModBlocks.byId(name) != null
                    : vanilla.getAsJsonArray("items").contains(new Gson().toJsonTree(name));
            assertTrue(ok, "advancement icon " + icon);
        }
        for (AdvancementSpec a : ModAdvancements.ALL) {
            assertTrue(a.parent() == null || parents.contains(a.parent()), "parent of " + a.id());
        }
        assertEquals(1, ModAdvancements.ALL.stream().filter(a -> a.parent() == null).count(), "exactly one root advancement");
    }

    /** Test-visible access to the palette collection of {@link RegistryTest}. */
    static final class RegistryTestAccess {
        static Set<String> allKeys() {
            return RegistryTest.allKeys();
        }
    }

    static Blueprint unusedImportGuard() {
        return null;
    }
}
