package com.lewandivka.entity.boss;

import com.lewandivka.core.boss.BossEvent;
import com.lewandivka.core.boss.BossRules;
import com.lewandivka.core.boss.CollarCollectorRules;
import com.lewandivka.entity.LeashAnchorEntity;
import com.lewandivka.entity.npc.CatEntity;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.world.service.BlockOps;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Collar Collector. Phase 1: the real collars are found by the cats' reactions. Phase 2: one player is leashed to an
 * anchor that the others destroy (Chinazik bites it through when nobody can). Phase 3: clones; only the real collector
 * keeps the cats' attention.
 */
public class CollarCollectorEntity extends BossEntity {

    private boolean clone;
    private UUID leashed;
    private UUID anchor;
    private final List<UUID> clones = new ArrayList<>();

    public CollarCollectorEntity(EntityType<? extends CollarCollectorEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected BossRules createRules(long seed) {
        return new CollarCollectorRules(seed);
    }

    @Override
    protected String structure() {
        return "shelter";
    }

    private CollarCollectorRules collector() {
        return (CollarCollectorRules) rules;
    }

    private List<CatEntity> cats() {
        return sw().getEntitiesByClass(CatEntity.class, getBoundingBox().expand(60, 20, 60), c -> true);
    }

    @Override
    public boolean station(String action, int index, UUID player) {
        if (!fighting || !action.equals("stand") || index < 0 || index >= CollarCollectorRules.STANDS) {
            return false;
        }
        apply(collector().useStand(index, now()));
        return true;
    }

    @Override
    protected void onBossEvent(BossEvent e) {
        switch (e.type()) {
            case NAMES_HIDDEN -> {
                for (CatEntity c : cats()) {
                    c.setStoryName("???");
                }
                tell(Text.translatable("message.lewandivka.cat.name_hidden"));
            }
            case COLLARS_SHUFFLED -> {
                for (int i = 1; i <= CollarCollectorRules.STANDS; i++) {
                    BlockOps.setProp(sw(), blockAt("stand_" + i), "filled", "true");
                }
            }
            case COLLAR_REACTION -> {
                BlockOps.setProp(sw(), blockAt("stand_" + (e.a() + 1)), "filled", "false");
                boolean real = e.b() == 1;
                for (CatEntity c : cats()) {
                    c.play(real ? "alert" : "look");
                    sw().playSound(null, c.getBlockPos(), GameSounds.get(real ? "cat.meow_high" : "cat.sniff"), SoundCategory.NEUTRAL, 1.0f, 1.0f);
                }
                tell(Text.translatable(real ? "message.lewandivka.collar.right" : "message.lewandivka.collar.wrong"));
            }
            case LEASH -> leash(e.a());
            case LEASH_END -> unleash();
            case LEASH_BROKEN_BY_CAT -> {
                for (CatEntity c : cats()) {
                    if (c.isChinazik()) {
                        c.play("scratch");
                    }
                }
                if (anchor != null && sw().getEntity(anchor) != null) {
                    sw().getEntity(anchor).discard();
                }
            }
            case CLONES -> spawnClones(e.a());
            case CLONES_GONE -> {
                for (UUID id : clones) {
                    Entity c = sw().getEntity(id);
                    if (c != null) {
                        c.discard();
                    }
                }
                clones.clear();
            }
            case REAL_BOSS_HINT -> {
                for (CatEntity c : cats()) {
                    c.play("point");
                    c.getLookControl().lookAt(getX(), getEyeY(), getZ());
                }
                sw().spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + spec.height + 0.5, getZ(), 6, 0.2, 0.3, 0.2, 0.0);
            }
            case DEFEATED -> {
                for (CatEntity c : cats()) {
                    c.setStoryName("");
                }
            }
            default -> { }
        }
    }

    private void leash(int targetIndex) {
        List<ServerPlayerEntity> ps = participants(40);
        if (ps.isEmpty()) {
            return;
        }
        ServerPlayerEntity target = ps.get(Math.floorMod(targetIndex, ps.size()));
        leashed = target.getUuid();
        Entity a = spawnAdd("leash_anchor", "anchor_" + (1 + random.nextInt(4)));
        if (a instanceof LeashAnchorEntity anchorEntity) {
            anchorEntity.setOwnerBoss(this);
            anchor = anchorEntity.getUuid();
        }
        tell(Text.translatable("message.lewandivka.leash.caught"));
    }

    private void unleash() {
        leashed = null;
        if (anchor != null && sw().getEntity(anchor) != null) {
            sw().getEntity(anchor).discard();
        }
        anchor = null;
    }

    /** The anchor entity died: the players freed their friend. */
    public void anchorDestroyed() {
        if (fighting) {
            apply(collector().anchorDestroyed(now()));
        }
    }

    private void spawnClones(int count) {
        for (int i = 0; i < count; i++) {
            Entity c = spawnAdd("collar_collector", "anchor_" + (1 + i % 4));
            if (c instanceof CollarCollectorEntity cc) {
                cc.clone = true;
                cc.setHealth(1.0f);
                clones.add(cc.getUuid());
            }
        }
    }

    @Override
    protected boolean isDecoy() {
        return clone;
    }

    @Override
    public void tick() {
        super.tick();
        if (clone) {
            // clones are decoys: they only wander around and pop when hit
            if (!getWorld().isClient && age % 40 == 0 && getTarget() == null) {
                getNavigation().startMovingTo(getX() + random.nextInt(9) - 4, getY(), getZ() + random.nextInt(9) - 4, 1.0);
            }
            return;
        }
        if (!getWorld().isClient && fighting && leashed != null) {
            ServerPlayerEntity p = sw().getServer().getPlayerManager().getPlayer(leashed);
            Entity a = anchor == null ? null : sw().getEntity(anchor);
            if (p == null || a == null || !p.isAlive()) {
                if (a == null && anchor != null) {
                    anchorDestroyed();
                }
            } else {
                Vec3d to = a.getPos().subtract(p.getPos());
                if (to.length() > 4.0) {
                    Vec3d pull = to.normalize().multiply(0.18);
                    p.addVelocity(pull.x, 0.02, pull.z);
                    p.velocityModified = true;
                }
                if (age % 2 == 0) {
                    Vec3d dir = to.normalize();
                    for (int i = 1; i < Math.min(12, (int) to.length()); i++) {
                        Vec3d point = p.getPos().add(0, 1, 0).add(dir.multiply(i));
                        sw().spawnParticles(ParticleTypes.ENCHANTED_HIT, point.x, point.y, point.z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (clone) {
            if (!getWorld().isClient) {
                sw().spawnParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                discard();
            }
            return true;
        }
        return super.damage(source, amount);
    }

    @Override
    public void onDeath(DamageSource source) {
        if (clone) {
            return;
        }
        super.onDeath(source);
    }

    @Override
    protected void onReset() {
        unleash();
        for (UUID id : clones) {
            Entity c = sw().getEntity(id);
            if (c != null) {
                c.discard();
            }
        }
        clones.clear();
        for (CatEntity c : cats()) {
            c.setStoryName("");
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("clone", clone);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        clone = nbt.getBoolean("clone");
        if (clone) {
            discard();
        }
    }
}
