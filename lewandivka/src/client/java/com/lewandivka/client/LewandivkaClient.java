package com.lewandivka.client;

import com.lewandivka.client.render.Renderers;
import com.lewandivka.client.sky.SkyRenderers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Client entrypoint: renderers, key bindings, HUD, the sky of Chromandivka and the packets of the server. */
public final class LewandivkaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Renderers.register();
        Keys.register();
        Hud.register();
        ClientNet.register();
        SkyRenderers.register();
        ClientTickEvents.START_CLIENT_TICK.register(Keys::tick);
        ClientTickEvents.END_CLIENT_TICK.register(Effects::tick);
    }
}
