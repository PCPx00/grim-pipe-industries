package com.xtri6.grimpipes.menu;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.block.EndConfig;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.item.UpgradeItem;
import com.xtri6.grimpipes.pipe.ConnectionMode;
import com.xtri6.grimpipes.pipe.FilterMode;
import com.xtri6.grimpipes.pipe.PipeType;
import com.xtri6.grimpipes.pipe.RedstoneMode;
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
 * GUI for one pipe end.
 * <ul>
 *     <li>Input ends: speed card slot, filter (Whitelist / Blacklist / Void) and redstone control.</li>
 *     <li>Output ends: priority and filter (Whitelist / Blacklist).</li>
 * </ul>
 * Filters only exist on strands that carry items or fluids.
 * <pre>
 * [Input only] slot 0     speed card
 * next 9 slots            filter (ghost: clicking stores a copy, the real item stays with the player), if shown
 * then                    player inventory (27) and hotbar (9)
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
    public static final int BTN_REDSTONE = 1;
    public static final int BTN_PRIORITY_DOWN = 2;
    public static final int BTN_PRIORITY_UP = 3;
    public static final int BTN_PRIORITY_DOWN_10 = 4;
    public static final int BTN_PRIORITY_UP_10 = 5;

    private static final int DATA_MODE = 0;
    private static final int DATA_REDSTONE = 1;
    private static final int DATA_PRIORITY = 2;

    private final BlockPos pos;
    private final PipeType type;
    private final Direction side;
    private final boolean input;
    private final boolean showFilter;
    private final ContainerData data;
    @Nullable
    private final PipeBlockEntity pipe;
    private final int filterStart;
    private final int invStart;

    /** Client side, built from the open-screen packet. */
    public EndMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), PipeType.byIndex(buf.readVarInt()), Direction.from3DDataValue(buf.readVarInt()),
                buf.readBoolean(), null, new ItemStackHandler(1), new ItemStackHandler(EndConfig.FILTER_SLOTS), new SimpleContainerData(3));
    }

    /** Server side, backed by the real end. */
    public EndMenu(int id, Inventory inv, PipeBlockEntity pipe, PipeType type, Direction side) {
        this(id, inv, pipe.getBlockPos(), type, side, pipe.mode(type.ordinal(), side) == ConnectionMode.INPUT, pipe,
                pipe.endConfig(type.ordinal(), side).card, pipe.endConfig(type.ordinal(), side).filter,
                dataFor(pipe.endConfig(type.ordinal(), side)));
    }

    private EndMenu(int id, Inventory inv, BlockPos pos, PipeType type, Direction side, boolean input,
                    @Nullable PipeBlockEntity pipe, IItemHandler card, IItemHandler filter, ContainerData data) {
        super(Registration.END_MENU.get(), id);
        this.pos = pos;
        this.type = type;
        this.side = side;
        this.input = input;
        this.pipe = pipe;
        this.data = data;
        this.showFilter = hasFilter(type);

        if (input) {
            addSlot(new CardSlot(card, 0, CARD_X, CARD_Y));
        }
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

    /** Filters apply to items and fluids; energy and chemical ends have none. */
    public static boolean hasFilter(PipeType type) {
        return type.carries(ResourceKind.ITEM) || type.carries(ResourceKind.FLUID);
    }

    /** Opens the screen packet for an end (server side). */
    public static void writeOpenData(RegistryFriendlyByteBuf buf, PipeBlockEntity pipe, PipeType type, Direction side) {
        buf.writeBlockPos(pipe.getBlockPos());
        buf.writeVarInt(type.ordinal());
        buf.writeVarInt(side.get3DDataValue());
        buf.writeBoolean(pipe.mode(type.ordinal(), side) == ConnectionMode.INPUT);
    }

    private static ContainerData dataFor(EndConfig cfg) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_MODE -> cfg.mode().ordinal();
                    case DATA_REDSTONE -> cfg.redstone().ordinal();
                    default -> cfg.priority();
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case DATA_MODE -> cfg.setMode(FilterMode.byIndex(value));
                    case DATA_REDSTONE -> cfg.setRedstone(RedstoneMode.byIndex(value));
                    default -> cfg.setPriority(value);
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    public PipeType type() {
        return type;
    }

    public Direction side() {
        return side;
    }

    public boolean isInput() {
        return input;
    }

    public boolean showsFilter() {
        return showFilter;
    }

    public int filterStart() {
        return filterStart;
    }

    public FilterMode mode() {
        return FilterMode.byIndex(data.get(DATA_MODE));
    }

    public RedstoneMode redstone() {
        return RedstoneMode.byIndex(data.get(DATA_REDSTONE));
    }

    /** Priority, read as a signed short (the vanilla data sync sends shorts). */
    public int priority() {
        return (short) data.get(DATA_PRIORITY);
    }

    public ItemStack card() {
        return input ? slots.get(0).getItem() : ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BTN_MODE -> {
                if (!showFilter) {
                    return false;
                }
                FilterMode next = mode().next();
                if (!input && next == FilterMode.VOID) {
                    next = next.next(); // Output ends never void
                }
                data.set(DATA_MODE, next.ordinal());
            }
            case BTN_REDSTONE -> {
                if (!input) {
                    return false;
                }
                data.set(DATA_REDSTONE, redstone().next().ordinal());
            }
            case BTN_PRIORITY_DOWN -> data.set(DATA_PRIORITY, priority() - 1);
            case BTN_PRIORITY_UP -> data.set(DATA_PRIORITY, priority() + 1);
            case BTN_PRIORITY_DOWN_10 -> data.set(DATA_PRIORITY, priority() - 10);
            case BTN_PRIORITY_UP_10 -> data.set(DATA_PRIORITY, priority() + 10);
            default -> {
                return false;
            }
        }
        return true;
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
        if (input && index == 0) {
            // Card -> inventory
            if (!moveItemStackTo(stack, invStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= invStart) {
            if (input && stack.getItem() instanceof UpgradeItem) {
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
        ConnectionMode now = pipe.mode(type.ordinal(), side);
        boolean sameKind = input ? now == ConnectionMode.INPUT : now == ConnectionMode.OUTPUT;
        return !pipe.isRemoved() && pipe.has(type) && sameKind
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
