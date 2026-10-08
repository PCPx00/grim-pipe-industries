package com.xtri6.grimpipes.compat;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.GrimPipes;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.network.Endpoint;
import com.xtri6.grimpipes.network.PipeNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Optional chemical support. Mekanism classes are only touched inside {@link MekanismChemicals}, which is
 * only loaded when Mekanism is installed, so the mod runs fine without it.
 */
public final class Chemicals {
    private static Bridge bridge;
    private static boolean resolved;

    private Chemicals() {
    }

    @Nullable
    public static Bridge bridge() {
        if (!resolved) {
            resolved = true;
            if (ModList.get().isLoaded("mekanism")) {
                try {
                    bridge = new MekanismChemicals();
                    GrimPipes.LOGGER.info("Mekanism found: chemical pipes enabled");
                } catch (Throwable t) {
                    GrimPipes.LOGGER.error("Mekanism found but its chemical API could not be loaded; chemical pipes disabled", t);
                    bridge = null;
                }
            }
        }
        return bridge;
    }

    public interface Bridge {
        boolean hasHandler(ServerLevel level, BlockPos pos, Direction side);

        /** Pulls chemicals from an Input end into the network's outputs. Returns the amount moved. */
        long pull(PipeNetwork net, Endpoint src, long amount);

        Object createInbound(PipeBlockEntity pipe, int type, Direction side);

        void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<PipeBlockEntity> type);
    }
}
