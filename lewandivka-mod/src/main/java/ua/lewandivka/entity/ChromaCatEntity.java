package ua.lewandivka.entity;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ua.lewandivka.block.GuardDoorBlock;
import ua.lewandivka.logic.LewState;
import ua.lewandivka.logic.ShelterQuest;
import ua.lewandivka.network.ModNetworking;
import ua.lewandivka.registry.ModBlocks;
import ua.lewandivka.registry.ModEntities;
import ua.lewandivka.registry.ModWorldgen;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Чіназік (великий чорний хаос) і Метадонна (мікро-киця з режимом ракети).
 * Після квесту в Притулку вони спільні для всіх гравців: будь-хто може дати будь-яку команду,
 * діє остання.
 */
public class ChromaCatEntity extends PathAwareEntity {
    public static final int MODE_WANDER = 0, MODE_SIT = 1, MODE_FOLLOW = 2, MODE_HOME = 3;
    public static final int POSE_NORMAL = 0, POSE_LOAF = 1, POSE_SLEEP = 2;

    public static final int CMD_SIT = 0, CMD_FOLLOW_ME = 1, CMD_FOLLOW_PLAYER = 2, CMD_WANDER = 3, CMD_HOME = 4, CMD_HELP = 5, CMD_SET_HOME = 6;

