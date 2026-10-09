package com.lewandivka.entity.npc;

import com.lewandivka.core.text.DialogueBook;
import com.lewandivka.quest.Dialogues;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.GoToWalkTargetGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * A resident of the district: he walks about within a few blocks of the place where the plan put him, looks at whoever passes and
 * says a few short words when spoken to (one of several, never the same twice in a row). He cannot be hurt.
 */
public class CitizenEntity extends NpcEntity {

    private static final int RADIUS = 6;
    private static final int COOLDOWN = 50;

    private BlockPos home;
    private long lastTalk = Long.MIN_VALUE / 2;
    private int lastScript = -1;

    public CitizenEntity(EntityType<? extends CitizenEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        goalSelector.add(4, new GoToWalkTargetGoal(this, 0.5));
        goalSelector.add(5, new WanderAroundGoal(this, 0.45, 200));
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (!getWorld().isClient && player instanceof ServerPlayerEntity sp) {
            talk(sp);
        }
        return ActionResult.success(getWorld().isClient);
    }

    private void talk(ServerPlayerEntity player) {
        long now = getWorld().getTime();
        int n = DialogueBook.talks(spec().id);
        if (n == 0 || now - lastTalk < COOLDOWN || player.getServer() == null || Dialogues.busy(player)) {
            return;
        }
        lastTalk = now;
        int pick = random.nextInt(n);
        if (n > 1 && pick == lastScript) {
            pick = (pick + 1) % n;
        }
        lastScript = pick;
        getLookControl().lookAt(player, 30.0f, 30.0f);
        play("talk");
        Dialogues.play(player.getServer(), DialogueBook.talkId(spec().id, pick + 1), List.of(player), this);
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && age % 40 == 5 && !hasPositionTarget()) {
            if (home == null) {
                home = getBlockPos();
            }
            setPositionTarget(home, RADIUS);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (home != null) {
            nbt.putLong("Home", home.asLong());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Home")) {
            home = BlockPos.fromLong(nbt.getLong("Home"));
        }
    }
}
