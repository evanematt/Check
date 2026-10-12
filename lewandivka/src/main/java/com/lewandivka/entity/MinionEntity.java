package com.lewandivka.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/** Small mechanic minion of the Garage King and gremlin of the paint workshop: fast, weak, melee only. */
public class MinionEntity extends LewMob {

    public MinionEntity(EntityType<? extends MinionEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }
}
