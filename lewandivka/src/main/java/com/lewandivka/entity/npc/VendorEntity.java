package com.lewandivka.entity.npc;

import com.lewandivka.core.registry.EntitySpec;
import com.lewandivka.core.registry.ModEntities;
import com.lewandivka.core.text.DialogueBook;
import com.lewandivka.core.trade.Wares;
import com.lewandivka.quest.Dialogues;
import com.lewandivka.sound.GameSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtCustomerGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.StopFollowingCustomerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Set;

/**
 * A trader of the market: he stands behind his counter, says a word to whoever comes and opens the trade screen of the game. He
 * sells food and things for emeralds and buys the plain goods of a first day of survival for emeralds ({@link Wares}); his goods
 * are made again every day. He cannot be hurt (the market stays open), he does not walk away.
 */
public class VendorEntity extends MerchantEntity implements GeoEntity {

    private static final Set<String> LOOPS = Set.of("idle", "walk", "run");
    private static final long DAY = 24000L;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final EntitySpec spec;
    private long restockDay = Long.MIN_VALUE;
    private int lastScript = -1;

    public VendorEntity(EntityType<? extends VendorEntity> type, World world) {
        super(type, world);
        this.spec = ModEntities.byId(Registries.ENTITY_TYPE.getId(type).getPath());
        setPersistent();
    }

    public EntitySpec spec() {
        return spec;
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new StopFollowingCustomerGoal(this));
        goalSelector.add(2, new LookAtCustomerGoal(this));
        goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        goalSelector.add(9, new LookAroundGoal(this));
    }

    @Override
    public PassiveEntity createChild(ServerWorld world, PassiveEntity other) {
        return null;
    }

    @Override
    public boolean isLeveledMerchant() {
        return false;
    }

    // ------------------------------------------------------------------ trading

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!isAlive() || hasCustomer() || isBaby() || hand != Hand.MAIN_HAND) {
            return super.interactMob(player, hand);
        }
        if (getOffers().isEmpty()) {
            return ActionResult.success(getWorld().isClient);
        }
        if (!getWorld().isClient) {
            restockIfANewDay();
            if (player instanceof ServerPlayerEntity sp) {
                greet(sp);
            }
            setCustomer(player);
            sendOffers(player, getDisplayName(), 1);
        }
        return ActionResult.success(getWorld().isClient);
    }

    /** The goods of the day: every offer of the table of the trader, as the trade screen of the game wants it. */
    @Override
    protected void fillRecipes() {
        TradeOfferList list = getOffers();
        for (Wares.Offer o : Wares.of(spec.id)) {
            ItemStack give = stack(o.give(), o.giveCount());
            ItemStack get = stack(o.get(), o.getCount());
            if (!give.isEmpty() && !get.isEmpty()) {
                list.add(new TradeOffer(give, get, o.maxUses(), 0, 0.0f));
            }
        }
    }

    private static ItemStack stack(String id, int count) {
        Item item = Registries.ITEM.get(new Identifier(id));
        return new ItemStack(item, count);
    }

    private void restockIfANewDay() {
        long day = getWorld().getTime() / DAY;
        if (day != restockDay) {
            restockDay = day;
            for (TradeOffer offer : getOffers()) {
                offer.resetUses();
            }
        }
    }

    @Override
    protected void afterUsing(TradeOffer offer) {
        if (offer.shouldRewardPlayerExperience()) {
            getWorld().spawnEntity(new ExperienceOrbEntity(getWorld(), getX(), getY() + 0.5, getZ(), 1 + random.nextInt(3)));
        }
    }

    /** One of the few words of the trader, said in the chat before the counter opens. */
    private void greet(ServerPlayerEntity player) {
        int n = DialogueBook.talks(spec.id);
        if (n == 0 || player.getServer() == null || Dialogues.busy(player)) {
            return;
        }
        int pick = random.nextInt(n);
        if (n > 1 && pick == lastScript) {
            pick = (pick + 1) % n;
        }
        lastScript = pick;
        play("talk");
        Dialogues.play(player.getServer(), DialogueBook.talkId(spec.id, pick + 1), List.of(player), this);
    }

    // ------------------------------------------------------------------ the creature

    @Override
    public boolean damage(DamageSource source, float amount) {
        // the market stays open: only the void, creative players and the kill command remove a trader
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)
                || source.getAttacker() instanceof PlayerEntity p && p.isCreative()) {
            return super.damage(source, amount);
        }
        play("hurt");
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putLong("RestockDay", restockDay);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("RestockDay")) {
            restockDay = nbt.getLong("RestockDay");
        }
        // the goods are always those of the table of the trader, so a better table reaches the traders of an old world too
        offers = null;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return GameSounds.has("npc.mutter") && !hasCustomer() ? GameSounds.get("npc.mutter") : null;
    }

    // ------------------------------------------------------------------ animation

    /** Server side: plays a one-shot animation of the humanoid model on all tracking clients. */
    public void play(String animation) {
        if (!getWorld().isClient && spec.animations.contains(animation)) {
            triggerAnim("action", animation);
        }
    }

    private String anim(String name) {
        return "animation." + spec.model + "." + name;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 5, this::moveState));
        AnimationController<VendorEntity> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        for (String a : spec.animations) {
            if (!LOOPS.contains(a)) {
                action.triggerableAnim(a, RawAnimation.begin().thenPlay(anim(a)));
            }
        }
        controllers.add(action);
    }

    private PlayState moveState(AnimationState<VendorEntity> state) {
        String name = state.isMoving() ? "walk" : "idle";
        return state.setAndContinue(RawAnimation.begin().thenLoop(anim(spec.animations.contains(name) ? name : "idle")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
