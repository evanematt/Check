package ua.lewandivka.world;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import ua.lewandivka.block.ShieldNodeBlock;
import ua.lewandivka.logic.LewState;
import ua.lewandivka.logic.ShelterQuest;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Фіксована мапа Хромандівки: де стоїть кожна локація і як її будувати. */
public final class Sites {
    public record Site(String key, String name, int x, int z) {
    }

    public static final Map<String, Site> ALL = new LinkedHashMap<>();

    static {
        add(new Site("base", "Будинок-база", 0, 0));
        add(new Site("garage", "Кооператив «Веселковий гараж»", 150, 20));
        add(new Site("shelter", "Притулок загублених імен", 10, 170));
        add(new Site("aqua", "Аквапарк сухого озера", -170, 40));
        add(new Site("depot", "Трамвайне депо над небом", 60, -190));
        add(new Site("tower", "Вежа Голови району", -20, -380));
    }

    private static void add(Site s) {
        ALL.put(s.key(), s);
    }

    public static String displayName(String key) {
        Site s = ALL.get(key);
        return s == null ? key : s.name();
    }

    public static ItemStack compassTo(ServerWorld chroma, String key) {
        Site s = ALL.get(key);
        ItemStack compass = new ItemStack(Items.COMPASS);
        if (s == null) {
            return compass;
        }
        NbtCompound nbt = compass.getOrCreateNbt();
        NbtCompound pos = new NbtCompound();
        pos.putInt("X", s.x());
        pos.putInt("Y", 80);
        pos.putInt("Z", s.z());
        nbt.put("LodestonePos", pos);
        World.CODEC.encodeStart(NbtOps.INSTANCE, chroma.getRegistryKey()).result().ifPresent(e -> nbt.put("LodestoneDimension", e));
        nbt.putBoolean("LodestoneTracked", false);
        compass.setCustomName(Text.literal("Компас: " + s.name()).formatted(Formatting.LIGHT_PURPLE));
        return compass;
    }

