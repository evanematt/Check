package com.lewandivka.entity.npc;

import com.lewandivka.entity.LewMob;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** Story npc without combat: Mr. Shlahbaum behind his kiosk and the Debtor. Talking is handled by {@link NpcActions}. */
public class NpcEntity extends LewMob {

    public NpcEntity(EntityType<? extends NpcEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient && player instanceof ServerPlayerEntity sp && hand == Hand.MAIN_HAND) {
            ActionResult r = NpcActions.interact(this, sp, hand);
            if (r != ActionResult.PASS) {
                play("talk");
                return r;
            }
        }
        return super.interactMob(player, hand);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // quest givers cannot be killed (the campaign must stay winnable); the debtor just flinches. The void, creative players and
        // the /kill command can: a stray one (a spawn egg, a summon) must be removable, and the story brings the real one back
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD) || source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL)
                || source.getAttacker() instanceof PlayerEntity p && p.isCreative()) {
            return super.damage(source, amount);
        }
        play("hurt");
        return false;
    }

    @Override
    public boolean isPushable() {
        return spec.role != com.lewandivka.core.registry.EntitySpec.Role.SHLAHBAUM;
    }

    @Override
    protected String ambientSoundId() {
        return "npc.mutter";
    }
}
