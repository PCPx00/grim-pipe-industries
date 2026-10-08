package com.xtri6.grimpipes.compat;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.network.Endpoint;
import com.xtri6.grimpipes.network.PipeNetwork;

import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Mekanism chemical transport (gases, infuse types, pigments, slurries). Only loaded with Mekanism. */
final class MekanismChemicals implements Chemicals.Bridge {
    /** Same name and type as Mekanism's own capability, so NeoForge hands back the same instance. */
    static final BlockCapability<IChemicalHandler, Direction> CHEMICAL = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    @Override
    public boolean hasHandler(ServerLevel level, BlockPos pos, Direction side) {
        return level.getCapability(CHEMICAL, pos, side) != null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static IChemicalHandler handler(Endpoint e) {
        if (!e.targetLoaded()) {
            return null;
        }
        if (e.chemicalCache == null) {
            e.chemicalCache = BlockCapabilityCache.create(CHEMICAL, e.level(), e.target(), e.side().getOpposite());
        }
        return ((BlockCapabilityCache<IChemicalHandler, Direction>) e.chemicalCache).getCapability();
    }

    @Override
    public long pull(PipeNetwork net, Endpoint src, long amount) {
        IChemicalHandler from = handler(src);
        if (from == null) {
            return 0;
        }
        ChemicalStack available = from.extractChemical(amount, Action.SIMULATE);
        if (available.isEmpty()) {
            return 0;
        }
        long remaining = available.getAmount();
        long moved = 0;
        int n = net.outputs().size();
        int start = (int) (net.level().getGameTime() & 0xFFFF);
        for (int i = 0; i < n && remaining > 0; i++) {
            Endpoint dest = net.outputAt(start, i);
            if (dest.target().equals(src.target())) {
                continue;
            }
            IChemicalHandler to = handler(dest);
            if (to == null) {
                continue;
            }
            ChemicalStack offer = available.copyWithAmount(remaining);
            long accepted = remaining - to.insertChemical(offer, Action.SIMULATE).getAmount();
            if (accepted <= 0) {
                continue;
            }
            ChemicalStack taken = from.extractChemical(available.copyWithAmount(accepted), Action.EXECUTE);
            if (taken.isEmpty()) {
                break;
            }
            ChemicalStack left = to.insertChemical(taken, Action.EXECUTE);
            if (!left.isEmpty()) {
                from.insertChemical(left, Action.EXECUTE);
            }
            long done = taken.getAmount() - left.getAmount();
            moved += done;
            remaining -= done;
        }
        return moved;
    }

    static ChemicalStack route(PipeNetwork net, Endpoint from, ChemicalStack stack, Action action) {
        int n = net.outputs().size();
        ChemicalStack remaining = stack.copy();
        for (int i = 0; i < n && !remaining.isEmpty(); i++) {
            Endpoint dest = net.outputAt(0, i);
            if (dest.target().equals(from.target())) {
                continue;
            }
            IChemicalHandler to = handler(dest);
            if (to != null) {
                remaining = to.insertChemical(remaining, action);
            }
        }
        return remaining;
    }

    @Override
    public Object createInbound(PipeBlockEntity pipe, int type, Direction side) {
        return new Inbound(pipe, type, side);
    }

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<PipeBlockEntity> type) {
        event.registerBlockEntity(CHEMICAL, type, (be, side) -> (IChemicalHandler) be.inboundChemicals(side));
    }

    /** Accepts chemicals pushed into a pipe side set to Input. */
    private static final class Inbound implements IChemicalHandler {
        private final PipeBlockEntity pipe;
        private final int type;
        private final Direction side;
        private long window = -1;
        private long used;

        Inbound(PipeBlockEntity pipe, int type, Direction side) {
            this.pipe = pipe;
            this.type = type;
            this.side = side;
        }

        @Override
        public int getChemicalTanks() {
            return 1;
        }

        @Override
        public ChemicalStack getChemicalInTank(int tank) {
            return ChemicalStack.EMPTY;
        }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack) {
        }

        @Override
        public long getChemicalTankCapacity(int tank) {
            return pipe.endTier(type, side).chemicalPerTick();
        }

        @Override
        public boolean isValid(int tank, ChemicalStack stack) {
            return pipe.endpoint(type, side) != null;
        }

        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            Endpoint e = pipe.isRemoved() ? null : pipe.endpoint(type, side);
            if (e == null || stack.isEmpty()) {
                return stack;
            }
            long now = e.level().getGameTime();
            if (now != window) {
                window = now;
                used = 0;
            }
            long allowed = Math.min(stack.getAmount(), pipe.endTier(type, side).chemicalPerTick() - used);
            if (allowed <= 0) {
                return stack;
            }
            ChemicalStack rest = route(e.network(), e, stack.copyWithAmount(allowed), action);
            long moved = allowed - rest.getAmount();
            if (action.execute()) {
                used += moved;
            }
            return moved <= 0 ? stack : stack.copyWithAmount(stack.getAmount() - moved);
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, Action action) {
            return ChemicalStack.EMPTY;
        }
    }
}
