package ua.lewandivka;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.lewandivka.logic.GameEvents;
import ua.lewandivka.logic.ModCommands;
import ua.lewandivka.network.ModNetworking;
import ua.lewandivka.registry.ModBlocks;
import ua.lewandivka.registry.ModEntities;
import ua.lewandivka.registry.ModItems;
import ua.lewandivka.registry.ModVillagers;
import ua.lewandivka.registry.ModWorldgen;

public class Lewandivka implements ModInitializer {
    public static final String MOD_ID = "lewandivka";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModBlocks.init();
        ModEntities.init();
        ModItems.init();
        ModWorldgen.init();
        ModVillagers.init();
        ua.lewandivka.config.LewConfig.load();
        ModNetworking.initServer();
        ua.lewandivka.ability.Abilities.init();
        GameEvents.init();
        ModCommands.init();
        LOG.info("Левандівка: по той бік району — завантажено");
    }
}
