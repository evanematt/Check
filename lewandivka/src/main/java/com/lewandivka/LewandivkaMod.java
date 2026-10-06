package com.lewandivka;

import com.lewandivka.block.GameBlocks;
import com.lewandivka.command.LewCommands;
import com.lewandivka.config.LewandivkaConfig;
import com.lewandivka.item.GameItems;
import com.lewandivka.item.GameTab;
import com.lewandivka.sound.GameSounds;
import com.lewandivka.world.dimension.DimensionRegistry;
import com.lewandivka.world.structure.Structures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint (client and dedicated server). Everything that touches the client lives in {@code LewandivkaClient}. */
public final class LewandivkaMod implements ModInitializer {

    public static final String MOD_ID = "lewandivka";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LewandivkaConfig.load();
        GameSounds.register();
        GameBlocks.register();
        GameItems.register();
        GameTab.register();
        DimensionRegistry.register();
        LewCommands.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> Structures.warmUp());
        LOGGER.info("Левандівка: по той бік району is ready");
    }
}
