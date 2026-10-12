package com.lewandivka.entity.boss;

import com.lewandivka.core.boss.BossEvent;
import com.lewandivka.core.boss.BossRules;
import com.lewandivka.core.boss.LadyVortexRules;
import com.lewandivka.core.puzzle.ZonePlanner.Zone;
import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.item.GameItems;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.world.service.BlockOps;
import com.lewandivka.world.structure.Structures.Marker;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Lady Vortex. The arena has four quarters, each with a colour AND a symbol. A quarter is announced, then flooded (and
 * hurts). In the second phase the players route water cores into the drains before she can be hurt.
 */
public class LadyVortexEntity extends BossEntity {

    private final Set<Zone> flooded = new HashSet<>();

    public LadyVortexEntity(EntityType<? extends LadyVortexEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected BossRules createRules(long seed) {
        return new LadyVortexRules(seed);
    }

    @Override
    protected String structure() {
        return "aquapark";
    }

    private LadyVortexRules vortex() {
        return (LadyVortexRules) rules;
    }

    @Override
    public boolean station(String action, int index, UUID player) {
        if (!fighting || !action.equals("drain")) {
            return false;
        }
        int before = vortex().drainedCount();
        apply(vortex().useDrain(index, now()));
        if (vortex().drainedCount() > before) {
            BlockOps.setProp(sw(), blockAt("drain_" + (index + 1)), "open", "true");
            sw().playSound(null, blockAt("drain_" + (index + 1)), GameSounds.get("drain.open"), SoundCategory.BLOCKS, 1.0f, 1.0f);
            tell(Text.translatable("message.lewandivka.vortex.drained"));
            return true;
        }
        return false;
    }

    private Marker zone(int ordinal) {
        return marker("zone_" + Zone.values()[Math.floorMod(ordinal, Zone.values().length)].key());
    }

    @Override
    protected void onBossEvent(BossEvent e) {
        switch (e.type()) {
            case ZONE_WARN -> {
                Zone z = Zone.values()[Math.floorMod(e.a(), Zone.values().length)];
                Marker m = zone(e.a());
                if (m != null) {
                    Vec3d c = m.center();
                    sw().spawnParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + 0.3, c.z, 40, m.sx() / 3.0, 0.2, m.sz() / 3.0, 0.02);
                }
                // colour AND symbol in the message, so nobody depends on colour alone
                tell(Text.translatable("message.lewandivka.vortex.zone", Text.translatable("hud.lewandivka.zone." + z.color)));
                sw().playSound(null, getBlockPos(), GameSounds.get("boss.telegraph"), SoundCategory.HOSTILE, 1.0f, 0.8f);
            }
            case ZONE_FLOOD -> {
                Marker m = zone(e.a());
                if (m != null) {
                    flood(m, true);
                    flooded.add(Zone.values()[Math.floorMod(e.a(), Zone.values().length)]);
                    sw().playSound(null, m.pos(), GameSounds.get("water.whoosh"), SoundCategory.HOSTILE, 1.0f, 1.0f);
                }
            }
            case ZONE_CLEAR -> clearFloods();
            case CORES_SPAWN -> {
                for (int i = 0; i < Math.max(1, e.a()); i++) {
                    Vec3d p = at("core_spawn_" + (1 + i % 4));
                    ItemEntity core = new ItemEntity(sw(), p.x, p.y + 0.5, p.z, new ItemStack(GameItems.get(QuestItems.WATER_CORE)));
                    adopt(core);
                    sw().spawnEntity(core);
                }
            }
            case DRAIN_PHASE_START -> {
                tell(Text.translatable("message.lewandivka.drain.core"));
                for (int i = 1; i <= 4; i++) {
                    BlockOps.setProp(sw(), blockAt("drain_" + i), "open", "false");
                }
            }
            case DRAIN_PHASE_FAILED -> tell(Text.translatable("message.lewandivka.encounter_reset"));
            default -> { }
        }
    }

    private void flood(Marker m, boolean on) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int x = m.x(); x < m.x() + m.sx(); x++) {
            for (int z = m.z(); z < m.z() + m.sz(); z++) {
                p.set(x, m.y(), z);
                if (on && sw().getBlockState(p).isAir()) {
                    sw().setBlockState(p, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS);
                } else if (!on && sw().getBlockState(p).isOf(Blocks.WATER)) {
                    sw().setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    private void clearFloods() {
        for (Zone z : flooded) {
            Marker m = marker("zone_" + z.key());
            if (m != null) {
                flood(m, false);
            }
        }
        flooded.clear();
    }

    @Override
    public void tick() {
        super.tick();
        // a flooded quarter hurts but never kills: it stops at three hearts
        if (!getWorld().isClient && fighting && age % 20 == 0) {
            for (Zone z : flooded) {
                Marker m = marker("zone_" + z.key());
                if (m == null) {
                    continue;
                }
                for (ServerPlayerEntity p : participants(40)) {
                    if (m.contains(p.getBlockPos()) || m.contains(p.getBlockPos().down())) {
                        if (p.getHealth() > 6.0f) {
                            p.damage(getDamageSources().magic(), 2.0f);
                        }
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
                    }
                }
            }
        }
    }

    @Override
    protected void onReset() {
        clearFloods();
        for (int i = 1; i <= 4; i++) {
            BlockOps.setProp(sw(), blockAt("drain_" + i), "open", "false");
        }
    }
}
