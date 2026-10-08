package com.xtri6.grimpipes.network;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.pipe.ResourceKind;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Lets generators that push power (instead of letting it be pulled) feed a pipe side set to Input.
 * Energy is passed straight through to the outputs, limited by the pipe's tier.
 */
public final class InboundEnergy implements IEnergyStorage {
    private final PipeBlockEntity pipe;
    private final int type;
    private final Direction side;

    public InboundEnergy(PipeBlockEntity pipe, int type, Direction side) {
        this.pipe = pipe;
        this.type = type;
        this.side = side;
    }

    @Nullable
    private Endpoint endpoint() {
        return pipe.isRemoved() ? null : pipe.endpoint(type, side);
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        Endpoint e = endpoint();
        if (e == null || toReceive <= 0) {
            return 0;
        }
        if (!e.active()) {
            return 0; // held off by redstone
        }
        long left = e.budgetLeft(ResourceKind.ENERGY, e.level().getGameTime(), 1, e.tier().energyPerTick());
        int allowed = (int) Math.min(toReceive, left);
        if (allowed <= 0) {
            return 0;
        }
        int moved = EnergyTransport.route(e.network(), e, allowed, simulate);
        if (!simulate) {
            e.spend(ResourceKind.ENERGY, moved);
        }
        return moved;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return 0;
    }

    @Override
    public int getMaxEnergyStored() {
        Endpoint e = endpoint();
        return e == null ? 0 : e.tier().energyPerTick();
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return endpoint() != null;
    }
}
