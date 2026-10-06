package com.lewandivka;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LewandivkaMod implements ModInitializer {

    public static final String MOD_ID = "lewandivka";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Левандівка: по той бік району — starting");
    }
}
