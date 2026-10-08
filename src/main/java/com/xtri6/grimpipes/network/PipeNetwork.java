package com.xtri6.grimpipes.network;

import java.util.ArrayList;
import java.util.List;

import com.xtri6.grimpipes.Config;
import com.xtri6.grimpipes.compat.Chemicals;
import com.xtri6.grimpipes.pipe.PipeType;
import com.xtri6.grimpipes.pipe.ResourceKind;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;

/** One connected run of a single strand type, with its cached ends. */
public final class PipeNetwork {
    private final NetworkManager manager;
    private final PipeType type;
    private final LongOpenHashSet members = new LongOpenHashSet();
    private final List<Endpoint> inputs = new ArrayList<>();
    private final List<Endpoint> outputs = new ArrayList<>();
    private boolean destroyed;

    /** Round-robin cursors for things pushed into the network (per resource kind). */
    final int[] pushCursor = new int[ResourceKind.values().length];

    PipeNetwork(NetworkManager manager, PipeType type) {
        this.manager = manager;
        this.type = type;
    }

    public PipeType type() {
        return type;
    }

    public ServerLevel level() {
        return manager.level();
    }

    public LongOpenHashSet members() {
        return members;
    }

    public List<Endpoint> inputs() {
        return inputs;
    }

    public List<Endpoint> outputs() {
        return outputs;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    void destroy() {
        destroyed = true;
    }

    /** Output priority groups: start index and size in {@link #outputs}, highest priority first. */
    private int[] groupStart = new int[0];
    private int[] groupSize = new int[0];

    void addEndpoint(Endpoint e) {
        (e.isInput() ? inputs : outputs).add(e);
    }

    /** Sorts outputs by priority (highest first) once the network is built. */
    void finish() {
        outputs.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        List<Integer> starts = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < outputs.size(); i++) {
            if (i == 0 || outputs.get(i).priority() != outputs.get(i - 1).priority()) {
                starts.add(i);
                sizes.add(0);
            }
            sizes.set(sizes.size() - 1, sizes.get(sizes.size() - 1) + 1);
        }
        groupStart = starts.stream().mapToInt(Integer::intValue).toArray();
        groupSize = sizes.stream().mapToInt(Integer::intValue).toArray();
    }

    void tick(long time) {
        if (inputs.isEmpty()) {
            return; // nothing pulls on this network: zero work
        }
        int batch = Config.FLUID_INTERVAL.get();
        boolean batchTick = time % batch == 0;
        for (Endpoint src : inputs) {
            if (destroyed) {
                return;
            }
            if (!src.active()) {
                continue; // held off by redstone
            }
            for (ResourceKind kind : type.kinds()) {
                if (!src.ready(kind, time)) {
                    continue;
                }
                switch (kind) {
                    case ITEM -> src.finish(kind, time, ItemTransport.pull(this, src) > 0, src.tier().itemInterval());
                    case FLUID -> {
                        if (batchTick) {
                            src.finish(kind, time, FluidTransport.pull(this, src, (long) src.tier().fluidPerTick() * batch) > 0, batch);
                        }
                    }
                    case ENERGY -> src.finish(kind, time, EnergyTransport.pull(this, src, src.tier().energyPerTick()) > 0, 1);
                    case CHEMICAL -> {
                        if (batchTick && Chemicals.bridge() != null) {
                            src.finish(kind, time, Chemicals.bridge().pull(this, src, src.tier().chemicalPerTick() * batch) > 0, batch);
                        }
                    }
                }
            }
        }
    }

    public int groupCount() {
        return groupSize.length;
    }

    public int groupSize(int g) {
        return groupSize[g];
    }

    /** Output {@code i} of priority group {@code g}, rotated by {@code cursor}. */
    public Endpoint groupOutput(int g, int cursor, int i) {
        return outputs.get(groupStart[g] + Math.floorMod(cursor + i, groupSize[g]));
    }

    /**
     * The i-th destination to try: highest priority group first, and round-robin (starting at
     * {@code cursor}) among outputs that share a priority. Used by every transport.
     */
    public Endpoint outputAt(int cursor, int i) {
        for (int g = 0; g < groupSize.length; g++) {
            int size = groupSize[g];
            if (i < size) {
                return outputs.get(groupStart[g] + Math.floorMod(cursor + i, size));
            }
            i -= size;
        }
        return outputs.get(Math.floorMod(cursor + i, outputs.size()));
    }
}
