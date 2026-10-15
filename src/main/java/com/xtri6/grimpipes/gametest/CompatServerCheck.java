package com.xtri6.grimpipes.gametest;

import java.util.ArrayList;
import java.util.List;

import com.xtri6.grimpipes.GrimPipes;
import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.pipe.ConnectionMode;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Dev-only (never shipped): a big-pack compatibility check on a real dedicated server. For each block from another
 * mod it pipes something INTO the block, then pipes it back OUT into a plain sink, and logs a result table.
 * Run with: gradlew runCompatCheck -Pcompat
 */
public final class CompatServerCheck {
    enum Kind { ITEM, FLUID, ENERGY }

    record Case(String block, Kind kind) {
    }

    /** Blocks that are expected to store things on their own (no network or multiblock needed). */
    private static final List<Case> CASES = List.of(
            new Case("create:item_vault", Kind.ITEM),
            new Case("create:depot", Kind.ITEM),
            new Case("create:fluid_tank", Kind.FLUID),
            new Case("create:basin", Kind.FLUID),
            new Case("ae2:interface", Kind.ITEM),
            new Case("ae2:energy_acceptor", Kind.ENERGY),
            new Case("refinedstorage:controller", Kind.ENERGY),
            new Case("mekanism:basic_bin", Kind.ITEM),
            new Case("mekanism:basic_fluid_tank", Kind.FLUID),
            new Case("mekanism:basic_energy_cube", Kind.ENERGY),
            new Case("mekanism:energized_smelter", Kind.ENERGY),
            new Case("grimsoul:xp_tank", Kind.FLUID),
            new Case("minecraft:barrel", Kind.ITEM));

    private static final BlockPos BASE = new BlockPos(0, 150, 64);
    private static int ticks = -1;
    private static final List<Case> active = new ArrayList<>();
    private static final List<String> report = new ArrayList<>();