    public static void ensureBuilt(ServerWorld world, String key) {
        LewState st = LewState.get(world.getServer());
        if (st.has("built_" + key)) {
            return;
        }
        Site s = ALL.get(key);
        if (s == null) {
            return;
        }
        st.set("built_" + key);
        world.getChunk(s.x() >> 4, s.z() >> 4);
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, s.x(), s.z());
        int y = Math.max(world.getSeaLevel() + 1, Math.min(top, 150)) - 1;
        Builder b = new Builder(world, new BlockPos(s.x(), y, s.z()));
        switch (key) {
            case "base" -> SiteBuilders.base(b, st);
            case "garage" -> SiteBuilders.garage(b, st);
            case "shelter" -> SiteBuilders.shelter(b, st);
            case "aqua" -> SiteBuilders.aqua(b, st);
            case "depot" -> SiteBuilders.depot(b, st);
            case "tower" -> SiteBuilders.tower(b, st);
            default -> {
            }
        }
    }

    /** Будуємо локації, коли хтось підходить ближче ніж на ~100 блоків. */
    public static void tickProximity(ServerWorld world) {
        LewState st = LewState.get(world.getServer());
        BlockPos bs = st.point("base", "spawn");
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (bs != null && !st.has("base_left") && p.squaredDistanceTo(bs.getX(), bs.getY(), bs.getZ()) > 1600) {
                st.set("base_left");
            }
            for (Site s : ALL.values()) {
                if (st.has("built_" + s.key())) {
                    continue;
                }
                double dx = p.getX() - s.x();
                double dz = p.getZ() - s.z();
                if (dx * dx + dz * dz < 100 * 100) {
                    ensureBuilt(world, s.key());
                    p.sendMessage(Text.literal("Попереду: " + s.name()).formatted(Formatting.LIGHT_PURPLE), true);
                    return;
                }
            }
        }
    }

    // ----- перемикачі -----

    private static int activeCount(ServerWorld world, List<BlockPos> nodes) {
        int n = 0;
        for (BlockPos p : nodes) {
            BlockState s = world.getBlockState(p);
            if (s.getBlock() instanceof ShieldNodeBlock && s.get(ShieldNodeBlock.ACTIVE)) {
                n++;
            }
        }
        return n;
    }

    /** Чи виконана умова локації: всі перемикачі увімкнені (а в депо — ще й різними гравцями). */
    public static boolean isSatisfied(ServerWorld world, String site) {
        LewState st = LewState.get(world.getServer());
        List<BlockPos> nodes = st.nodes(site);
        if (nodes.isEmpty() || activeCount(world, nodes) < nodes.size()) {
            return false;
        }
        if (site.equals("depot")) {
            Set<UUID> who = new HashSet<>();
            for (BlockPos p : nodes) {
                UUID u = st.nodePullers.get(p.asLong());
                if (u != null) {
                    who.add(u);
                }
            }
            BlockPos c = st.point("depot", "arena");
            int near = c == null ? 1 : world.getPlayers(pl -> pl.squaredDistanceTo(c.getX(), c.getY(), c.getZ()) < 40 * 40).size();
            return who.size() >= Math.max(1, Math.min(3, near));
        }
        return true;
    }

    public static void onNodeActivated(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
        LewState st = LewState.get(world.getServer());
        String site = st.siteOfNode(pos);
        if (site == null) {
            return;
        }
        st.nodePullers.put(pos.asLong(), player.getUuid());
        List<BlockPos> nodes = st.nodes(site);
        int active = activeCount(world, nodes);
        String what = switch (site) {
            case "garage" -> "Підйомники вимкнено";
            case "depot" -> "Квитки прокомпостовано";
            default -> "Важелі опущено";
        };
        Text msg = Text.literal(what + ": " + active + "/" + nodes.size() + " (тримається 30 с)").formatted(Formatting.YELLOW);
        for (ServerPlayerEntity p : world.getPlayers(pl -> pl.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) < 48 * 48)) {
            p.sendMessage(msg, true);
        }
        if (site.equals("shelter") && active >= nodes.size()) {
            ShelterQuest.openDoor(world);
        } else if (active >= nodes.size() && isSatisfied(world, site)) {
            world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 2f, 1.2f);
            for (ServerPlayerEntity p : world.getPlayers(pl -> pl.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) < 48 * 48)) {
                p.sendMessage(Text.literal("Захист боса впав! Бийте, поки перемикачі тримаються!").formatted(Formatting.GREEN, Formatting.BOLD), false);
            }
        } else if (site.equals("depot") && active >= nodes.size()) {
            player.sendMessage(Text.literal("Кондуктор: «Один квиток — одна людина!» Компостуйте різними гравцями.").formatted(Formatting.RED), false);
        }
    }

    public static void onNodeReset(ServerWorld world, BlockPos pos) {
        LewState.get(world.getServer()).nodePullers.remove(pos.asLong());
    }

    public static void useTramStop(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
        LewState st = LewState.get(world.getServer());
        BlockPos ground = st.point("depot", "stop_ground");
        BlockPos sky = st.point("depot", "stop_sky");
        if (ground == null || sky == null) {
            return;
        }
        BlockPos dest;
        String line;
        if (pos.equals(ground)) {
            dest = st.point("depot", "arrive_sky");
            line = "Дзинь-дзинь! Наступна зупинка — Депо над небом.";
        } else {
            dest = st.point("depot", "arrive_ground");
            line = "Дзинь-дзинь! Кінцева. Земля.";
        }
        if (dest == null) {
            return;
        }
        world.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.5f, 1.5f);
        player.teleport(world, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, player.getYaw(), 0);
        player.fallDistance = 0;
        player.sendMessage(Text.literal(line).formatted(Formatting.YELLOW), true);
    }

    private Sites() {
    }
}
