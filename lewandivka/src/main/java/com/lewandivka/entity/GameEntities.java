package com.lewandivka.entity;

import com.lewandivka.core.registry.EntitySpec;
import com.lewandivka.core.registry.EntitySpec.Group;
import com.lewandivka.core.registry.ModEntities;
import com.lewandivka.entity.boss.CollarCollectorEntity;
import com.lewandivka.entity.boss.ColorlessHeadEntity;
import com.lewandivka.entity.boss.ConductorEntity;
import com.lewandivka.entity.boss.GarageKingEntity;
import com.lewandivka.entity.boss.LadyVortexEntity;
import com.lewandivka.entity.npc.CatEntity;
import com.lewandivka.entity.npc.NpcEntity;
import com.lewandivka.entity.projectile.SeedProjectileEntity;
import com.lewandivka.entity.projectile.WheelProjectileEntity;
import com.lewandivka.entity.vehicle.TramEntity;
import com.lewandivka.util.Ids;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registers every entity type of the catalog; the classes are chosen by the role (and for bosses by the id). */
public final class GameEntities {

    private static final Map<String, EntityType<?>> TYPES = new LinkedHashMap<>();

    private GameEntities() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> EntityType<T> type(String id) {
        EntityType<?> t = TYPES.get(id);
        if (t == null) {
            throw new IllegalArgumentException("unknown entity " + id);
        }
        return (EntityType<T>) t;
    }

    public static boolean has(String id) {
        return TYPES.containsKey(id);
    }

    public static void register() {
        for (EntitySpec spec : ModEntities.ALL) {
            EntityType<?> type = switch (spec.role) {
                case GOPNIK, SEED_THROWER, SENIOR_GOPNIK -> mob(spec, GopnikEntity::new);
                case DEBTOR, SHLAHBAUM -> mob(spec, NpcEntity::new);
                case FARE_DODGER, FARE_LEADER -> mob(spec, FareDodgerEntity::new);
                case CAT -> mob(spec, CatEntity::new);
                case MINION -> mob(spec, MinionEntity::new);
                case MARKER -> mob(spec, LeashAnchorEntity::new);
                case BOSS -> switch (spec.id) {
                    case "garage_king" -> mob(spec, GarageKingEntity::new);
                    case "collar_collector" -> mob(spec, CollarCollectorEntity::new);
                    case "lady_vortex" -> mob(spec, LadyVortexEntity::new);
                    case "conductor" -> mob(spec, ConductorEntity::new);
                    case "colorless_head" -> mob(spec, ColorlessHeadEntity::new);
                    default -> throw new IllegalStateException("no class for boss " + spec.id);
                };
                case PROJECTILE -> spec.id.equals("seed_projectile")
                        ? plain(spec, SeedProjectileEntity::new, 4) : plain(spec, WheelProjectileEntity::new, 4);
                case VEHICLE -> plain(spec, TramEntity::new, 16);
            };
            Registry.register(Registries.ENTITY_TYPE, Ids.of(spec.id), type);
            TYPES.put(spec.id, type);
        }
    }

    private static <T extends LewMob> EntityType<T> mob(EntitySpec spec, EntityType.EntityFactory<T> factory) {
        FabricEntityTypeBuilder.Mob<T> b = FabricEntityTypeBuilder.<T>createMob()
                .spawnGroup(SpawnGroup.MISC)
                .entityFactory(factory)
                .dimensions(EntityDimensions.fixed(spec.width, spec.height))
                .trackRangeBlocks(spec.boss ? 128 : 64)
                .defaultAttributes(() -> LewMob.attributes(spec));
        if (spec.fireproof) {
            b.fireImmune();
        }
        return b.build();
    }

    private static <T extends Entity> EntityType<T> plain(EntitySpec spec, EntityType.EntityFactory<T> factory, int trackChunks) {
        return FabricEntityTypeBuilder.create(SpawnGroup.MISC, factory)
                .dimensions(EntityDimensions.fixed(spec.width, spec.height))
                .trackRangeChunks(trackChunks)
                .fireImmune()
                .build();
    }

    /** Spawn eggs for every creature and monster (the models and names are generated). */
    @SuppressWarnings("unchecked")
    public static void registerSpawnEggs() {
        for (EntitySpec spec : ModEntities.ALL) {
            if (spec.group == Group.MISC) {
                continue;
            }
            EntityType<? extends MobEntity> t = (EntityType<? extends MobEntity>) TYPES.get(spec.id);
            Registry.register(Registries.ITEM, Ids.of(spec.id + "_spawn_egg"), new SpawnEggItem(t, spec.eggPrimary, spec.eggSecondary, new Item.Settings()));
        }
    }
}
