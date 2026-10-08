package com.xtri6.grimpipes.network;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.block.EndConfig;
import com.xtri6.grimpipes.pipe.FilterMode.Decision;
import com.xtri6.grimpipes.pipe.ResourceKind;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Exposed on a pipe side set to Input, so hoppers, droppers and machines that auto-eject can push straight
 * into the pipe. The pipe stores nothing: items are passed on immediately, limited by the pipe's tier.
 */
public final class InboundItems implements IItemHandler {
    private final PipeBlockEntity pipe;
    private final int type;
    private final Direction side;

    public InboundItems(PipeBlockEntity pipe, int type, Direction side) {
        this.pipe = pipe;
        this.type = type;
        this.side = side;
    }

    @Nullable
    private Endpoint endpoint() {
        return pipe.isRemoved() ? null : pipe.endpoint(type, side);
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        Endpoint e = endpoint();
        if (e == null || stack.isEmpty()) {
            return stack;
        }
        EndConfig cfg = e.config();
        Decision decision = cfg == null ? Decision.PASS : cfg.decide(stack);
        if (decision == Decision.BLOCK) {
            return stack;
        }
        long time = e.level().getGameTime();
        int interval = e.tier().itemInterval();
        long left = e.budgetLeft(ResourceKind.ITEM, time, interval, e.tier().itemsPerOperation());
        if (left <= 0) {
            return stack;
        }
        int allowed = (int) Math.min(stack.getCount(), left);
        ItemStack offer = stack.copyWithCount(allowed);
        ItemStack rest = decision == Decision.VOID ? ItemStack.EMPTY : ItemTransport.route(e.network(), e, offer, simulate);
        int moved = allowed - rest.getCount();
        if (!simulate) {
            e.spend(ResourceKind.ITEM, moved);
        }
        return moved <= 0 ? stack : stack.copyWithCount(stack.getCount() - moved);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        Endpoint e = endpoint();
        if (e == null) {
            return false;
        }
        EndConfig cfg = e.config();
        return cfg == null || cfg.decide(stack) != Decision.BLOCK;
    }
}
