package com.xtri6.grimpipes.client;

import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.pipe.PipeTier;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Client-only setup, registered from the mod constructor on physical clients. */
public final class GrimPipesClient {
    private GrimPipesClient() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(GrimPipesClient::registerLoaders);
        modBus.addListener(GrimPipesClient::registerColors);
        modBus.addListener(GrimPipesClient::registerScreens);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener(ModConfigEvent.Loading.class, ClientConfig::onLoad);
        modBus.addListener(ModConfigEvent.Reloading.class, ClientConfig::onLoad);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Registration.END_MENU.get(), EndScreen::new);
    }

    public static void registerLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(PipeModelLoader.ID, new PipeModelLoader());
    }

    /** Tint index 1-5 = speed-card tier corners on Input end plates, colored like the Grim Soul Industries tiers. */
    public static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> tint >= 1 ? PipeTier.byIndex(tint - 1).rgb() : 0xFFFFFF,
                Registration.PIPE.get());
    }
}
