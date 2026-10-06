package com.lewandivka.core.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lewandivka.core.campaign.Ability;
import com.lewandivka.core.campaign.CampaignStage;
import com.lewandivka.core.campaign.QuestStep;
import com.lewandivka.core.campaign.Reputation;
import com.lewandivka.core.dialogue.DialogueScript;
import com.lewandivka.core.registry.AdvancementSpec;
import com.lewandivka.core.registry.BlockSpec;
import com.lewandivka.core.registry.EntitySpec;
import com.lewandivka.core.registry.ItemSpec;
import com.lewandivka.core.registry.ModAdvancements;
import com.lewandivka.core.registry.ModBlocks;
import com.lewandivka.core.registry.ModEntities;
import com.lewandivka.core.registry.ModItems;
import com.lewandivka.core.registry.ModSounds;
import com.lewandivka.core.registry.PropSpec;
import com.lewandivka.core.registry.SoundSpec;
import com.lewandivka.core.text.DialogueBook;
import com.lewandivka.core.text.LoreNotes;
import com.lewandivka.core.text.QuestTexts;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code gradle -p core exportSpec} writes the registry/text catalogs as JSON to {@code tools/data/spec}. The Python
 * resource generator reads them, so Java stays the single source of truth for ids, states and texts.
 */
public final class SpecExport {

