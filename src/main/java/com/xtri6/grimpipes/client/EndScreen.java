package com.xtri6.grimpipes.client;

import java.util.ArrayList;
import java.util.List;

import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.compat.Chemicals;
import com.xtri6.grimpipes.item.UpgradeItem;
import com.xtri6.grimpipes.menu.EndMenu;
import com.xtri6.grimpipes.pipe.FilterMode;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;
import com.xtri6.grimpipes.pipe.ResourceKind;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Screen for one Input end: speed card on the left (with the speeds it gives), filter and mode on the right.
 * Uses the Grim Soul Industries themes; the switch is in the top-right corner.
 */
public class EndScreen extends AbstractContainerScreen<EndMenu> {
    private Button modeButton;
    private FilterMode shownMode;

    public EndScreen(EndMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = EndMenu.WIDTH;
        this.imageHeight = EndMenu.HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 7;
        this.inventoryLabelX = (EndMenu.WIDTH - 162) / 2;
        this.inventoryLabelY = EndMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(GuiStyle.themeButton(leftPos + imageWidth - 16, topPos + 5));
        if (menu.showsFilter()) {
            modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
                        if (minecraft != null && minecraft.gameMode != null) {
                            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, EndMenu.BTN_MODE);
                        }
                    })
                    .bounds(leftPos + EndMenu.FILTER_X - 1, topPos + EndMenu.FILTER_Y + 58, 56, 14)
                    .build());
            updateMode();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateMode();
    }

    private void updateMode() {
        if (modeButton == null) {
            return;
        }
        FilterMode mode = menu.mode();
        if (mode != shownMode) {
            shownMode = mode;
            modeButton.setMessage(mode.displayName());
            modeButton.setTooltip(Tooltip.create(Component.translatable("gui.grimpipes.mode." + mode.id() + ".tooltip")));
        }
    }

    private PipeTier tier() {
        ItemStack card = menu.card();
        return card.getItem() instanceof UpgradeItem u ? u.tier() : PipeTier.HOLLOW;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (menu.getCarried().isEmpty() && hoveredSlot != null && !hoveredSlot.hasItem()) {
            int index = menu.slots.indexOf(hoveredSlot);
            if (index == 0) {
                graphics.renderTooltip(font, Component.translatable("gui.grimpipes.card.tooltip"), mouseX, mouseY);
            } else if (menu.showsFilter() && index <= 9) {
                graphics.renderTooltip(font, Component.translatable("gui.grimpipes.filter.tooltip"), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GuiStyle.panel(g, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            boolean filterSlot = menu.showsFilter() && i >= 1 && i <= 9;
            GuiStyle.slot(g, leftPos + slot.x, topPos + slot.y, filterSlot ? GuiStyle.FILTER_BG : GuiStyle.SLOT_BG);
        }
        // Card slot: accent frame, and a grayed-out card while it is empty.
        int cx = leftPos + EndMenu.CARD_X;
        int cy = topPos + EndMenu.CARD_Y;
        g.fill(cx - 2, cy - 2, cx + 18, cy - 1, GuiStyle.ACCENT);
        if (!menu.slots.get(0).hasItem()) {
            GuiStyle.ghost(g, new ItemStack(Registration.UPGRADES.get(PipeTier.WHISPERING).get()), cx, cy);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Line 1: pipe name (kept clear of the theme button). Line 2: which end this is.
        int maxTitle = imageWidth - titleLabelX - 20;
        g.drawString(font, font.plainSubstrByWidth(title.getString(), maxTitle), titleLabelX, titleLabelY, GuiStyle.TEXT_ACCENT, false);
        Component subtitle = Component.translatable("gui.grimpipes.end.subtitle",
                Component.translatable("side.grimpipes." + menu.side().getName()));
        g.drawString(font, subtitle, titleLabelX, titleLabelY + 11, GuiStyle.TEXT_DIM, false);
        g.fill(titleLabelX, titleLabelY + 22, imageWidth - 8, titleLabelY + 23, GuiStyle.ACCENT);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiStyle.TEXT_DIM, false);

        int leftCenter = EndMenu.CARD_X + 8;
        GuiStyle.centered(g, font, Component.translatable("gui.grimpipes.card"), leftCenter, EndMenu.CARD_Y - 12, GuiStyle.TEXT, false);
        PipeTier tier = tier();
        GuiStyle.centered(g, font, tier.displayName(), leftCenter, EndMenu.CARD_Y + 22, 0xFF000000 | tierTextColor(tier), false);

        // Speeds this end runs at with the current card.
        int y = EndMenu.CARD_Y + 35;
        for (Component line : rates(menu.type(), tier)) {
            GuiStyle.centered(g, font, line, leftCenter + 6, y, GuiStyle.TEXT_DIM, false);
            y += 9;
        }

        if (menu.showsFilter()) {
            GuiStyle.centered(g, font, Component.translatable("gui.grimpipes.filter"), EndMenu.FILTER_X + 26,
                    EndMenu.FILTER_Y - 12, GuiStyle.TEXT, false);
        }
    }

    /** Tier colors drawn on the light panel need to stay readable, so dark themes get the bright variant. */
    private static int tierTextColor(PipeTier tier) {
        if (GuiStyle.theme() == GuiStyle.Theme.DARK) {
            return tier.rgb();
        }
        return switch (tier) {
            case HOLLOW -> 0x5B5666;
            case WHISPERING -> 0x157A8C;
            case RESTLESS -> 0x6A2FC4;
            case WRAITHBOUND -> 0xA0258E;
            case SOULFORGED -> 0xA86A00;
        };
    }

    private static List<Component> rates(PipeType type, PipeTier tier) {
        List<Component> out = new ArrayList<>();
        try {
            if (type.carries(ResourceKind.ITEM)) {
                out.add(Component.translatable("gui.grimpipes.rate.items", fmt(tier.itemsPerOperation() * 20.0 / tier.itemInterval())));
            }
            if (type.carries(ResourceKind.FLUID)) {
                out.add(Component.translatable("gui.grimpipes.rate.fluid", fmt(tier.fluidPerTick())));
            }
            if (type.carries(ResourceKind.ENERGY)) {
                out.add(Component.translatable("gui.grimpipes.rate.energy", fmt(tier.energyPerTick())));
            }
            if (type.carries(ResourceKind.CHEMICAL) && Chemicals.bridge() != null) {
                out.add(Component.translatable("gui.grimpipes.rate.chemical", fmt(tier.chemicalPerTick())));
            }
        } catch (IllegalStateException configNotLoaded) {
            // speeds unknown until configs load
        }
        return out;
    }

    private static String fmt(double v) {
        if (v >= 1_000_000) {
            return trim(v / 1_000_000) + "M";
        }
        if (v >= 10_000) {
            return trim(v / 1_000) + "k";
        }
        return trim(v);
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
