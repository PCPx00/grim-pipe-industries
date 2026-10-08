package com.xtri6.grimpipes.network;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.block.EndConfig;
import com.xtri6.grimpipes.pipe.FilterMode.Decision;
import com.xtri6.grimpipes.pipe.ResourceKind;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Lets machines push fluid into a pipe side set to Input. Passed on at once, limited by tier. */
public final class InboundFluids implements IFluidHandler {
    private final PipeBlockEntity pipe;
    private final int type;
    private final Direction side;

    public InboundFluids(PipeBlockEntity pipe, int type, Direction side) {
        this.pipe = pipe;
        this.type = type;
        this.side = side;
    }

    @Nullable
    private Endpoint endpoint() {
        return pipe.isRemoved() ? null : pipe.endpoint(type, side);
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        Endpoint e = endpoint();
        return e == null ? 0 : e.tier().fluidPerTick();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return endpoint() != null;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        Endpoint e = endpoint();
        if (e == null || resource.isEmpty()) {
            return 0;
        }
        if (!e.active()) {
            return 0; // held off by redstone
        }
        EndConfig cfg = e.config();
        Decision decision = cfg == null ? Decision.PASS : cfg.decide(resource);
        if (decision == Decision.BLOCK) {
            return 0;
        }
        long left = e.budgetLeft(ResourceKind.FLUID, e.level().getGameTime(), 1, e.tier().fluidPerTick());
        int allowed = (int) Math.min(resource.getAmount(), left);
        if (allowed <= 0) {
            return 0;
        }
        int filled = decision == Decision.VOID ? allowed
                : FluidTransport.route(e.network(), e, resource.copyWithAmount(allowed), action);
        if (action.execute()) {
            e.spend(ResourceKind.FLUID, filled);
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
