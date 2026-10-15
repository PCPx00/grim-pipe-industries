package com.xtri6.grimpipes.network;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.block.EndConfig;
import com.xtri6.grimpipes.pipe.FilterMode;
import com.xtri6.grimpipes.pipe.FilterMode.Decision;
import com.xtri6.grimpipes.pipe.ResourceKind;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Instant item routing: items go straight from source to destination with no entities in the pipe, so
 * there is nothing to render or tick while they travel. Destinations are served round-robin.
 */
final class ItemTransport {
    private ItemTransport() {
    }

    /** Pulls up to one operation's worth of items from an Input end. Returns how many moved. */
    static int pull(PipeNetwork net, Endpoint src) {
        IItemHandler from = src.items();
        if (from == null) {
            return 0;
        }
        int slots = from.getSlots();
        if (slots <= 0) {
            return 0;
        }
        EndConfig cfg = src.config();
        boolean canVoid = cfg != null && cfg.mode() == FilterMode.VOID;
        if (net.outputs().isEmpty() && !canVoid) {
            return 0;
        }
        int budget = src.tier().itemsPerOperation();
        int moved = 0;
        int start = Math.floorMod(src.slotCursor, slots);
        for (int i = 0; i < slots && budget > 0; i++) {
            int slot = (start + i) % slots;
            ItemStack peek = from.extractItem(slot, budget, true);
            if (peek.isEmpty()) {
                continue;
            }
            Decision decision = cfg == null ? Decision.PASS : cfg.decide(peek);
            if (decision == Decision.BLOCK) {
                continue;
            }
            int m;
            if (decision == Decision.VOID) {
                m = from.extractItem(slot, budget, false).getCount(); // deleted on purpose
            } else {
                m = moveFromSlot(net, src, from, slot, budget);
            }
            if (m > 0) {
                moved += m;
                budget -= m;
                src.slotCursor = slot;
            }
        }
        if (moved == 0) {
            src.slotCursor = 0;
        }
        src.advance();
        return moved;
    }

    private static int moveFromSlot(PipeNetwork net, Endpoint src, IItemHandler from, int slot, int budget) {
        List<Endpoint> outs = net.outputs();
        int n = outs.size();
        int moved = 0;
        for (int i = 0; i < n && budget > 0; i++) {
            Endpoint dest = src.dest(i);
            if (dest.target().equals(src.target())) {
                continue; // never loop a block back into itself
            }
            IItemHandler to = dest.items();
            if (to == null) {
                continue;
            }
            ItemStack sim = from.extractItem(slot, budget, true);
            if (sim.isEmpty()) {
                break;
            }
            if (!dest.accepts(sim)) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItem(to, sim, true);
            int canMove = sim.getCount() - rest.getCount();
            if (canMove <= 0) {
                continue;
            }
            ItemStack real = from.extractItem(slot, canMove, false);
            if (real.isEmpty()) {
                break;
            }
            ItemStack left = ItemHandlerHelper.insertItem(to, real, false);
            int done = real.getCount() - left.getCount();
            if (!left.isEmpty()) {
                giveBack(net, src, from, slot, left);
            }
            moved += done;
            budget -= done;
        }
        return moved;
    }

    /** A destination lied during simulation: put the rest back, or drop it rather than delete it. */
    private static void giveBack(PipeNetwork net, Endpoint src, IItemHandler from, int slot, ItemStack left) {
        left = from.insertItem(slot, left, false);
        if (!left.isEmpty()) {
            left = ItemHandlerHelper.insertItem(from, left, false);
        }
        if (!left.isEmpty()) {
            Block.popResource(net.level(), src.pipe().getBlockPos(), left);
        }
    }

    /**
     * Routes a stack pushed into an Input end (by a hopper, machine, etc.) to the network's outputs.
     * Returns what could not be placed.
     */
    static ItemStack route(PipeNetwork net, @Nullable Endpoint from, ItemStack stack, boolean simulate) {
        List<Endpoint> outs = net.outputs();
        int n = outs.size();
        if (n == 0 || stack.isEmpty()) {
            return stack;
        }
        int cursor = net.pushCursor[ResourceKind.ITEM.ordinal()];
        ItemStack remaining = stack.copy();
        for (int i = 0; i < n && !remaining.isEmpty(); i++) {
            Endpoint dest = net.outputAt(cursor, i);
            if (from != null && dest.target().equals(from.target())) {
                continue;
            }
            IItemHandler to = dest.items();
            if (to != null && dest.accepts(remaining)) {
                remaining = ItemHandlerHelper.insertItem(to, remaining, simulate);
            }
        }
        if (!simulate) {
            net.pushCursor[ResourceKind.ITEM.ordinal()] = cursor + 1;
        }
        return remaining;
    }
}
