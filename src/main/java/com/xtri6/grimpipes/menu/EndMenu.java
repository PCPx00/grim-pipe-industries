package com.xtri6.grimpipes.menu;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.block.EndConfig;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.item.UpgradeItem;
import com.xtri6.grimpipes.pipe.ConnectionMode;
import com.xtri6.grimpipes.pipe.FilterMode;
import com.xtri6.grimpipes.pipe.PipeType;
import com.xtri6.grimpipes.pipe.ResourceKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * GUI for one Input end: a speed card slot and (for item and fluid strands) a 9-slot ghost filter with
 * Whitelist / Blacklist / Void.
 * <pre>
 * slot 0       speed card
 * slots 1-9    filter (ghost: clicking stores a copy, the real item stays with the player), if shown
 * then         player inventory (27) and hotbar (9)
 * </pre>
 */
public class EndMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 222;
    public static final int CARD_X = 26;
    public static final int CARD_Y = 48;
    public static final int FILTER_X = 98;
    public static final int FILTER_Y = 48;
    public static final int INV_Y = 140;
    public static final int BTN_MODE = 0;

    private final BlockPos pos;
    private final PipeType type;
    private final Direction side;
    private final boolean showFilter;
    private final ContainerData data;
    @Nullable
    private final PipeBlockEntity pipe;
    private final int filterStart;
    private final int invStart;

    /** Client side, built from the open-screen packet. */
    public EndMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), PipeType.byIndex(buf.readVarInt()), Direction.from3DDataValue(buf.readVarInt()),
                null, new ItemStackHandler(1), new ItemStackHandler(EndConfig.FILTER_SLOTS), new SimpleContainerData(1));
    }

    /** Server side, backed by the real end. */
    public EndMenu(int id, Inventory inv, PipeBlockEntity pipe, PipeType type, Direction side) {
        this(id, inv, pipe.getBlockPos(), type, side, pipe, pipe.endConfig(type.ordinal(), side).card,
                pipe.endConfig(type.ordinal(), side).filter, dataFor(pipe, type, side));
    }

    private EndMenu(int id, Inventory inv, BlockPos pos, PipeType type, Direction side, @Nullable PipeBlockEntity pipe,
                    IItemHandler card, IItemHandler filter, ContainerData data) {
        super(Registration.END_MENU.get(), id);
        this.pos = pos;
        this.type = type;
        this.side = side;
        this.pipe = pipe;
        this.data = data;
        this.showFilter = hasFilter(type);

        addSlot(new CardSlot(card, 0, CARD_X, CARD_Y));
        filterStart = slots.size();
        if (showFilter) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    addSlot(new FilterSlot(filter, row * 3 + col, FILTER_X + col * 18, FILTER_Y + row * 18));
                }
            }
        }
        invStart = slots.size();
        int invX = (WIDTH - 162) / 2;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, invX + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, invX + col * 18, INV_Y + 58));
        }
        addDataSlots(data);
    }

    /** Filters apply to items and fluids; energy and chemical ends only get the card slot. */
    public static boolean hasFilter(PipeType type) {
        return type.carries(ResourceKind.ITEM) || type.carries(ResourceKind.FLUID);
    }

    private static ContainerData dataFor(PipeBlockEntity pipe, PipeType type, Direction side) {
        EndConfig cfg = pipe.endConfig(type.ordinal(), side);
        return new ContainerData() {
            @Override
            public int get(int index) {
                return cfg.mode().ordinal();
            }

            @Override
            public void set(int index, int value) {
                cfg.setMode(FilterMode.byIndex(value));
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
    }

    public PipeType type() {
        return type;
    }

    public Direction side() {
        return side;
    }

    public boolean showsFilter() {
        return showFilter;
    }

    public FilterMode mode() {
        return FilterMode.byIndex(data.get(0));
    }

    public ItemStack card() {
        return slots.get(0).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BTN_MODE && showFilter) {
            data.set(0, mode().next().ordinal());
            return true;
        }
        return false;
    }

    /** Ghost filter slots: set a single-item copy of whatever is on the cursor, or clear it. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (showFilter && slotId >= filterStart && slotId < invStart) {
            Slot slot = slots.get(slotId);
            ItemStack carried = getCarried();
            if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
                slot.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            // Card -> inventory
            if (!moveItemStackTo(stack, invStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= invStart) {
            if (stack.getItem() instanceof UpgradeItem) {
                if (!moveItemStackTo(stack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (showFilter) {
                // Shift-clicking anything else adds it to the filter (a copy; the item stays put).
                for (int i = filterStart; i < invStart; i++) {
                    if (ItemStack.isSameItem(slots.get(i).getItem(), stack)) {
                        return ItemStack.EMPTY;
                    }
                }
                for (int i = filterStart; i < invStart; i++) {
                    if (!slots.get(i).hasItem()) {
                        slots.get(i).set(stack.copyWithCount(1));
                        break;
                    }
                }
                return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (pipe == null) {
            return true;
        }
        return !pipe.isRemoved() && pipe.has(type)
                && pipe.mode(type.ordinal(), side) == ConnectionMode.INPUT
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    private static final class CardSlot extends SlotItemHandler {
        CardSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Filter entries never hold a real item, only a marker copy. */
    private static final class FilterSlot extends SlotItemHandler {
        FilterSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
