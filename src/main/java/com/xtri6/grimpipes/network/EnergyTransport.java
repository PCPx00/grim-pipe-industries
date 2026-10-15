package com.xtri6.grimpipes.network;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Energy routing. Each tick an Input end's energy is split evenly across outputs first, then any
 * leftover goes to whoever still has room. Pipes store no energy.
 */
final class EnergyTransport {
    private EnergyTransport() {
    }

    static int pull(PipeNetwork net, Endpoint src, int rate) {
        IEnergyStorage from = src.energy();
        if (from == null || !from.canExtract()) {
            return 0;
        }
        int available = from.extractEnergy(rate, true);
        if (available <= 0) {
            return 0;
        }
        int moved = distribute(net, src, available, from);
        src.advance();
        return moved;
    }

    /**
     * Sends up to {@code amount} FE to outputs. When {@code source} is given, energy is extracted from it
     * as it is delivered; when null (energy pushed into the pipe) it is simply delivered.
     */
    static int distribute(PipeNetwork net, @Nullable Endpoint src, int amount, @Nullable IEnergyStorage source) {
        int remaining = amount;
        int moved = 0;
        int cursor = net.pushCursor[com.xtri6.grimpipes.pipe.ResourceKind.ENERGY.ordinal()];
        boolean nearestFirst = src != null && src.distribution() == com.xtri6.grimpipes.pipe.Distribution.NEAREST;
        // Highest priority group first; inside a group, split evenly, then let anyone with room take the rest.
        for (int g = 0; g < net.groupCount() && remaining > 0; g++) {
            int n = net.groupSize(g);
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                int receivers = n;
                for (int i = 0; i < n && remaining > 0; i++) {
                    Endpoint dest = src != null ? src.groupDest(g, i) : net.groupOutput(g, cursor, i);
                    if (src != null && dest.target().equals(src.target())) {
                        receivers--;
                        continue;
                    }
                    IEnergyStorage to = dest.energy();
                    if (to == null || !to.canReceive()) {
                        receivers--;
                        continue;
                    }
                    int share = pass == 0 && !nearestFirst ? Math.max(1, remaining / Math.max(1, receivers - i)) : remaining;
                    int accepted = to.receiveEnergy(Math.min(share, remaining), true);
                    if (accepted <= 0) {
                        continue;
                    }
                    int taken = source != null ? source.extractEnergy(accepted, false) : accepted;
                    if (taken <= 0) {
                        return moved;
                    }
                    int given = to.receiveEnergy(taken, false);
                    if (given < taken && source != null) {
                        source.receiveEnergy(taken - given, false);
                    }
                    moved += given;
                    remaining -= taken;
                }
            }
        }
        return moved;
    }

    static int route(PipeNetwork net, @Nullable Endpoint from, int amount, boolean simulate) {
        int n = net.outputs().size();
        if (n == 0 || amount <= 0) {
            return 0;
        }
        if (simulate) {
            int total = 0;
            for (int i = 0; i < n && total < amount; i++) {
                Endpoint dest = net.outputAt(0, i);
                if (from != null && dest.target().equals(from.target())) {
                    continue;
                }
                IEnergyStorage to = dest.energy();
                if (to != null && to.canReceive()) {
                    total += to.receiveEnergy(amount - total, true);
                }
            }
            return total;
        }
        return distribute(net, from, amount, null);
    }
}
