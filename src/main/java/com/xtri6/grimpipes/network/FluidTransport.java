package com.xtri6.grimpipes.network;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.block.EndConfig;
import com.xtri6.grimpipes.pipe.FilterMode;
import com.xtri6.grimpipes.pipe.FilterMode.Decision;
import com.xtri6.grimpipes.pipe.ResourceKind;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Fluid routing. No fluid is stored in the pipes, so nothing can get stuck or mixed inside them. */
final class FluidTransport {
    private FluidTransport() {
    }

    static long pull(PipeNetwork net, Endpoint src, long amount) {
        IFluidHandler from = src.fluids();
        if (from == null) {
            return 0;
        }
        EndConfig cfg = src.config();
        boolean canVoid = cfg != null && cfg.mode() == FilterMode.VOID;
        if (net.outputs().isEmpty() && !canVoid) {
            return 0;
        }
        int budget = (int) Math.min(Integer.MAX_VALUE, amount);
        long moved = 0;
        boolean sawAny = false;
        // Go tank by tank so the filter can pick which fluid to pull.
        for (int tank = 0; tank < from.getTanks() && budget > 0; tank++) {
            FluidStack inTank = from.getFluidInTank(tank);
            if (inTank.isEmpty()) {
                continue;
            }
            sawAny = true;
            long m = pullFluid(net, src, from, cfg, inTank.copyWithAmount(budget));
            moved += m;
            budget -= (int) m;
        }
        if (!sawAny && budget > 0) {
            // Some handlers hide their tanks; fall back to whatever they will hand out.
            FluidStack any = from.drain(budget, FluidAction.SIMULATE);
            if (!any.isEmpty()) {
                moved += pullFluid(net, src, from, cfg, any);
            }
        }
        src.cursor++;
        return moved;
    }

    private static long pullFluid(PipeNetwork net, Endpoint src, IFluidHandler from, @Nullable EndConfig cfg, FluidStack want) {
        Decision decision = cfg == null ? Decision.PASS : cfg.decide(want);
        if (decision == Decision.BLOCK) {
            return 0;
        }
        FluidStack available = from.drain(want, FluidAction.SIMULATE);
        if (available.isEmpty()) {
            return 0;
        }
        if (decision == Decision.VOID) {
            return from.drain(available, FluidAction.EXECUTE).getAmount(); // deleted on purpose
        }
        long moved = 0;
        int remaining = available.getAmount();
        int n = net.outputs().size();
        for (int i = 0; i < n && remaining > 0; i++) {
            Endpoint dest = net.outputAt(src.cursor, i);
            if (dest.target().equals(src.target())) {
                continue;
            }
            IFluidHandler to = dest.fluids();
            if (to == null) {
                continue;
            }
            if (!dest.accepts(available)) {
                continue;
            }
            int accepted = to.fill(available.copyWithAmount(remaining), FluidAction.SIMULATE);
            if (accepted <= 0) {
                continue;
            }
            FluidStack drained = from.drain(available.copyWithAmount(accepted), FluidAction.EXECUTE);
            if (drained.isEmpty()) {
                break;
            }
            int filled = to.fill(drained, FluidAction.EXECUTE);
            if (filled < drained.getAmount()) {
                from.fill(drained.copyWithAmount(drained.getAmount() - filled), FluidAction.EXECUTE);
            }
            moved += filled;
            remaining -= filled;
        }
        return moved;
    }

    static int route(PipeNetwork net, @Nullable Endpoint from, FluidStack resource, FluidAction action) {
        int n = net.outputs().size();
        if (n == 0 || resource.isEmpty()) {
            return 0;
        }
        int cursor = net.pushCursor[ResourceKind.FLUID.ordinal()];
        int remaining = resource.getAmount();
        int total = 0;
        for (int i = 0; i < n && remaining > 0; i++) {
            Endpoint dest = net.outputAt(cursor, i);
            if (from != null && dest.target().equals(from.target())) {
                continue;
            }
            IFluidHandler to = dest.fluids();
            if (to == null || !dest.accepts(resource)) {
                continue;
            }
            int filled = to.fill(resource.copyWithAmount(remaining), action);
            total += filled;
            remaining -= filled;
        }
        if (action.execute()) {
            net.pushCursor[ResourceKind.FLUID.ordinal()] = cursor + 1;
        }
        return total;
    }
}
