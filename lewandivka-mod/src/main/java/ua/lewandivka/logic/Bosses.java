package ua.lewandivka.logic;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import ua.lewandivka.entity.boss.ChromaBossEntity;
import ua.lewandivka.registry.ModEntities;
import ua.lewandivka.registry.ModItems;
import ua.lewandivka.world.Sites;

import java.util.UUID;

/** Виклик босів з вівтарів і нагороди за перемоги. */
public final class Bosses {
    private static final String[] SITE_BY_INDEX = {"garage", "aqua", "depot", "shelter", "tower"};

    public static void summon(ServerWorld world, BlockPos altar, int index, ServerPlayerEntity player) {
        if (index < 0 || index >= SITE_BY_INDEX.length) {
            return;
        }
        String site = SITE_BY_INDEX[index];
        LewState st = LewState.get(world.getServer());
        if (st.has("done_" + site)) {
            player.sendMessage(Text.literal("Тут уже тихо. Колір повернувся.").formatted(Formatting.GRAY), true);
            return;
        }
        UUID existing = st.boss(site);
        if (existing != null) {
            Entity e = world.getEntity(existing);
            if (e != null && e.isAlive()) {
                player.sendMessage(Text.literal("Бос уже тут!").formatted(Formatting.RED), true);
                return;
            }
        }
        if (site.equals("tower") && !(st.has("done_garage") && st.has("done_shelter") && st.has("done_aqua") && st.has("done_depot"))) {
            player.sendMessage(Text.literal("Голова району приймає лише тих, хто пройшов усі данджі і повернув котам імена.").formatted(Formatting.GRAY), false);
            return;
        }
        EntityType<? extends ChromaBossEntity> type = switch (site) {
            case "garage" -> ModEntities.GARAGE_KING;
            case "aqua" -> ModEntities.LADY_WHIRL;
            case "depot" -> ModEntities.CONDUCTOR;
            case "shelter" -> ModEntities.COLLECTOR;
            default -> ModEntities.COLORLESS_HEAD;
        };
        ChromaBossEntity boss = spawn(world, type, altar, altar.add(0, 0, 5));
        if (boss == null) {
            return;
        }
        String intro = switch (site) {
            case "garage" -> "З-під гаражів підіймається Гаражний Король! Його ядро прикривають три підйомники.";
            case "aqua" -> "Вода в сухому озері збирається в постать. Пані Вирва вийшла поплавати!";
            case "depot" -> "Дзинь-дзинь! Кондуктор Останнього Рейсу перевіряє квитки. Захист знімуть лише три різні компостування.";
            default -> "Безбарвний Голова: «Залиште котів — і я поверну вас додому.» Ви відмовляєтесь. Бій!";
        };
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(altar.getX(), altar.getY(), altar.getZ()) < 80 * 80) {
                p.sendMessage(Text.literal(intro).formatted(Formatting.GOLD), false);
            }
        }
    }

    public static ChromaBossEntity spawn(ServerWorld world, EntityType<? extends ChromaBossEntity> type, BlockPos arena, BlockPos at) {
        ChromaBossEntity boss = type.create(world);
        if (boss == null) {
            return null;
        }
        boss.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180, 0);
        boss.setArena(arena);
        world.spawnEntity(boss);
        world.playSound(null, at, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.8f, 1.3f);
        LewState.get(world.getServer()).setBoss(boss.siteKey(), boss.getUuid());
        return boss;
    }

    public static void onDefeated(ServerWorld world, ChromaBossEntity boss) {
        MinecraftServer server = world.getServer();
        LewState st = LewState.get(server);
        String site = boss.siteKey();
        if (st.has("done_" + site)) {
            return;
        }
        st.set("done_" + site);
        st.setBoss(site, null);
        switch (site) {
            case "garage" -> reward(world, "Гаражний Король розсипається на металобрухт! Хромандівка повертає трохи кольору.",
                    new ItemStack(ModItems.DASH_SNEAKERS), "Кросівки-ривок");
            case "aqua" -> reward(world, "Пані Вирва розтікається веселкою по сухому озеру. Вода знову кольорова!",
                    new ItemStack(ModItems.SPRING_INSOLES), "Пружні устілки");
            case "depot" -> reward(world, "Кондуктор складає компостер: «Проїзд оплачено. Наступна зупинка — Вежа.»",
                    new ItemStack(ModItems.GLIDER_TICKET), "Квиток-планер");
            case "shelter" -> ShelterQuest.onCollectorDefeated(world);
            case "tower" -> Finale.victory(world);
            default -> {
            }
        }
    }

    private static void reward(ServerWorld world, String line, ItemStack item, String itemName) {
        MinecraftServer server = world.getServer();
        LewState st = LewState.get(server);
        String next = Travel.nextObjective(st);
        server.getPlayerManager().broadcast(Text.literal(line).formatted(Formatting.GOLD, Formatting.BOLD), false);
        server.getPlayerManager().broadcast(Text.literal("Нова здібність: " + itemName + ". Компас тепер веде до: " + Sites.displayName(next) + ".")
                .formatted(Formatting.AQUA), false);
        String ab = item.isOf(ModItems.DASH_SNEAKERS) ? ua.lewandivka.ability.Abilities.DASH
                : item.isOf(ModItems.SPRING_INSOLES) ? ua.lewandivka.ability.Abilities.JUMP : ua.lewandivka.ability.Abilities.GLIDE;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            st.grantAbility(p.getUuid(), ab);
            ua.lewandivka.ability.Abilities.announce(p, ab);
            Travel.give(p, Sites.compassTo(world, next));
        }
    }

    private Bosses() {
    }
}
