package com.lewandivka.entity;

import com.lewandivka.entity.boss.CollarCollectorEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.world.World;

/** The anchor of the Collar Collector's magic leash. It has a little health: the players break it to free their friend. */
public class LeashAnchorEntity extends LewMob {

    private CollarCollectorEntity owner;

    public LeashAnchorEntity(EntityType<? extends LeashAnchorEntity> type, World world) {
        super(type, world);
        setAiDisabled(true);
    }

    public void setOwnerBoss(CollarCollectorEntity owner) {
        this.owner = owner;
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!getWorld().isClient && owner != null) {
            owner.anchorDestroyed();
        }
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        if (!getWorld().isClient && owner != null && !isAlive() == false && isRemoved()) {
            // removed without dying (reset): nothing to report
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void initGoals() {
    }
}
