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

    void addEndpoint(Endpoint e) {
        (e.isInput() ? inputs : outputs).add(e);
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

    /** Output ends in round-robin order starting at {@code cursor}, used by every transport. */
    public Endpoint outputAt(int cursor, int i) {
        return outputs.get(Math.floorMod(cursor + i, outputs.size()));
    }
}
