package com.xtri6.grimpipes.network;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.Config;
import com.xtri6.grimpipes.block.EndConfig;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.pipe.Distribution;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.ResourceKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * One pipe end touching a non-pipe block. Holds lazily-created capability caches for the block it
 * touches, round-robin cursors, idle backoff and per-tick budgets for things pushed into the pipe.
 */
public final class Endpoint {
    private static final int KINDS = ResourceKind.values().length;

    private final PipeNetwork network;
    private final PipeBlockEntity pipe;
    private final int type;
    private final Direction side;
    private final BlockPos target;
    private final boolean input;

    private BlockCapabilityCache<IItemHandler, Direction> itemCache;
    private BlockCapabilityCache<IFluidHandler, Direction> fluidCache;
    private BlockCapabilityCache<IEnergyStorage, Direction> energyCache;
    /** Owned by the chemical bridge (a Mekanism capability cache when Mekanism is installed). */
    public Object chemicalCache;

    int cursor;
    int slotCursor;
    /** Outputs ordered by priority, then by pipe distance from this end (built on first use). */
    private Endpoint[] nearest;
    private final long[] nextRun = new long[KINDS];
    private final int[] backoff = new int[KINDS];
    private final long[] budgetWindow = new long[KINDS];
    private final long[] budgetUsed = new long[KINDS];

    Endpoint(PipeNetwork network, PipeBlockEntity pipe, int type, Direction side, boolean input) {
        this.network = network;
        this.pipe = pipe;
        this.type = type;
        this.side = side;
        this.target = pipe.getBlockPos().relative(side);
        this.input = input;
        for (int i = 0; i < KINDS; i++) {
            budgetWindow[i] = -1;
        }
    }

    public PipeNetwork network() {
        return network;
    }

    public PipeBlockEntity pipe() {
        return pipe;
    }

    public int type() {
        return type;
    }

    /** Direction from the pipe to the block it touches. */
    public Direction side() {
        return side;
    }

    public BlockPos target() {
        return target;
    }

    public boolean isInput() {
        return input;
    }

    /** Tier of this end's own speed card (Hollow without one). */
    public PipeTier tier() {
        return pipe.endTier(type, side);
    }

    /** Input ends: whether redstone currently lets this end run. */
    public boolean active() {
        EndConfig cfg = config();
        return cfg == null || cfg.redstone().allows(pipe.isPowered());
    }

    /** Output ends: higher priority is filled first. */
    public int priority() {
        EndConfig cfg = config();
        return cfg == null ? 0 : cfg.priority();
    }

    /** Output ends: whether this end's filter takes the item. */
    public boolean accepts(net.minecraft.world.item.ItemStack stack) {
        EndConfig cfg = config();
        return cfg == null || cfg.accepts(stack);
    }

    /** Output ends: whether this end's filter takes the fluid. */
    public boolean accepts(net.neoforged.neoforge.fluids.FluidStack fluid) {
        EndConfig cfg = config();
        return cfg == null || cfg.accepts(fluid);
    }

    /** Input ends: how pulled things are spread over outputs with the same priority. */
    public Distribution distribution() {
        EndConfig cfg = config();
        return cfg == null ? Distribution.ROUND_ROBIN : cfg.distribution();
    }

    /** The i-th output to try for something this end pulled, honoring priority and distribution. */
    public Endpoint dest(int i) {
        if (distribution() == Distribution.NEAREST) {
            return nearest()[i];
        }
        return network.outputAt(cursor, i);
    }

    /** Output {@code i} of priority group {@code g}, in this end's distribution order. */
    public Endpoint groupDest(int g, int i) {
        if (distribution() == Distribution.NEAREST) {
            return nearest()[network.groupStart(g) + i];
        }
        return network.groupOutput(g, cursor, i);
    }

    /** Moves on to the next output after an operation. */
    public void advance() {
        switch (distribution()) {
            case ROUND_ROBIN -> cursor++;
            case RANDOM -> cursor = java.util.concurrent.ThreadLocalRandom.current().nextInt(1 << 20);
            case NEAREST -> {
            }
        }
    }

    private Endpoint[] nearest() {
        if (nearest == null || nearest.length != network.outputs().size()) {
            it.unimi.dsi.fastutil.longs.Long2IntMap dist = network.distancesFrom(pipe);
            Endpoint[] arr = network.outputs().toArray(new Endpoint[0]);
            // Stable sort keeps the priority-group order the network already sorted by.
            java.util.Arrays.sort(arr, (a, b) -> {
                int p = Integer.compare(b.priority(), a.priority());
                if (p != 0) {
                    return p;
                }
                return Integer.compare(dist.getOrDefault(a.pipe.getBlockPos().asLong(), Integer.MAX_VALUE),
                        dist.getOrDefault(b.pipe.getBlockPos().asLong(), Integer.MAX_VALUE));
            });
            nearest = arr;
        }
        return nearest;
    }

    /** This end's filter and card, or null if never configured (pulls everything). */
    @Nullable
    public EndConfig config() {
        return pipe.endConfigOrNull(type, side);
    }

    public ServerLevel level() {
        return network.level();
    }

    public boolean targetLoaded() {
        return level().isLoaded(target);
    }

    // ---- Capabilities of the touched block ----

    @Nullable
    public IItemHandler items() {
        if (!targetLoaded()) {
            return null;
        }
        if (itemCache == null) {
            itemCache = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level(), target, side.getOpposite());
        }
        return itemCache.getCapability();
    }

    @Nullable
    public IFluidHandler fluids() {
        if (!targetLoaded()) {
            return null;
        }
        if (fluidCache == null) {
            fluidCache = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, level(), target, side.getOpposite());
        }
        return fluidCache.getCapability();
    }

    @Nullable
    public IEnergyStorage energy() {
        if (!targetLoaded()) {
            return null;
        }
        if (energyCache == null) {
            energyCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level(), target, side.getOpposite());
        }
        return energyCache.getCapability();
    }

    // ---- Scheduling with idle backoff ----

    boolean ready(ResourceKind kind, long time) {
        return time >= nextRun[kind.ordinal()];
    }

    void finish(ResourceKind kind, long time, boolean moved, int interval) {
        int k = kind.ordinal();
        if (moved) {
            network.markMoved(time);
            backoff[k] = 0;
            nextRun[k] = time + interval;
        } else {
            int max = Math.max(interval, Config.IDLE_BACKOFF_MAX.get());
            backoff[k] = backoff[k] == 0 ? interval : Math.min(max, backoff[k] * 2);
            nextRun[k] = time + backoff[k];
        }
    }

    /** Wakes this end up immediately (its card or filter just changed). */
    public void wake() {
        java.util.Arrays.fill(nextRun, 0);
        java.util.Arrays.fill(backoff, 0);
    }

    // ---- Budgets for things pushed into the pipe ----

    /** How much more of {@code kind} may be pushed in during the current window. */
    long budgetLeft(ResourceKind kind, long time, int windowTicks, long perWindow) {
        int k = kind.ordinal();
        long window = time / Math.max(1, windowTicks);
        if (budgetWindow[k] != window) {
            budgetWindow[k] = window;
            budgetUsed[k] = 0;
        }
        return Math.max(0, perWindow - budgetUsed[k]);
    }

    void spend(ResourceKind kind, long amount) {
        budgetUsed[kind.ordinal()] += amount;
        if (amount > 0) {
            network.markMoved(level().getGameTime());
        }
    }
}
