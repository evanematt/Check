package ua.lewandivka.registry;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.Heightmap;
import ua.lewandivka.Lewandivka;
import ua.lewandivka.entity.BorzhnykEntity;
import ua.lewandivka.entity.ChromaCatEntity;
import ua.lewandivka.entity.GopnikEntity;
import ua.lewandivka.entity.boss.CollectorEntity;
import ua.lewandivka.entity.boss.ColorlessHeadEntity;
import ua.lewandivka.entity.boss.ConductorEntity;
import ua.lewandivka.entity.boss.GarageKingEntity;
import ua.lewandivka.entity.boss.LadyWhirlEntity;

public final class ModEntities {
    public static final EntityType<ChromaCatEntity> CHINAZIK = register("chinazik", FabricEntityTypeBuilder.<ChromaCatEntity>createMob()
            .spawnGroup(SpawnGroup.MISC).entityFactory(ChromaCatEntity::new)
            .dimensions(EntityDimensions.fixed(0.8f, 0.9f)).trackRangeBlocks(96)
            .defaultAttributes(ChromaCatEntity::createChinazikAttributes).build());
    public static final EntityType<ChromaCatEntity> METADONNA = register("metadonna", FabricEntityTypeBuilder.<ChromaCatEntity>createMob()
            .spawnGroup(SpawnGroup.MISC).entityFactory(ChromaCatEntity::new)
            .dimensions(EntityDimensions.fixed(0.5f, 0.55f)).trackRangeBlocks(96)
            .defaultAttributes(ChromaCatEntity::createMetadonnaAttributes).build());

    public static final EntityType<GopnikEntity> GOPNIK = register("gopnik", FabricEntityTypeBuilder.<GopnikEntity>createMob()
            .spawnGroup(SpawnGroup.MONSTER).entityFactory(GopnikEntity::new)
            .dimensions(EntityDimensions.fixed(0.6f, 1.95f)).trackRangeBlocks(64)
            .spawnRestriction(SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark)
            .defaultAttributes(GopnikEntity::createGopnikAttributes).build());
    public static final EntityType<GopnikEntity> SHADE = register("shade", FabricEntityTypeBuilder.<GopnikEntity>createMob()
            .spawnGroup(SpawnGroup.MONSTER).entityFactory(GopnikEntity::new)
            .dimensions(EntityDimensions.fixed(0.6f, 1.95f)).trackRangeBlocks(64)
            .spawnRestriction(SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark)
            .defaultAttributes(GopnikEntity::createShadeAttributes).build());
    public static final EntityType<GopnikEntity> COLORLESS = register("colorless", FabricEntityTypeBuilder.<GopnikEntity>createMob()
            .spawnGroup(SpawnGroup.MONSTER).entityFactory(GopnikEntity::new)
            .dimensions(EntityDimensions.fixed(0.6f, 1.95f)).trackRangeBlocks(64)
            .spawnRestriction(SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark)
            .defaultAttributes(GopnikEntity::createColorlessAttributes).build());
    public static final EntityType<BorzhnykEntity> BORZHNYK = register("borzhnyk", FabricEntityTypeBuilder.<BorzhnykEntity>createMob()
            .spawnGroup(SpawnGroup.MISC).entityFactory(BorzhnykEntity::new)
            .dimensions(EntityDimensions.fixed(0.6f, 1.95f)).trackRangeBlocks(80)
            .defaultAttributes(BorzhnykEntity::createAttributes).build());

    public static final EntityType<GarageKingEntity> GARAGE_KING = register("garage_king", FabricEntityTypeBuilder.<GarageKingEntity>createMob()
            .spawnGroup(SpawnGroup.MONSTER).entityFactory(GarageKingEntity::new)
            .dimensions(EntityDimensions.fixed(1.4f, 4.3f)).trackRangeBlocks(96).fireImmune()
            .defaultAttributes(GarageKingEntity::createAttributes).build());
    public static final EntityType<LadyWhirlEntity> LADY_WHIRL = register("lady_whirl", FabricEntityTypeBuilder.<LadyWhirlEntity>createMob()
            .spawnGroup(SpawnGroup.MONSTER).entityFactory(LadyWhirlEntity::new)
            .dimensions(EntityDimensions.fixed(3.2f, 3.2f)).trackRangeBlocks(96).fireImmune()
            .defaultAttributes(LadyWhirlEntity::createAttributes).build());
    public static final EntityType<ConductorEntity> CONDUCTOR = register("conductor", FabricEntityTypeBuilder.<ConductorEntity>createMob()
            .spawnGroup(SpawnGroup.MONSTER).entityFactory(ConductorEntity::new)
            .dimensions(EntityDimensions.fixed(0.9f, 2.9f)).trackRangeBlocks(96).fireImmune()
            .defaultAttributes(ConductorEntity::createAttributes).build());
    public static final EntityType<CollectorEntity> COLLECTOR = register("collector", FabricEntityTypeBuilder.<CollectorEntity>createMob()
            .spawnGroup(SpawnGroup.MONSTER).entityFactory(CollectorEntity::new)
            .dimensions(EntityDimensions.fixed(0.8f, 2.5f)).trackRangeBlocks(96).fireImmune()
            .defaultAttributes(CollectorEntity::createAttributes).build());
    public static final EntityType<ColorlessHeadEntity> COLORLESS_HEAD = register("colorless_head", FabricEntityTypeBuilder.<ColorlessHeadEntity>createMob()
            .spawnGroup(SpawnGroup.MONSTER).entityFactory(ColorlessHeadEntity::new)
            .dimensions(EntityDimensions.fixed(1.8f, 5.8f)).trackRangeBlocks(128).fireImmune()
            .defaultAttributes(ColorlessHeadEntity::createAttributes).build());

    private static <T extends net.minecraft.entity.Entity> EntityType<T> register(String name, EntityType<T> type) {
        return Registry.register(Registries.ENTITY_TYPE, Lewandivka.id(name), type);
    }

    public static void init() {
        // Гопники на районі: вночі у звичайному світі.
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, GOPNIK, 35, 1, 3);
    }

    private ModEntities() {
    }
}