    private SpecExport() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static Map<String, String> render() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("blocks.json", GSON.toJson(blocks()));
        files.put("items.json", GSON.toJson(items()));
        files.put("entities.json", GSON.toJson(entities()));
        files.put("sounds.json", GSON.toJson(sounds()));
        files.put("advancements.json", GSON.toJson(advancements()));
        files.put("story.json", GSON.toJson(story()));
        return files;
    }

    private static JsonArray blocks() {
        JsonArray out = new JsonArray();
        for (BlockSpec b : ModBlocks.ALL) {
            JsonObject o = new JsonObject();
            o.addProperty("id", b.id);
            o.addProperty("behaviour", b.behaviour.name());
            o.addProperty("model", b.model.name());
            o.addProperty("nameUk", b.nameUk);
            o.addProperty("nameEn", b.nameEn);
            o.addProperty("light", b.light);
            if (b.lightProp != null) {
                o.addProperty("lightProp", b.lightProp);
            }
            o.addProperty("sound", b.sound);
            o.addProperty("unbreakable", b.unbreakable);
            o.addProperty("passable", b.passable);
            o.addProperty("translucent", b.translucent);
            o.addProperty("cutout", b.cutout);
            o.addProperty("inTab", b.inTab);
            JsonArray props = new JsonArray();
            for (PropSpec p : b.props) {
                JsonObject po = new JsonObject();
                po.addProperty("name", p.name());
                po.addProperty("type", p.type().name());
                JsonArray values = new JsonArray();
                p.values().forEach(values::add);
                po.add("values", values);
                props.add(po);
            }
            o.add("props", props);
            JsonArray visual = new JsonArray();
            b.visual.forEach(visual::add);
            o.add("visual", visual);
            out.add(o);
        }
        return out;
    }

    private static JsonArray items() {
        JsonArray out = new JsonArray();
        for (ItemSpec i : ModItems.ALL) {
            JsonObject o = new JsonObject();
            o.addProperty("id", i.id);
            o.addProperty("use", i.use.name());
            o.addProperty("nameUk", i.nameUk);
            o.addProperty("nameEn", i.nameEn);
            o.addProperty("loreUk", i.loreUk);
            o.addProperty("loreEn", i.loreEn);
            o.addProperty("stack", i.stack);
            o.addProperty("rarity", i.rarity);
            o.addProperty("glint", i.glint);
            o.addProperty("quest", i.quest);
            o.addProperty("inTab", i.inTab);
            o.addProperty("hunger", i.hunger);
            o.addProperty("saturation", i.saturation);
            o.addProperty("eatTicks", i.eatTicks);
            out.add(o);
        }
        return out;
    }

    private static JsonArray entities() {
        JsonArray out = new JsonArray();
        for (EntitySpec e : ModEntities.ALL) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id);
            o.addProperty("role", e.role.name());
            o.addProperty("group", e.group.name());
            o.addProperty("nameUk", e.nameUk);
            o.addProperty("nameEn", e.nameEn);
            o.addProperty("width", e.width);
            o.addProperty("height", e.height);
            o.addProperty("health", e.health);
            o.addProperty("attack", e.attack);
            o.addProperty("speed", e.speed);
            o.addProperty("armor", e.armor);
            o.addProperty("knockbackResistance", e.knockbackResistance);
            o.addProperty("followRange", e.followRange);
            o.addProperty("model", e.model);
            o.addProperty("texture", e.texture);
            o.addProperty("eggPrimary", e.eggPrimary);
            o.addProperty("eggSecondary", e.eggSecondary);
            o.addProperty("boss", e.boss);
            o.addProperty("scale", e.scale);
            JsonArray anims = new JsonArray();
            e.animations.forEach(anims::add);
            o.add("animations", anims);
            out.add(o);
        }
        return out;
    }

    private static JsonArray sounds() {
        JsonArray out = new JsonArray();
        for (SoundSpec s : ModSounds.ALL) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.id());
            o.addProperty("subtitleUk", s.subtitleUk());
            o.addProperty("subtitleEn", s.subtitleEn());
            o.addProperty("category", s.category());
            o.addProperty("variants", s.variants());
            o.addProperty("stream", s.stream());
            o.addProperty("volume", s.volume());
            out.add(o);
        }
        return out;
    }

    private static JsonArray advancements() {
        JsonArray out = new JsonArray();
        for (AdvancementSpec a : ModAdvancements.ALL) {
            JsonObject o = new JsonObject();
            o.addProperty("id", a.id());
            if (a.parent() != null) {
                o.addProperty("parent", a.parent());
            }
            o.addProperty("icon", a.icon());
            o.addProperty("frame", a.frame());
            o.addProperty("hidden", a.hidden());
            o.addProperty("titleUk", a.titleUk());
            o.addProperty("titleEn", a.titleEn());
            o.addProperty("descUk", a.descUk());
            o.addProperty("descEn", a.descEn());
            out.add(o);
        }
        return out;
    }

    private static JsonObject story() {
        JsonObject o = new JsonObject();
        JsonObject quests = new JsonObject();
        for (QuestStep s : QuestStep.ordered()) {
            QuestTexts.StepText t = QuestTexts.of(s);
            JsonObject so = new JsonObject();
            so.addProperty("id", s.id);
            so.addProperty("stage", s.stage.key());
            so.addProperty("counterMax", s.counterMax);
            so.addProperty("titleUk", t.titleUk());
            so.addProperty("titleEn", t.titleEn());
            so.addProperty("objectiveUk", t.objectiveUk());
            so.addProperty("objectiveEn", t.objectiveEn());
            so.addProperty("hintUk", t.hintUk());
            so.addProperty("hintEn", t.hintEn());
            quests.add(s.key(), so);
        }
        o.add("steps", quests);
        JsonObject stages = new JsonObject();
        for (CampaignStage st : CampaignStage.values()) {
            String[] n = QuestTexts.stages().get(st.key());
            JsonObject so = new JsonObject();
            so.addProperty("uk", n[0]);
            so.addProperty("en", n[1]);
            stages.add(st.key(), so);
        }
        o.add("stages", stages);
        JsonObject dialogue = new JsonObject();
        for (Map.Entry<String, DialogueBook.Line> e : DialogueBook.text().entrySet()) {
            JsonObject lo = new JsonObject();
            lo.addProperty("uk", e.getValue().uk());
            lo.addProperty("en", e.getValue().en());
            dialogue.add(e.getKey(), lo);
        }
        o.add("text", dialogue);
        JsonArray scripts = new JsonArray();
        for (DialogueScript s : DialogueBook.scripts().values()) {
            scripts.add(s.id());
        }
        o.add("scripts", scripts);
        JsonObject notes = new JsonObject();
        for (Map.Entry<Integer, LoreNotes.Note> e : LoreNotes.all().entrySet()) {
            JsonObject lo = new JsonObject();
            lo.addProperty("uk", e.getValue().uk());
            lo.addProperty("en", e.getValue().en());
            notes.add(String.valueOf(e.getKey()), lo);
        }
        o.add("notes", notes);
        JsonArray abilities = new JsonArray();
        for (Ability a : Ability.values()) {
            abilities.add(a.key());
        }
        o.add("abilities", abilities);
        JsonArray reps = new JsonArray();
        for (Reputation r : Reputation.values()) {
            reps.add(r.key());
        }
        o.add("reputations", reps);
        return o;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("out", "../tools/data/spec"));
        dir.mkdirs();
        for (Map.Entry<String, String> e : render().entrySet()) {
            Files.writeString(new File(dir, e.getKey()).toPath(), e.getValue() + "\n", StandardCharsets.UTF_8);
        }
        System.out.println("wrote spec to " + dir.getAbsolutePath());
    }
}
