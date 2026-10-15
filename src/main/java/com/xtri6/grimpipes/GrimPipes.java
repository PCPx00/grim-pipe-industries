package com.xtri6.grimpipes;

import com.mojang.logging.LogUtils;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.compat.Chemicals;
import com.xtri6.grimpipes.network.NetworkManager;

import org.slf4j.Logger;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(GrimPipes.MODID)
public final class GrimPipes {
    public static final String MODID = "grimpipes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GrimPipes(IEventBus modBus, ModContainer container) {
        Registration.register(modBus);
        com.xtri6.grimpipes.packet.Packets.register(modBus);
        modBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Reloading.class, GrimPipes::onConfigReload);
        modBus.addListener(this::registerCapabilities);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            com.xtri6.grimpipes.client.GrimPipesClient.init(modBus, container);
        }

        NeoForge.EVENT_BUS.addListener(GrimPipes::onLevelTick);
        NeoForge.EVENT_BUS.addListener(GrimPipes::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(GrimPipes::onServerStopped);
        NeoForge.EVENT_BUS.addListener(GrimPipes::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(GrimPipes::onLogin);

        // Dev-only test fixtures; the gametest package is stripped from the release jar.
        if (net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()) {
            try {
                Class.forName("com.xtri6.grimpipes.gametest.TestCaps").getMethod("register", IEventBus.class).invoke(null, modBus);
                if (Boolean.getBoolean("grimpipes.mekcheck")) {
                    Class.forName("com.xtri6.grimpipes.gametest.MekanismServerCheck").getMethod("register", IEventBus.class).invoke(null, modBus);
                }
                if (Boolean.getBoolean("grimpipes.compat")) {
                    Class.forName("com.xtri6.grimpipes.gametest.CompatServerCheck").getMethod("register", IEventBus.class).invoke(null, modBus);
                }
                if (Boolean.getBoolean("grimpipes.smoke")) {
                    Class.forName("com.xtri6.grimpipes.gametest.ClientSmokeCheck").getMethod("register", IEventBus.class).invoke(null, modBus);
                }
                if (Boolean.getBoolean("grimpipes.shots")) {
                    Class.forName("com.xtri6.grimpipes.gametest.ShowcaseHook").getMethod("register", IEventBus.class).invoke(null, modBus);
                }
            } catch (ReflectiveOperationException ignored) {
                // not present in release builds
            }
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Pipes accept pushes only on sides set to Input; everything else returns null so nothing
        // mistakes a pipe for a storage block.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, Registration.PIPE_ENTITY.get(), PipeBlockEntity::inboundItems);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, Registration.PIPE_ENTITY.get(), PipeBlockEntity::inboundFluids);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, Registration.PIPE_ENTITY.get(), PipeBlockEntity::inboundEnergy);
        if (Chemicals.bridge() != null) {
            Chemicals.bridge().registerCapabilities(event, Registration.PIPE_ENTITY.get());
        }
    }

    /** The single place pipe logic runs: once per server level per tick, never per pipe. */
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NetworkManager existing = NetworkManager.getIfPresent(level);
            if (existing != null) {
                existing.tick();
            }
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NetworkManager.remove(level);
        }
    }

    /**
     * Sneak-clicks Minecraft would normally skip: other mods' wrenches (c:tools/wrench), and full blocks used as facades.
     */
    private static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown() || !event.getLevel().getBlockState(event.getPos()).is(Registration.PIPE.get())) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = event.getItemStack();
        boolean wrench = stack.is(Registration.WRENCH_TAG);
        boolean facade = stack.getItem() instanceof net.minecraft.world.item.BlockItem bi
                && !(stack.getItem() instanceof com.xtri6.grimpipes.item.PipeItem)
                && event.getLevel().getBlockEntity(event.getPos()) instanceof PipeBlockEntity be && be.facade() == null
                && com.xtri6.grimpipes.block.PipeBlock.canBeFacade(bi.getBlock().defaultBlockState(), event.getLevel(), event.getPos());
        if (wrench || facade) {
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
        }
    }

    /** Sends the server's pipe speeds so screens and tooltips match them. */
    private static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.xtri6.grimpipes.packet.Packets.sendRates(player);
        }
    }

    private static void onConfigReload(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (event.getConfig().getSpec() == Config.SPEC && server != null) {
            server.execute(com.xtri6.grimpipes.packet.Packets::sendRatesToAll); // config reloads arrive off-thread
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        NetworkManager.clearAll();
    }
}