    private CompatServerCheck() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(CompatServerCheck::started);
        NeoForge.EVENT_BUS.addListener(CompatServerCheck::tick);
    }

    private static BlockPos origin(int i) {
        return BASE.offset(0, 0, i * 4);
    }

    private static void place(ServerLevel level, BlockPos pos, Block block) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        ItemStack stack = new ItemStack(block.asItem());
        block.setPlacedBy(level, pos, level.getBlockState(pos), FakePlayerFactory.getMinecraft(level), stack);
        var placed = level.getBlockEntity(pos);
        if (placed != null) {
            placed.applyComponentsFromItemStack(stack); // what a real placement does (Mekanism keeps its default side config here)
            placed.setChanged();
        }
    }

    private static PipeBlockEntity pipe(ServerLevel level, BlockPos pos, PipeType type) {
        level.setBlockAndUpdate(pos, Registration.PIPE.get().defaultBlockState());
        PipeBlockEntity be = (PipeBlockEntity) level.getBlockEntity(pos);
        be.addType(type);
        return be;
    }

    private static PipeType pipeFor(Kind kind) {
        return switch (kind) {
            case ITEM -> PipeType.ITEM;
            case FLUID -> PipeType.FLUID;
            case ENERGY -> PipeType.ENERGY;
        };
    }

    /** Source block | in-pipe | in-pipe | TARGET | out-pipe | out-pipe | sink, along +x. */
    private static void started(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        for (int x = -1; x <= 0; x++) {
            for (int z = 3; z <= 8; z++) {
                level.setChunkForced(x, z, true); // keep the test area loaded (only this dev check ever loads chunks)
            }
        }
        for (int i = 0; i < CASES.size(); i++) {
            Case c = CASES.get(i);
            ResourceLocation id = ResourceLocation.parse(c.block());
            if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                report.add(String.format("%-28s %-6s SKIPPED (mod not installed)", c.block(), c.kind()));
                continue;
            }
            BlockPos o = origin(i);
            for (int x = -1; x <= 7; x++) {
                for (int y = -1; y <= 2; y++) {
                    for (int z = -1; z <= 1; z++) {
                        level.setBlockAndUpdate(o.offset(x, y, z), y == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
            }
            switch (c.kind()) {
                case ITEM -> {
                    level.setBlockAndUpdate(o, Blocks.CHEST.defaultBlockState());
                    ((Container) level.getBlockEntity(o)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    level.setBlockAndUpdate(o.east(6), Blocks.CHEST.defaultBlockState());
                }
                case FLUID -> {
                    if (c.block().equals("grimsoul:xp_tank")) {
                        // XP tanks only hold Liquid Experience, so the source is a second tank full of it.
                        place(level, o, BuiltInRegistries.BLOCK.get(id));
                        IFluidHandler src = level.getCapability(Capabilities.FluidHandler.BLOCK, o, null);
                        var xp = BuiltInRegistries.FLUID.get(ResourceLocation.parse("grimsoul:liquid_experience"));
                        if (src != null) {
                            src.fill(new FluidStack(xp, 4000), IFluidHandler.FluidAction.EXECUTE);
                        }
                        level.setBlockAndUpdate(o.east(6), Blocks.LAPIS_BLOCK.defaultBlockState());
                        break;
                    }
                    level.setBlockAndUpdate(o, Blocks.GOLD_BLOCK.defaultBlockState());
                    level.setBlockAndUpdate(o.east(6), Blocks.LAPIS_BLOCK.defaultBlockState());
                }
                case ENERGY -> {
                    level.setBlockAndUpdate(o, Blocks.COPPER_BLOCK.defaultBlockState());
                    level.setBlockAndUpdate(o.east(6), Blocks.IRON_BLOCK.defaultBlockState());
                }
            }
            place(level, o.east(3), BuiltInRegistries.BLOCK.get(id));
            if (c.block().equals("ae2:energy_acceptor")) {
                // An AE2 acceptor only takes power when its grid has somewhere to store it.
                place(level, o.east(3).above(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:energy_cell")));
            }
            PipeType t = pipeFor(c.kind());
            PipeBlockEntity in = pipe(level, o.east(1), t);
            pipe(level, o.east(2), t);
            in.setMode(t.ordinal(), Direction.WEST, ConnectionMode.INPUT);
            in.swapCard(t.ordinal(), Direction.WEST, new ItemStack(Registration.UPGRADES.get(PipeTier.RESTLESS).get()));
            active.add(c);
        }
        ticks = 0;
    }

    private static long stored(ServerLevel level, BlockPos pos, Kind kind) {
        long best = 0;
        for (Direction d : new Direction[]{null, Direction.WEST, Direction.EAST, Direction.UP, Direction.NORTH}) {
            long v = 0;
            switch (kind) {
                case ITEM -> {
                    IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, d);
                    if (h != null) {
                        for (int s = 0; s < h.getSlots(); s++) {
                            v += h.getStackInSlot(s).getCount();
                        }
                    }
                }
                case FLUID -> {
                    IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, d);
                    if (h != null) {
                        for (int s = 0; s < h.getTanks(); s++) {
                            v += h.getFluidInTank(s).getAmount();
                        }
                    }
                }
                case ENERGY -> {
                    IEnergyStorage h = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, d);
                    if (h != null) {
                        v = h.getEnergyStored();
                    }
                }
            }
            best = Math.max(best, v);
        }
        return best;
    }

    private static String sides(ServerLevel level, BlockPos pos, Kind kind) {
        StringBuilder sb = new StringBuilder();
        for (Direction d : Direction.values()) {
            boolean has = switch (kind) {
                case ITEM -> level.getCapability(Capabilities.ItemHandler.BLOCK, pos, d) != null;
                case FLUID -> level.getCapability(Capabilities.FluidHandler.BLOCK, pos, d) != null;
                case ENERGY -> level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, d) != null;
            };
            if (has) {
                sb.append(d.getName().charAt(0));
            }
        }
        return sb.isEmpty() ? "none" : sb.toString();
    }

    private static final long[] inserted = new long[32];

    private static void tick(ServerTickEvent.Post event) {
        if (ticks < 0) {
            return;
        }
        ticks++;
        ServerLevel level = event.getServer().overworld();
        if (ticks == 120) {
            // Phase 2: pull back out of each target into a plain sink.
            for (Case c : active) {
                int i = CASES.indexOf(c);
                BlockPos o = origin(i);
                long have = stored(level, o.east(3), c.kind());
                if (have == 0 && c.kind() == Kind.ENERGY) {
                    // Some blocks (an AE2 acceptor) take power but don't report a buffer: count what left the source instead.
                    IEnergyStorage src = level.getCapability(Capabilities.EnergyStorage.BLOCK, o, null);
                    if (src != null) {
                        have = 100_000L - src.getEnergyStored();
                    }
                }
                inserted[i] = have;
                if (have == 0 && c.kind() == Kind.FLUID) {
                    IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, o.east(3), null);
                    if (h != null) {
                        h.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                    }
                }
                PipeType t = pipeFor(c.kind());
                PipeBlockEntity out = pipe(level, o.east(4), t);
                pipe(level, o.east(5), t);
                out.setMode(t.ordinal(), Direction.WEST, ConnectionMode.INPUT);
                out.swapCard(t.ordinal(), Direction.WEST, new ItemStack(Registration.UPGRADES.get(PipeTier.RESTLESS).get()));
                // Stop feeding so extraction is measured on its own.
                level.setBlockAndUpdate(o, Blocks.STONE.defaultBlockState());
            }
        }
        if (ticks == 260) {
            for (Case c : active) {
                int i = CASES.indexOf(c);
                BlockPos o = origin(i);
                long out = stored(level, o.east(6), c.kind());
                boolean in = inserted[i] > 0;
                report.add(String.format("%-28s %-6s into: %-4s (%d)   out of: %-4s (%d)   sides: %s",
                        c.block(), c.kind(), in ? "OK" : "--", inserted[i], out > 0 ? "OK" : "--", out,
                        sides(level, o.east(3), c.kind())));
            }
            GrimPipes.LOGGER.info("[compat] ===== Grim Pipe Industries compatibility check =====");
            for (String line : report) {
                GrimPipes.LOGGER.info("[compat] {}", line);
            }
            GrimPipes.LOGGER.info("[compat] loaded mods: {}", net.neoforged.fml.ModList.get().getMods().stream()
                    .map(m -> m.getModId() + " " + m.getVersion()).toList());
            event.getServer().halt(false);
        }
    }
}
