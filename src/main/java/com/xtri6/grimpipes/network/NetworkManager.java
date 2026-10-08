package com.xtri6.grimpipes.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.pipe.PipeKey;
import com.xtri6.grimpipes.pipe.PipeType;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Owns every pipe network in one level and is the only thing that ticks.
 *
 * <p>Why it stays lag-free:</p>
 * <ul>
 *     <li>Pipe blocks never tick. Work scales with the number of pipe <em>ends</em>, not pipe blocks.</li>
 *     <li>Networks are built once and cached. A change only tears down the networks it touches, and they
 *         are rebuilt lazily on the next tick, all edits in that tick batched together.</li>
 *     <li>Neighbor capabilities are cached with NeoForge's {@code BlockCapabilityCache}.</li>
 *     <li>Idle ends back off (see {@code performance.idleBackoffMaxTicks}).</li>
 *     <li>Nothing here ever loads a chunk.</li>
 * </ul>
 */
public final class NetworkManager {
    private static final Map<ServerLevel, NetworkManager> MANAGERS = Collections.synchronizedMap(new IdentityHashMap<>());

    private final ServerLevel level;
    private final Long2ObjectOpenHashMap<PipeNetwork[]> byPos = new Long2ObjectOpenHashMap<>();
    private final Set<PipeNetwork> networks = Collections.newSetFromMap(new IdentityHashMap<>());
    private final LongOpenHashSet refreshQueue = new LongOpenHashSet();
    private final LongOpenHashSet rebuildQueue = new LongOpenHashSet();
    private boolean ticking;