    private static final TrackedData<Integer> MODE = DataTracker.registerData(ChromaCatEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> CAT_POSE = DataTracker.registerData(ChromaCatEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> BONDED = DataTracker.registerData(ChromaCatEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Nullable private UUID followTarget;
    @Nullable private BlockPos homePos;
    private String homeDim = ModWorldgen.CHROMA.getValue().toString();
    @Nullable private BlockPos anchor;

    private int zoomies;
    private int napTicks;
    @Nullable private BlockPos napPos;
    @Nullable private BlockPos doorPos;
    private int doorMeowTicks;
    @Nullable private BlockPos litterPos;
    @Nullable private UUID snuggleTarget;
    private int snuggleTicks;
    private int quirkCooldown = 200;
    private int helpCooldown;
    private long lastHelpTime = -10000;
    private int wanderCooldown;
    private final List<MobEntity> taunted = new ArrayList<>();
    private int tauntTicks;

    public ChromaCatEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
        setCustomName(Text.literal(isChinazik() ? "Чіназік" : "Метадонна"));
        setCustomNameVisible(true);
    }

    public static DefaultAttributeContainer.Builder createChinazikAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    public static DefaultAttributeContainer.Builder createMetadonnaAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 18.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.34)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(MODE, MODE_WANDER);
        this.dataTracker.startTracking(CAT_POSE, POSE_NORMAL);
        this.dataTracker.startTracking(BONDED, false);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new BrainGoal());
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(6, new LookAroundGoal(this));
    }

    // ----- accessors -----

    public boolean isChinazik() {
        return getType() == ModEntities.CHINAZIK;
    }

    public String catName() {
        return isChinazik() ? "Чіназік" : "Метадонна";
    }

    public int getMode() {
        return dataTracker.get(MODE);
    }

    private void setMode(int mode) {
        dataTracker.set(MODE, mode);
    }

    public int getCatPose() {
        return dataTracker.get(CAT_POSE);
    }

    private void setCatPose(int pose) {
        if (getCatPose() != pose) {
            dataTracker.set(CAT_POSE, pose);
        }
    }

    public boolean isBonded() {
        return dataTracker.get(BONDED);
    }

    public boolean isFollowing(UUID player) {
        return getMode() == MODE_FOLLOW && player.equals(followTarget);
    }

    /** Після квесту: кіт стає спільним для всієї команди. */
    public void bond(@Nullable ServerPlayerEntity follow, BlockPos home, RegistryKey<World> homeWorld) {
        dataTracker.set(BONDED, true);
        this.homePos = home;
        this.homeDim = homeWorld.getValue().toString();
        if (follow != null) {
            this.followTarget = follow.getUuid();
            setMode(MODE_FOLLOW);
        } else {
            this.anchor = getBlockPos();
            setMode(MODE_WANDER);
        }
        setNoGravity(false);
        setCatPose(POSE_NORMAL);
    }

    /** Режим до квесту: сидить (Метадонна) або гуляє навколо точки (Чіназік). */
    public void setQuestMode(boolean sit) {
        dataTracker.set(BONDED, false);
        this.anchor = getBlockPos();
        setMode(sit ? MODE_SIT : MODE_WANDER);
    }

    // ----- player interaction -----

    private static boolean isFish(ItemStack s) {
        return s.isOf(Items.COD) || s.isOf(Items.SALMON) || s.isOf(Items.COOKED_COD) || s.isOf(Items.COOKED_SALMON) || s.isOf(Items.TROPICAL_FISH);
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        ServerPlayerEntity sp = (ServerPlayerEntity) player;
        ItemStack held = player.getStackInHand(hand);
        if (isFish(held)) {
            if (!player.getAbilities().creativeMode) {
                held.decrement(1);
            }
            heal(6);
            playSound(SoundEvents.ENTITY_CAT_EAT, 1f, isChinazik() ? 0.8f : 1.3f);
            ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.HEART, getX(), getY() + 0.8, getZ(), 4, 0.3, 0.3, 0.3, 0);
            String line = isChinazik() ? "Чіназік ковтає рибу не жуючи і дивиться: «Я не їв ніколи.»" : "Метадонна з'їдає шматочок і йде спати.";
            sp.sendMessage(Text.literal(line).formatted(Formatting.LIGHT_PURPLE), true);
            return ActionResult.CONSUME;
        }
        if (!isBonded()) {
            sp.sendMessage(Text.literal(catName() + " тебе ще не впізнає. Поверніть котам імена в Притулку.").formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        ModNetworking.openCatMenu(sp, this);
        return ActionResult.CONSUME;
    }

    /** Команда з меню. Будь-хто з гравців може командувати будь-яким котом — діє остання. */
    public void command(int cmd, ServerPlayerEntity by, @Nullable UUID target) {
        if (!isBonded()) {
            return;
        }
        MinecraftServerHelper h = new MinecraftServerHelper(by);
        String who = by.getName().getString();
        MutableText msg;
        clearQuirks();
        switch (cmd) {
            case CMD_SIT -> {
                setMode(MODE_SIT);
                msg = Text.literal(catName() + " сідає і чекає").formatted(Formatting.AQUA);
            }
            case CMD_FOLLOW_ME -> {
                followTarget = by.getUuid();
                setMode(MODE_FOLLOW);
                msg = Text.literal(catName() + " тепер іде за " + who).formatted(Formatting.AQUA);
            }
            case CMD_FOLLOW_PLAYER -> {
                ServerPlayerEntity t = target == null ? null : h.player(target);
                if (t == null) {
                    by.sendMessage(Text.literal("Цього гравця немає онлайн."), true);
                    return;
                }
                followTarget = t.getUuid();
                setMode(MODE_FOLLOW);
                msg = Text.literal(catName() + " тепер іде за " + t.getName().getString()).formatted(Formatting.AQUA);
            }
            case CMD_WANDER -> {
                anchor = getBlockPos();
                setMode(MODE_WANDER);
                msg = Text.literal(catName() + " гуляє поруч").formatted(Formatting.AQUA);
            }
            case CMD_HOME -> {
                setMode(MODE_HOME);
                msg = Text.literal(catName() + " іде додому").formatted(Formatting.AQUA);
            }
            case CMD_SET_HOME -> {
                homePos = getBlockPos();
                homeDim = getWorld().getRegistryKey().getValue().toString();
                msg = Text.literal(catName() + ": тепер дім тут").formatted(Formatting.AQUA);
            }
            case CMD_HELP -> {
                if (helpCooldown > 0) {
                    by.sendMessage(Text.literal(catName() + " ще відпочиває після минулого разу (" + (helpCooldown / 20 + 1) + " с)"), true);
                    return;
                }
                useHelp(by);
                return;
            }
            default -> {
                return;
            }
        }
        broadcast(msg.append(Text.literal("  (команда від " + who + ")").formatted(Formatting.DARK_GRAY)));
    }

    private void broadcast(Text text) {
        if (getWorld() instanceof ServerWorld sw) {
            for (ServerPlayerEntity p : sw.getServer().getPlayerManager().getPlayerList()) {
                p.sendMessage(text, false);
            }
        }
    }

    private void clearQuirks() {
        napTicks = 0;
        napPos = null;
        doorPos = null;
        litterPos = null;
        if (snuggleTarget != null) {
            snuggleTarget = null;
            setNoGravity(false);
        }
        setCatPose(POSE_NORMAL);
    }

    // ----- abilities -----

    private void useHelp(ServerPlayerEntity by) {
        ServerWorld world = (ServerWorld) getWorld();
        helpCooldown = 100;
        long now = world.getTime();
        int done = 0;
        if (isChinazik()) {
            // «Відкрийте, бляха»: відчиняє всі двері поруч, «Нічний забіг»: стягує ворогів на себе.
            playSound(SoundEvents.ENTITY_CAT_STRAY_AMBIENT, 2.0f, 0.7f);
            for (BlockPos p : BlockPos.iterate(getBlockPos().add(-8, -3, -8), getBlockPos().add(8, 3, 8))) {
                BlockState s = world.getBlockState(p);
                if (s.isOf(ModBlocks.GUARD_DOOR) && !s.get(GuardDoorBlock.OPEN)) {
                    world.setBlockState(p, s.with(GuardDoorBlock.OPEN, true), Block.NOTIFY_ALL);
                    world.spawnParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.01);
                    done++;
                } else if (s.getBlock() instanceof DoorBlock door && !s.get(DoorBlock.OPEN)) {
                    door.setOpen(this, world, s, p.toImmutable(), true);
                    done++;
                } else if (s.getBlock() instanceof FenceGateBlock && !s.get(FenceGateBlock.OPEN)) {
                    world.setBlockState(p, s.with(FenceGateBlock.OPEN, true), Block.NOTIFY_ALL);
                    done++;
                } else if (s.getBlock() instanceof TrapdoorBlock && !s.get(TrapdoorBlock.OPEN)) {
                    world.setBlockState(p, s.with(TrapdoorBlock.OPEN, true), Block.NOTIFY_ALL);
                    done++;
                }
            }
            int lured = 0;
            for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, getBoundingBox().expand(12), m -> !(m instanceof ua.lewandivka.entity.boss.ChromaBossEntity))) {
                mob.setTarget(this);
                taunted.add(mob);
                lured++;
            }
            tauntTicks = 160;
            zoomies = 160;
            broadcast(Text.literal("Чіназік: «МЯЯЯУ! ВІДКРИЙТЕ, БЛЯХА!»").formatted(Formatting.GOLD)
                    .append(Text.literal(" (дверей відчинено: " + done + ", ворогів відволік: " + lured + ")").formatted(Formatting.GRAY)));
        } else {
            // «Якщо влізла — це ліжко»: натискає кнопки в коробках за ґратами; заспокоює ворогів.
            playSound(SoundEvents.ENTITY_CAT_PURREOW, 1.5f, 1.4f);
            for (BlockPos p : BlockPos.iterate(getBlockPos().add(-10, -4, -10), getBlockPos().add(10, 4, 10))) {
                if (world.getBlockState(p).isOf(ModBlocks.BOX_SWITCH)) {
                    BlockPos sw = p.toImmutable();
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, sw.getX() + 0.5, sw.getY() + 1, sw.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0);
                    for (BlockPos b : BlockPos.iterate(sw.add(-10, -3, -10), sw.add(10, 3, 10))) {
                        if (world.getBlockState(b).isOf(ModBlocks.CHROMA_BARS)) {
                            world.setBlockState(b, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                            world.spawnParticles(ParticleTypes.END_ROD, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.02);
                            done++;
                        }
                    }
                }
            }
            int calmed = 0;
            for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, getBoundingBox().expand(10), m -> true)) {
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 1));
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1));
                calmed++;
            }
            broadcast(Text.literal("Метадонна пролазить туди, куди ніхто не пролізе, і муркоче.").formatted(Formatting.LIGHT_PURPLE)
                    .append(Text.literal(" (ґрат прибрано: " + done + ", заспокоєно: " + calmed + ")").formatted(Formatting.GRAY)));
        }
        // «П'ять хвилин божевілля»: якщо інший кіт допомагав щойно — спільний котячий переполох.
        lastHelpTime = now;
        for (ChromaCatEntity other : world.getEntitiesByClass(ChromaCatEntity.class, getBoundingBox().expand(16), c -> c != this)) {
            if (now - other.lastHelpTime < 200) {
                for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, getBoundingBox().expand(14), m -> true)) {
                    mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                    mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 160, 3));
                }
                zoomies = 200;
                other.zoomies = 200;
                broadcast(Text.literal("КОТЯЧИЙ ПЕРЕПОЛОХ! Обидва коти носяться колами — вороги не знають, куди дивитись!").formatted(Formatting.RED, Formatting.BOLD));
                break;
            }
        }
    }

    // ----- life -----

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.isSourceCreativePlayer() && !source.isOf(DamageTypes.OUT_OF_WORLD);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) && !getWorld().isClient) {
            goHomeNow();
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return getCatPose() == POSE_NORMAL;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        if (getCatPose() == POSE_SLEEP) {
            return SoundEvents.ENTITY_CAT_PURR;
        }
        return isChinazik() ? SoundEvents.ENTITY_CAT_STRAY_AMBIENT : SoundEvents.ENTITY_CAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_CAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_CAT_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return isChinazik() ? 0.8f + random.nextFloat() * 0.1f : 1.3f + random.nextFloat() * 0.15f;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return isChinazik() ? 200 : 400;
    }

    @Nullable
    private ServerPlayerEntity serverPlayer(@Nullable UUID id) {
        if (id == null || !(getWorld() instanceof ServerWorld sw)) {
            return null;
        }
        return sw.getServer().getPlayerManager().getPlayer(id);
    }

    @Nullable
    public ChromaCatEntity teleportTo(ServerWorld dest, Vec3d pos) {
        if (dest == getWorld()) {
            requestTeleport(pos.x, pos.y, pos.z);
            getNavigation().stop();
            return this;
        }
        Entity moved = FabricDimensions.teleport(this, dest, new TeleportTarget(pos, Vec3d.ZERO, getYaw(), getPitch()));
        return moved instanceof ChromaCatEntity c ? c : null;
    }

    private void goHomeNow() {
        ServerWorld sw = (ServerWorld) getWorld();
        ServerWorld dest = sw.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(homeDim)));
        if (dest == null || homePos == null) {
            dest = sw.getServer().getWorld(ModWorldgen.CHROMA);
            if (dest == null) {
                return;
            }
            BlockPos h = LewState.get(sw.getServer()).point("base", "cat_home");
            homePos = h != null ? h : dest.getSpawnPos();
        }
        anchor = homePos;
        setMode(MODE_WANDER);
        setVelocity(Vec3d.ZERO);
        fallDistance = 0;
        teleportTo(dest, Vec3d.ofBottomCenter(homePos));
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            return;
        }
        if (helpCooldown > 0) {
            helpCooldown--;
        }
        if (tauntTicks > 0 && --tauntTicks == 0) {
            for (MobEntity m : taunted) {
                if (m.isAlive() && m.getTarget() == this) {
                    m.setTarget(null);
                }
            }
            taunted.clear();
        }
        if (getY() < getWorld().getBottomY() + 2) {
            goHomeNow();
            return;
        }
        // Слідування між вимірами: якщо гравець в іншому світі — кіт стрибає за ним.
        if (isBonded() && getMode() == MODE_FOLLOW && age % 20 == 0) {
            ServerPlayerEntity t = serverPlayer(followTarget);
            if (t != null && t.getWorld() != getWorld()) {
                teleportTo(t.getServerWorld(), t.getPos());
            }
        }
        if (!isBonded() && isChinazik() && age % 10 == 0) {
            ShelterQuest.checkChinazikDoormat(this);
        }
    }

    /** Головний «мозок» кота, викликається з BrainGoal кожен тік на сервері. */
    private void think() {
        ServerWorld world = (ServerWorld) getWorld();
        if (snuggleTarget != null) {
            tickSnuggle();
            return;
        }
        int mode = getMode();
        if (zoomies > 0) {
            zoomies--;
        }
        switch (mode) {
            case MODE_SIT -> {
                getNavigation().stop();
                setCatPose(napTicks > 0 ? POSE_SLEEP : POSE_LOAF);
                if (!isChinazik() && !isBonded() && random.nextInt(600) == 0) {
                    playSound(SoundEvents.ENTITY_CAT_PURR, 0.8f, 1.4f);
                }
            }
            case MODE_FOLLOW -> {
                setCatPose(POSE_NORMAL);
                ServerPlayerEntity t = serverPlayer(followTarget);
                if (t == null) {
                    anchor = getBlockPos();
                    setMode(MODE_WANDER);
                    return;
                }
                if (t.getWorld() != world) {
                    return;
                }
                double d = squaredDistanceTo(t);
                if (d > 18 * 18) {
                    teleportTo(world, t.getPos());
                } else if (d > 9) {
                    if (age % 10 == 0 || getNavigation().isIdle()) {
                        getNavigation().startMovingTo(t, zoomies > 0 ? 1.8 : 1.25);
                    }
                } else {
                    getNavigation().stop();
                }
            }
            case MODE_HOME -> {
                setCatPose(POSE_NORMAL);
                if (homePos == null || !homeDim.equals(world.getRegistryKey().getValue().toString())
                        || !homePos.isWithinDistance(getPos(), 48)) {
                    goHomeNow();
                    return;
                }
                if (homePos.isWithinDistance(getPos(), 2.0)) {
                    anchor = homePos;
                    setMode(MODE_WANDER);
                } else if (getNavigation().isIdle()) {
                    getNavigation().startMovingTo(homePos.getX() + 0.5, homePos.getY(), homePos.getZ() + 0.5, 1.1);
                }
            }
            default -> wander(world);
        }
    }

    private void wander(ServerWorld world) {
        if (anchor == null) {
            anchor = getBlockPos();
        }
        if (quirkCooldown > 0) {
            quirkCooldown--;
        }
        // Дрімота (в коробці) — нічого не робимо.
        if (napTicks > 0) {
            napTicks--;
            getNavigation().stop();
            if (napPos != null && squaredDistanceTo(Vec3d.ofBottomCenter(napPos)) > 2.5) {
                napTicks = 0;
                napPos = null;
            } else {
                setCatPose(POSE_SLEEP);
                return;
            }
        }
        setCatPose(POSE_NORMAL);

        // Чіназік: «Я не їв ніколи» — йде до будь-кого з рибою.
        if (isChinazik()) {
            PlayerEntity fishHolder = world.getClosestPlayer(getX(), getY(), getZ(), isBonded() ? 8 : 12,
                    p -> isFish(((PlayerEntity) p).getMainHandStack()) || isFish(((PlayerEntity) p).getOffHandStack()));
            if (fishHolder != null) {
                getLookControl().lookAt(fishHolder, 30, 30);
                if (squaredDistanceTo(fishHolder) > 4) {
                    getNavigation().startMovingTo(fishHolder, 1.3);
                } else {
                    getNavigation().stop();
                    if (random.nextInt(60) == 0) {
                        playSound(SoundEvents.ENTITY_CAT_BEG_FOR_FOOD, 1.2f, 0.8f);
                    }
                }
                return;
            }
        }
        if (!isBonded()) {
            randomStroll(10);
            return;
        }

        // Метадонна: «Ти тепер матрац».
        if (!isChinazik()) {
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.isSleeping() && p.squaredDistanceTo(this) < 20 * 20) {
                    snuggleTarget = p.getUuid();
                    snuggleTicks = 0;
                    return;
                }
            }
        }

        // Чіназік мявкає під зачиненими дверима.
        if (isChinazik() && doorPos != null) {
            tickDoorMeow(world);
            return;
        }
        if (litterPos != null) {
            tickLitter(world);
            return;
        }
        if (quirkCooldown <= 0) {
            quirkCooldown = 200 + random.nextInt(200);
            if (isChinazik()) {
                if (random.nextInt(3) == 0) {
                    findClosedDoor(world);
                } else if (random.nextInt(14) == 0) {
                    findLitter(world);
                }
                if (zoomies == 0 && random.nextInt(10) == 0) {
                    startZoomies(world);
                }
            } else if (random.nextInt(2) == 0) {
                findBoxNap(world);
            }
            if (napPos != null) {
                return;
            }
        }
        if (napPos != null) {
            if (squaredDistanceTo(Vec3d.ofBottomCenter(napPos)) < 1.5) {
                requestTeleport(napPos.getX() + 0.5, napPos.getY(), napPos.getZ() + 0.5);
                napTicks = 600 + random.nextInt(600);
                getNavigation().stop();
            } else if (getNavigation().isIdle()) {
                if (!getNavigation().startMovingTo(napPos.getX() + 0.5, napPos.getY(), napPos.getZ() + 0.5, 1.0)) {
                    napPos = null;
                }
            }
            return;
        }
        randomStroll(zoomies > 0 ? 8 : 10);
    }

    private void randomStroll(int radius) {
        if (wanderCooldown > 0) {
            wanderCooldown--;
        }
        if (getNavigation().isIdle() && (zoomies > 0 || wanderCooldown <= 0)) {
            wanderCooldown = 60 + random.nextInt(120);
            BlockPos base = anchor != null ? anchor : getBlockPos();
            double x = base.getX() + 0.5 + random.nextInt(radius * 2 + 1) - radius;
            double z = base.getZ() + 0.5 + random.nextInt(radius * 2 + 1) - radius;
            getNavigation().startMovingTo(x, base.getY(), z, zoomies > 0 ? 2.0 : 0.9);
        }
    }

    private void startZoomies(ServerWorld world) {
        zoomies = 200;
        broadcastNear(Text.literal(catName() + " раптом зривається з місця і носиться по хаті!").formatted(Formatting.GOLD));
        for (ChromaCatEntity other : world.getEntitiesByClass(ChromaCatEntity.class, getBoundingBox().expand(16), c -> c != this)) {
            if (other.isBonded() && other.getMode() == MODE_WANDER && other.random.nextBoolean()) {
                other.zoomies = 200;
                other.napTicks = 0;
                other.napPos = null;
                broadcastNear(Text.literal(other.catName() + " приєднується до забігу. П'ять хвилин божевілля!").formatted(Formatting.GOLD));
            }
        }
    }

    private void broadcastNear(Text text) {
        for (PlayerEntity p : getWorld().getPlayers()) {
            if (p.squaredDistanceTo(this) < 32 * 32) {
                p.sendMessage(text, false);
            }
        }
    }

    private void findClosedDoor(ServerWorld world) {
        for (BlockPos p : BlockPos.iterate(getBlockPos().add(-6, -1, -6), getBlockPos().add(6, 1, 6))) {
            BlockState s = world.getBlockState(p);
            boolean closedDoor = (s.getBlock() instanceof DoorBlock && !s.get(DoorBlock.OPEN))
                    || (s.isOf(ModBlocks.GUARD_DOOR) && !s.get(GuardDoorBlock.OPEN));
            if (closedDoor) {
                doorPos = p.toImmutable();
                doorMeowTicks = 300;
                return;
            }
        }
    }

    private void tickDoorMeow(ServerWorld world) {
        BlockState s = world.getBlockState(doorPos);
        boolean stillClosed = (s.getBlock() instanceof DoorBlock && !s.get(DoorBlock.OPEN))
                || (s.isOf(ModBlocks.GUARD_DOOR) && !s.get(GuardDoorBlock.OPEN));
        if (!stillClosed) {
            // Двері відчинили — і він просто йде в інший бік.
            if (random.nextBoolean()) {
                broadcastNear(Text.literal("Двері відчинено. Чіназік дивиться на них… і йде в інший бік.").formatted(Formatting.GRAY));
            }
            doorPos = null;
            anchor = getBlockPos();
            return;
        }
        if (--doorMeowTicks <= 0) {
            doorPos = null;
            return;
        }
        if (squaredDistanceTo(Vec3d.ofCenter(doorPos)) > 4) {
            if (getNavigation().isIdle()) {
                getNavigation().startMovingTo(doorPos.getX() + 0.5, doorPos.getY(), doorPos.getZ() + 0.5, 1.0);
            }
            setCatPose(POSE_NORMAL);
        } else {
            getNavigation().stop();
            getLookControl().lookAt(Vec3d.ofCenter(doorPos));
            setCatPose(POSE_LOAF);
            if (doorMeowTicks % 40 == 0) {
                playSound(SoundEvents.ENTITY_CAT_STRAY_AMBIENT, 1.6f, 0.75f);
                if (doorMeowTicks % 160 == 0) {
                    broadcastNear(Text.literal("Чіназік сидить під дверима: «МЯУ. Відкрийте, бляха.»").formatted(Formatting.GOLD));
                }
            }
        }
    }

    private void findLitter(ServerWorld world) {
        for (BlockPos p : BlockPos.iterate(getBlockPos().add(-12, -2, -12), getBlockPos().add(12, 2, 12))) {
            if (world.getBlockState(p).isOf(ModBlocks.LITTER_BOX)) {
                litterPos = p.toImmutable();
                return;
            }
        }
    }

    private void tickLitter(ServerWorld world) {
        if (!world.getBlockState(litterPos).isOf(ModBlocks.LITTER_BOX)) {
            litterPos = null;
            return;
        }
        if (squaredDistanceTo(Vec3d.ofBottomCenter(litterPos)) > 5) {
            if (getNavigation().isIdle() && !getNavigation().startMovingTo(litterPos.getX() + 0.5, litterPos.getY(), litterPos.getZ() + 0.5, 1.0)) {
                litterPos = null;
            }
            return;
        }
        // Майже влучив: поруч з лотком, а не в лоток.
        for (BlockPos p : new BlockPos[]{litterPos.north(), litterPos.south(), litterPos.east(), litterPos.west()}) {
            if (world.getBlockState(p).isAir() && world.getBlockState(p.down()).isSideSolidFullSquare(world, p.down(), net.minecraft.util.math.Direction.UP)) {
                world.setBlockState(p, ModBlocks.SURPRISE.getDefaultState(), Block.NOTIFY_ALL);
                broadcastNear(Text.literal("Чіназік залишив сюрприз біля лотка. Біля. Не в.").formatted(Formatting.DARK_GREEN));
                break;
            }
        }
        litterPos = null;
    }

    private void findBoxNap(ServerWorld world) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.iterate(getBlockPos().add(-8, -2, -8), getBlockPos().add(8, 2, 8))) {
            if (world.getBlockState(p).isOf(ModBlocks.CARDBOARD_BOX) && world.getBlockState(p.up()).isAir() && world.getBlockState(p.up(2)).isAir()) {
                double d = p.getSquaredDistance(getBlockPos());
                if (d < bestD) {
                    bestD = d;
                    best = p.up().toImmutable();
                }
            }
        }
        napPos = best;
    }

    private void tickSnuggle() {
        ServerPlayerEntity p = serverPlayer(snuggleTarget);
        if (p == null || p.getWorld() != getWorld()) {
            snuggleTarget = null;
            setNoGravity(false);
            return;
        }
        if (!p.isSleeping()) {
            if (snuggleTicks > 100) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 1200, 0));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 1));
                p.sendMessage(Text.literal("Метадонна всю ніч муркотіла на тобі. Ти тепер матрац — зате виспаний як ніколи.").formatted(Formatting.LIGHT_PURPLE), false);
            }
            snuggleTarget = null;
            setNoGravity(false);
            setCatPose(POSE_NORMAL);
            return;
        }
        if (squaredDistanceTo(p) > 4 && snuggleTicks == 0) {
            if (getNavigation().isIdle()) {
                getNavigation().startMovingTo(p, 1.1);
            }
            if (squaredDistanceTo(p) > 20 * 20) {
                snuggleTarget = null;
            }
            return;
        }
        snuggleTicks++;
        setNoGravity(true);
        getNavigation().stop();
        setVelocity(Vec3d.ZERO);
        refreshPositionAndAngles(p.getX(), p.getY() + 0.35, p.getZ(), getYaw(), getPitch());
        setCatPose(POSE_SLEEP);
        if (snuggleTicks % 100 == 1) {
            playSound(SoundEvents.ENTITY_CAT_PURR, 1f, 1.3f);
        }
    }

    // ----- persistence -----

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("CatMode", getMode());
        nbt.putBoolean("Bonded", isBonded());
        if (followTarget != null) {
            nbt.putUuid("FollowTarget", followTarget);
        }
        if (homePos != null) {
            nbt.putLong("HomePos", homePos.asLong());
        }
        nbt.putString("HomeDim", homeDim);
        if (anchor != null) {
            nbt.putLong("Anchor", anchor.asLong());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setMode(nbt.getInt("CatMode"));
        dataTracker.set(BONDED, nbt.getBoolean("Bonded"));
        followTarget = nbt.containsUuid("FollowTarget") ? nbt.getUuid("FollowTarget") : null;
        homePos = nbt.contains("HomePos") ? BlockPos.fromLong(nbt.getLong("HomePos")) : null;
        if (nbt.contains("HomeDim")) {
            homeDim = nbt.getString("HomeDim");
        }
        anchor = nbt.contains("Anchor") ? BlockPos.fromLong(nbt.getLong("Anchor")) : null;
    }

    private class BrainGoal extends Goal {
        BrainGoal() {
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return true;
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            think();
        }
    }

    /** Маленький помічник для пошуку гравців на сервері. */
    private record MinecraftServerHelper(ServerPlayerEntity by) {
        @Nullable
        ServerPlayerEntity player(UUID id) {
            return by.getServer().getPlayerManager().getPlayer(id);
        }
    }
}
