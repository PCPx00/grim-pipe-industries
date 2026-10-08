package com.xtri6.grimpipes.block;

import java.util.Optional;

import com.xtri6.grimpipes.item.UpgradeItem;
import com.xtri6.grimpipes.pipe.FilterMode;
import com.xtri6.grimpipes.pipe.FilterMode.Decision;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.RedstoneMode;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Settings for one pipe end: its speed card and its 9-slot filter. Only Input ends use them, so a
 * network needs a card only where it pulls, never on every pipe.
 */
public final class EndConfig {
    public static final int FILTER_SLOTS = 9;

    public static final int MIN_PRIORITY = -99;
    public static final int MAX_PRIORITY = 99;

    private final Runnable onCardChanged;
    private final Runnable onChanged;
    private final Runnable onRoutingChanged;
    private FilterMode mode = FilterMode.BLACKLIST;
    private RedstoneMode redstone = RedstoneMode.ALWAYS;
    private int priority;
    private boolean filterEmpty = true;
    private PipeTier tier = PipeTier.HOLLOW;

    public final ItemStackHandler card = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            ItemStack s = getStackInSlot(0);
            tier = s.getItem() instanceof UpgradeItem u ? u.tier() : PipeTier.HOLLOW;
            onCardChanged.run();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    public final ItemStackHandler filter = new ItemStackHandler(FILTER_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            refreshFilterEmpty();
            onChanged.run();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    EndConfig(Runnable onCardChanged, Runnable onChanged, Runnable onRoutingChanged) {
        this.onCardChanged = onCardChanged;
        this.onChanged = onChanged;
        this.onRoutingChanged = onRoutingChanged;
    }

    public RedstoneMode redstone() {
        return redstone;
    }

    public void setRedstone(RedstoneMode redstone) {
        this.redstone = redstone;
        onChanged.run();
    }

    /** Output ends with a higher priority are filled first. */
    public int priority() {
        return priority;
    }

    public void setPriority(int priority) {
        int clamped = Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, priority));
        if (clamped != this.priority) {
            this.priority = clamped;
            onChanged.run();
            onRoutingChanged.run();
        }
    }

    public PipeTier tier() {
        return tier;
    }

    public FilterMode mode() {
        return mode;
    }

    public void setMode(FilterMode mode) {
        this.mode = mode;
        onChanged.run();
    }

    public boolean isDefault() {
        return card.getStackInSlot(0).isEmpty() && filterEmpty && mode == FilterMode.BLACKLIST
                && redstone == RedstoneMode.ALWAYS && priority == 0;
    }

    private void refreshFilterEmpty() {
        filterEmpty = true;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (!filter.getStackInSlot(i).isEmpty()) {
                filterEmpty = false;
                return;
            }
        }
    }

    // ---- Filtering ----

    private Decision decide(boolean matches) {
        return switch (mode) {
            case WHITELIST -> matches ? Decision.PASS : Decision.BLOCK;
            case BLACKLIST -> matches ? Decision.BLOCK : Decision.PASS;
            case VOID -> matches ? Decision.VOID : Decision.PASS;
        };
    }

    public Decision decide(ItemStack stack) {
        if (filterEmpty) {
            return mode == FilterMode.WHITELIST ? Decision.BLOCK : Decision.PASS;
        }
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filter.getStackInSlot(i);
            if (!f.isEmpty() && ItemStack.isSameItem(f, stack)) {
                return decide(true);
            }
        }
        return decide(false);
    }

    /** Fluids match a filter entry that is, or contains, that fluid (a water bucket filters water). */
    public Decision decide(FluidStack fluid) {
        if (filterEmpty) {
            return mode == FilterMode.WHITELIST ? Decision.BLOCK : Decision.PASS;
        }
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filter.getStackInSlot(i);
            if (f.isEmpty()) {
                continue;
            }
            Optional<FluidStack> contained = FluidUtil.getFluidContained(f);
            if (contained.isPresent() && FluidStack.isSameFluid(contained.get(), fluid)) {
                return decide(true);
            }
        }
        return decide(false);
    }

    // ---- Output ends: what they accept ----

    private boolean matchesItem(ItemStack stack) {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filter.getStackInSlot(i);
            if (!f.isEmpty() && ItemStack.isSameItem(f, stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesFluid(FluidStack fluid) {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filter.getStackInSlot(i);
            if (!f.isEmpty()) {
                Optional<FluidStack> c = FluidUtil.getFluidContained(f);
                if (c.isPresent() && FluidStack.isSameFluid(c.get(), fluid)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether an Output end takes this item. Whitelist: only listed items. Blacklist: anything else. */
    public boolean accepts(ItemStack stack) {
        if (filterEmpty) {
            return mode != FilterMode.WHITELIST;
        }
        return mode == FilterMode.WHITELIST ? matchesItem(stack) : !matchesItem(stack);
    }

    public boolean accepts(FluidStack fluid) {
        if (filterEmpty) {
            return mode != FilterMode.WHITELIST;
        }
        return mode == FilterMode.WHITELIST ? matchesFluid(fluid) : !matchesFluid(fluid);
    }

    // ---- Save ----

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Card", card.serializeNBT(registries));
        tag.put("Filter", filter.serializeNBT(registries));
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("Redstone", redstone.ordinal());
        tag.putInt("Priority", priority);
        return tag;
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        card.deserializeNBT(registries, tag.getCompound("Card"));
        filter.deserializeNBT(registries, tag.getCompound("Filter"));
        mode = FilterMode.byIndex(tag.getInt("Mode"));
        redstone = RedstoneMode.byIndex(tag.getInt("Redstone"));
        priority = Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, tag.getInt("Priority")));
        ItemStack s = card.getStackInSlot(0);
        tier = s.getItem() instanceof UpgradeItem u ? u.tier() : PipeTier.HOLLOW;
        refreshFilterEmpty();
    }
}