    private NetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static NetworkManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, NetworkManager::new);
    }

    @Nullable
    public static NetworkManager getIfPresent(ServerLevel level) {
        return MANAGERS.get(level);
    }

    public static void remove(ServerLevel level) {
        NetworkManager m = MANAGERS.remove(level);
        if (m != null) {
            m.networks.forEach(PipeNetwork::destroy);
        }
    }

    public static void clearAll() {
        synchronized (MANAGERS) {
            MANAGERS.values().forEach(m -> m.networks.forEach(PipeNetwork::destroy));
            MANAGERS.clear();
        }
    }

    public ServerLevel level() {
        return level;
    }

    // ---- Change notifications ----

    /** A pipe just loaded: re-check its neighbors next tick, then (re)join a network. */
    public void queueRefresh(BlockPos pos) {
        refreshQueue.add(pos.asLong());
        rebuildQueue.add(pos.asLong());
    }

    /** Something about this pipe changed: drop every network it was part of, rebuild next tick. */
    public void invalidate(BlockPos pos) {
        long p = pos.asLong();
        PipeNetwork[] nets = byPos.get(p);
        if (nets != null) {
            for (PipeNetwork n : nets) {
                if (n != null) {
                    destroy(n);
                }
            }
        }
        rebuildQueue.add(p);
        for (Direction d : Direction.values()) {
            rebuildQueue.add(BlockPos.offset(p, d));
        }
    }

    private void destroy(PipeNetwork net) {
        if (net.isDestroyed()) {
            return;
        }
        net.destroy();
        networks.remove(net);
        int t = net.type().ordinal();
        LongIterator it = net.members().iterator();
        while (it.hasNext()) {
            long m = it.nextLong();
            PipeNetwork[] arr = byPos.get(m);
            if (arr != null && arr[t] == net) {
                arr[t] = null;
                if (isEmpty(arr)) {
                    byPos.remove(m);
                }
            }
            rebuildQueue.add(m);
        }
    }

    private static boolean isEmpty(PipeNetwork[] arr) {
        for (PipeNetwork n : arr) {
            if (n != null) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public PipeNetwork networkAt(BlockPos pos, int type) {
        PipeNetwork[] arr = byPos.get(pos.asLong());
        return arr == null ? null : arr[type];
    }

    // ---- Tick ----

    /** Total time spent in pipe logic for this level, for the debug/performance check. */
    private long profiledNanos;
    private long profiledTicks;

    public long averageTickNanos() {
        return profiledTicks == 0 ? 0 : profiledNanos / profiledTicks;
    }

    public void resetProfile() {
        profiledNanos = 0;
        profiledTicks = 0;
    }

    public void tick() {
        if (ticking) {
            return;
        }
        ticking = true;
        long start = System.nanoTime();
        try {
            processQueues();
            long time = level.getGameTime();
            // Copy so a transfer that changes the world (and invalidates a network) can't break iteration.
            for (PipeNetwork net : new ArrayList<>(networks)) {
                if (!net.isDestroyed()) {
                    net.tick(time);
                }
            }
        } finally {
            ticking = false;
            profiledNanos += System.nanoTime() - start;
            profiledTicks++;
        }
    }

    private void processQueues() {
        if (!refreshQueue.isEmpty()) {
            long[] refresh = refreshQueue.toLongArray();
            refreshQueue.clear();
            for (long p : refresh) {
                PipeBlockEntity be = pipeAt(BlockPos.of(p));
                if (be != null) {
                    be.refreshConnections();
                }
            }
        }
        if (!rebuildQueue.isEmpty()) {
            long[] rebuild = rebuildQueue.toLongArray();
            rebuildQueue.clear();
            for (long p : rebuild) {
                PipeBlockEntity be = pipeAt(BlockPos.of(p));
                if (be == null) {
                    continue;
                }
                PipeNetwork[] arr = byPos.get(p);
                for (PipeType t : PipeType.VALUES) {
                    if (be.has(t) && (arr == null || arr[t.ordinal()] == null)) {
                        build(be, t);
                        arr = byPos.get(p);
                    }
                }
            }
        }
    }

    @Nullable
    private PipeBlockEntity pipeAt(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof PipeBlockEntity be && !be.isRemoved() ? be : null;
    }

    /** Flood-fills one strand type from a starting pipe. Never crosses into unloaded chunks. */
    private void build(PipeBlockEntity start, PipeType type) {
        int t = type.ordinal();
        PipeNetwork net = new PipeNetwork(this, type);
        ArrayDeque<PipeBlockEntity> queue = new ArrayDeque<>();
        queue.add(start);
        net.members().add(start.getBlockPos().asLong());
        while (!queue.isEmpty()) {
            PipeBlockEntity be = queue.poll();
            BlockPos pos = be.getBlockPos();
            PipeNetwork[] arr = byPos.computeIfAbsent(pos.asLong(), k -> new PipeNetwork[PipeType.COUNT]);
            if (arr[t] != null && arr[t] != net) {
                destroy(arr[t]); // stale overlap; ours replaces it
                arr = byPos.computeIfAbsent(pos.asLong(), k -> new PipeNetwork[PipeType.COUNT]);
            }
            arr[t] = net;

            PipeKey key = be.key();
            for (Direction d : Direction.values()) {
                int arm = key.arm(t, d);
                be.setEndpoint(t, d, null);
                if (arm == PipeKey.ARM_LINK) {
                    BlockPos np = pos.relative(d);
                    if (net.members().contains(np.asLong())) {
                        continue;
                    }
                    PipeBlockEntity other = pipeAt(np);
                    if (other != null && other.has(type) && other.key().arm(t, d.getOpposite()) == PipeKey.ARM_LINK) {
                        net.members().add(np.asLong());
                        queue.add(other);
                    }
                } else if (arm == PipeKey.ARM_OUTPUT || arm == PipeKey.ARM_INPUT) {
                    Endpoint e = new Endpoint(net, be, t, d, arm == PipeKey.ARM_INPUT);
                    net.addEndpoint(e);
                    be.setEndpoint(t, d, e);
                }
            }
        }
        net.finish();
        networks.add(net);
    }

    public int networkCount() {
        return networks.size();
    }
}
