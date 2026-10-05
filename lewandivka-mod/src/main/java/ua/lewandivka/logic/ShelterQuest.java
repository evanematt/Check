package ua.lewandivka.logic;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import ua.lewandivka.entity.ChromaCatEntity;
import ua.lewandivka.registry.ModEntities;
import ua.lewandivka.registry.ModWorldgen;
import ua.lewandivka.world.Sites;

/** Квест «Двоє, які знають дорогу» / «Відкрий двері. Ні, вже не треба». */
public final class ShelterQuest {
    private static void say(MinecraftServer server, String line, Formatting color) {
        server.getPlayerManager().broadcast(Text.literal(line).formatted(color), false);
    }

    public static void useDoor(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
        LewState st = LewState.get(world.getServer());
        BlockPos inside = st.point("shelter", "int_arrival");
        BlockPos outside = st.point("shelter", "ext_arrival");
        if (inside == null || outside == null) {
            return;
        }
        if (pos.getY() > inside.getY() + 10) {
            player.teleport(world, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 180, 0);
            player.sendMessage(Text.literal("Всередині Притулок набагато більший, ніж зовні…").formatted(Formatting.LIGHT_PURPLE), true);
            if (!st.has("shelter_intro")) {
                st.set("shelter_intro");
                say(world.getServer(), "Притулок загублених імен. За величезними дверима чути знайоме мявкання. Три важелі мають бути увімкнені разом.", Formatting.LIGHT_PURPLE);
            }
        } else {
            player.teleport(world, outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5, 0, 0);
        }
    }

    /** Усі три важелі увімкнені — двері урочисто відчиняються. */
    public static void openDoor(ServerWorld world) {
        LewState st = LewState.get(world.getServer());
        if (st.has("shelter_door_open")) {
            return;
        }
        BlockPos min = st.point("shelter", "door_min");
        BlockPos max = st.point("shelter", "door_max");
        BlockPos chSpawn = st.point("shelter", "chinazik_spawn");
        if (min == null || max == null || chSpawn == null) {
            return;
        }
        st.set("shelter_door_open");
        for (BlockPos p : BlockPos.iterate(min, max)) {
            world.setBlockState(p, Blocks.AIR.getDefaultState());
            world.spawnParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
        }
        world.playSound(null, min, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 2f, 0.5f);
        ChromaCatEntity ch = ModEntities.CHINAZIK.create(world);
        if (ch != null) {
            ch.refreshPositionAndAngles(chSpawn.getX() + 0.5, chSpawn.getY(), chSpawn.getZ() + 0.5, 180, 0);
            ch.setQuestMode(false);
            world.spawnEntity(ch);
            st.chinazik = ch.getUuid();
            st.markDirty();
        }
        MinecraftServer server = world.getServer();
        say(server, "Двері урочисто відчиняються…", Formatting.GOLD);
        Scheduler.after(server, 40, () -> say(server, "Чіназік сидить на порозі, дивиться на вас — і йде в інший бік.", Formatting.GOLD));
        Scheduler.after(server, 100, () -> say(server, "Підказка: виманіть його рибою до килимка біля виходу. А Метадонна… десь серед коробок.", Formatting.GRAY));
    }

    public static void checkChinazikDoormat(ChromaCatEntity cat) {
        if (!(cat.getWorld() instanceof ServerWorld world) || !world.getRegistryKey().equals(ModWorldgen.CHROMA)) {
            return;
        }
        LewState st = LewState.get(world.getServer());
        if (st.has("chinazik_found") || !st.has("shelter_door_open")) {
            return;
        }
        BlockPos mat = st.point("shelter", "doormat");
        if (mat != null && mat.isWithinDistance(cat.getPos(), 2.5)) {
            st.set("chinazik_found");
            cat.setQuestMode(true);
            world.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 1, cat.getZ(), 8, 0.4, 0.4, 0.4, 0);
            say(world.getServer(), "Чіназік всівся на килимок біля дверей і мявкає, щоб відчинили. Знайдено!", Formatting.GOLD);
            maybeSpawnCollector(world);
        }
    }

    public static void onTinyBox(ServerWorld world, BlockPos pos) {
        if (!world.getRegistryKey().equals(ModWorldgen.CHROMA)) {
            return;
        }
        LewState st = LewState.get(world.getServer());
        if (st.has("metadonna_found") || !st.has("shelter_door_open")) {
            if (!st.has("shelter_door_open")) {
                world.getPlayers().forEach(p -> {
                    if (p.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) < 64) {
                        p.sendMessage(Text.literal("Коробочка порожня. Поки що."), true);
                    }
                });
            }
            return;
        }
        st.set("metadonna_found");
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
        ChromaCatEntity md = ModEntities.METADONNA.create(world);
        if (md != null) {
            md.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            md.setQuestMode(true);
            world.spawnEntity(md);
            st.metadonna = md.getUuid();
            st.markDirty();
        }
        world.spawnParticles(ParticleTypes.HEART, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
        world.playSound(null, pos, SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 1.5f, 1.4f);
        say(world.getServer(), "Найменша, зовсім непропорційна коробочка ворушиться… Звідти вилазить Метадонна!", Formatting.LIGHT_PURPLE);
        maybeSpawnCollector(world);
    }

    private static void maybeSpawnCollector(ServerWorld world) {
        LewState st = LewState.get(world.getServer());
        if (!st.has("chinazik_found") || !st.has("metadonna_found") || st.has("collector_spawned")) {
            return;
        }
        BlockPos at = st.point("shelter", "collector_spawn");
        if (at == null) {
            return;
        }
        st.set("collector_spawned");
        MinecraftServer server = world.getServer();
        Scheduler.after(server, 40, () -> {
            Bosses.spawn(world, ModEntities.COLLECTOR, at, at);
            say(server, "З темряви виходить Колекціонер Нашийників: «Ці імена — мої. Вони забудуть дорогу додому.»", Formatting.DARK_PURPLE);
            say(server, "Переможіть його — і поверніть котам імена!", Formatting.GOLD);
        });
    }

    public static void onCollectorDefeated(ServerWorld world) {
        MinecraftServer server = world.getServer();
        LewState st = LewState.get(server);
        BlockPos home = st.point("base", "cat_home");
        if (home == null) {
            home = world.getSpawnPos();
        }
        for (java.util.UUID id : new java.util.UUID[]{st.chinazik, st.metadonna}) {
            if (id == null) {
                continue;
            }
            Entity e = world.getEntity(id);
            if (e instanceof ChromaCatEntity cat) {
                ServerPlayerEntity nearest = (ServerPlayerEntity) world.getClosestPlayer(cat, 64);
                cat.bond(nearest, home, ModWorldgen.CHROMA);
                world.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 1, cat.getZ(), 12, 0.5, 0.5, 0.5, 0);
            }
        }
        say(server, "Колекціонер розсипається, і з його кишень сиплються нашийники. Імена повертаються!", Formatting.GOLD);
        say(server, "Чіназік і Метадонна тепер ваші — спільні для всіх трьох. ПКМ по коту відкриває команди.", Formatting.LIGHT_PURPLE);
        String next = Travel.nextObjective(st);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Advancements.grant(p, "cats");
            Travel.give(p, Sites.compassTo(world, next));
        }
        say(server, "Компас тепер веде до: " + Sites.displayName(next) + ".", Formatting.AQUA);
    }

    private ShelterQuest() {
    }
}
