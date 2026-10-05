package ua.lewandivka.logic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Увесь прогрес пригоди: хто де, які локації збудовані, які боси переможені. */
public class LewState extends PersistentState {
    public record ReturnPoint(RegistryKey<World> world, BlockPos pos) {
    }

    public boolean arrived;
    private final Set<String> flags = new HashSet<>();
    private final Set<UUID> visitors = new HashSet<>();
    private final Map<UUID, ReturnPoint> returns = new HashMap<>();
    private final Map<String, Map<String, BlockPos>> points = new HashMap<>();
    private final Map<String, List<BlockPos>> nodes = new HashMap<>();
    private final Map<String, UUID> bosses = new HashMap<>();
    @Nullable public UUID chinazik;
    @Nullable public UUID metadonna;

    // Тимчасове (не зберігається): хто і коли з'їв Хрому, хто вмикав перемикачі.
    public final Map<UUID, Long> pillEaters = new HashMap<>();
    public final Map<Long, UUID> nodePullers = new HashMap<>();

    public static LewState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(LewState::fromNbt, LewState::new, "lewandivka");
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }

    public void set(String flag) {
        if (flags.add(flag)) {
            markDirty();
        }
    }

    public void clearAll() {
        arrived = false;
        flags.clear();
        visitors.clear();
        points.clear();
        nodes.clear();
        bosses.clear();
        chinazik = null;
        metadonna = null;
        markDirty();
    }

    public boolean firstVisit(UUID player) {
        boolean first = visitors.add(player);
        if (first) {
            markDirty();
        }
        return first;
    }

    public void setReturn(UUID player, RegistryKey<World> world, BlockPos pos) {
        returns.put(player, new ReturnPoint(world, pos.toImmutable()));
        markDirty();
    }

    @Nullable
    public ReturnPoint getReturn(UUID player) {
        return returns.get(player);
    }

    public void setPoint(String site, String name, BlockPos pos) {
        points.computeIfAbsent(site, k -> new HashMap<>()).put(name, pos.toImmutable());
        markDirty();
    }

    @Nullable
    public BlockPos point(String site, String name) {
        Map<String, BlockPos> m = points.get(site);
        return m == null ? null : m.get(name);
    }

    public void addNode(String site, BlockPos pos) {
        nodes.computeIfAbsent(site, k -> new ArrayList<>()).add(pos.toImmutable());
        markDirty();
    }

    public List<BlockPos> nodes(String site) {
        return nodes.getOrDefault(site, List.of());
    }

    @Nullable
    public String siteOfNode(BlockPos pos) {
        for (Map.Entry<String, List<BlockPos>> e : nodes.entrySet()) {
            if (e.getValue().contains(pos)) {
                return e.getKey();
            }
        }
        return null;
    }

    public void setBoss(String site, @Nullable UUID id) {
        if (id == null) {
            bosses.remove(site);
        } else {
            bosses.put(site, id);
        }
        markDirty();
    }

    @Nullable
    public UUID boss(String site) {
        return bosses.get(site);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean("Arrived", arrived);
        NbtList f = new NbtList();
        for (String s : flags) {
            f.add(NbtString.of(s));
        }
        nbt.put("Flags", f);
        NbtList v = new NbtList();
        for (UUID u : visitors) {
            v.add(NbtString.of(u.toString()));
        }
        nbt.put("Visitors", v);
        NbtCompound r = new NbtCompound();
        returns.forEach((u, rp) -> {
            NbtCompound c = new NbtCompound();
            c.putString("World", rp.world().getValue().toString());
            c.putLong("Pos", rp.pos().asLong());
            r.put(u.toString(), c);
        });
        nbt.put("Returns", r);
        NbtCompound pts = new NbtCompound();
        points.forEach((site, m) -> {
            NbtCompound c = new NbtCompound();
            m.forEach((k, p) -> c.putLong(k, p.asLong()));
            pts.put(site, c);
        });
        nbt.put("Points", pts);
        NbtCompound nd = new NbtCompound();
        nodes.forEach((site, list) -> nd.putLongArray(site, list.stream().mapToLong(BlockPos::asLong).toArray()));
        nbt.put("Nodes", nd);
        NbtCompound b = new NbtCompound();
        bosses.forEach((site, id) -> b.putUuid(site, id));
        nbt.put("Bosses", b);
        if (chinazik != null) {
            nbt.putUuid("Chinazik", chinazik);
        }
        if (metadonna != null) {
            nbt.putUuid("Metadonna", metadonna);
        }
        return nbt;
    }

    public static LewState fromNbt(NbtCompound nbt) {
        LewState s = new LewState();
        s.arrived = nbt.getBoolean("Arrived");
        for (NbtElement e : nbt.getList("Flags", NbtElement.STRING_TYPE)) {
            s.flags.add(e.asString());
        }
        for (NbtElement e : nbt.getList("Visitors", NbtElement.STRING_TYPE)) {
            s.visitors.add(UUID.fromString(e.asString()));
        }
        NbtCompound r = nbt.getCompound("Returns");
        for (String k : r.getKeys()) {
            NbtCompound c = r.getCompound(k);
            s.returns.put(UUID.fromString(k), new ReturnPoint(
                    RegistryKey.of(RegistryKeys.WORLD, new Identifier(c.getString("World"))), BlockPos.fromLong(c.getLong("Pos"))));
        }
        NbtCompound pts = nbt.getCompound("Points");
        for (String site : pts.getKeys()) {
            NbtCompound c = pts.getCompound(site);
            Map<String, BlockPos> m = new HashMap<>();
            for (String k : c.getKeys()) {
                m.put(k, BlockPos.fromLong(c.getLong(k)));
            }
            s.points.put(site, m);
        }
        NbtCompound nd = nbt.getCompound("Nodes");
        for (String site : nd.getKeys()) {
            List<BlockPos> list = new ArrayList<>();
            for (long l : nd.getLongArray(site)) {
                list.add(BlockPos.fromLong(l));
            }
            s.nodes.put(site, list);
        }
        NbtCompound b = nbt.getCompound("Bosses");
        for (String site : b.getKeys()) {
            s.bosses.put(site, b.getUuid(site));
        }
        s.chinazik = nbt.containsUuid("Chinazik") ? nbt.getUuid("Chinazik") : null;
        s.metadonna = nbt.containsUuid("Metadonna") ? nbt.getUuid("Metadonna") : null;
        return s;
    }
}
