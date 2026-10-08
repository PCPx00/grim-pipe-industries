package com.xtri6.grimpipes.gametest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Test-only (never shipped): gives a few vanilla blocks energy/fluid storage so the game tests can check
 * energy and fluid pipes without depending on another mod.
 * Copper block = 100k FE source, iron block = 1M FE sink, gold block = 10 B water source, lapis block = 20 B sink.
 */
public final class TestCaps {
    public static final Map<BlockPos, EnergyStorage> ENERGY = new ConcurrentHashMap<>();
    public static final Map<BlockPos, FluidTank> FLUID = new ConcurrentHashMap<>();

    private TestCaps() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(TestCaps::caps);
    }

    private static void caps(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) ->
                ENERGY.computeIfAbsent(pos.immutable(), p -> new EnergyStorage(1_000_000, 1_000_000, 1_000_000, 100_000)), Blocks.COPPER_BLOCK);
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) ->
                ENERGY.computeIfAbsent(pos.immutable(), p -> new EnergyStorage(1_000_000, 1_000_000, 0, 0)), Blocks.IRON_BLOCK);
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) ->
                FLUID.computeIfAbsent(pos.immutable(), p -> {
                    FluidTank t = new FluidTank(20_000);
                    t.setFluid(new FluidStack(Fluids.WATER, 10_000));
                    return t;
                }), Blocks.GOLD_BLOCK);
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) ->
                FLUID.computeIfAbsent(pos.immutable(), p -> new FluidTank(20_000)), Blocks.LAPIS_BLOCK);
        // A pretend "machine" that takes items, fluids and energy, for the multi-pipe showcase.
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) ->
                ENERGY.computeIfAbsent(pos.immutable(), p -> new EnergyStorage(1_000_000)), Blocks.BLAST_FURNACE);
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) ->
                FLUID.computeIfAbsent(pos.immutable(), p -> new FluidTank(20_000)), Blocks.BLAST_FURNACE);
    }
}
