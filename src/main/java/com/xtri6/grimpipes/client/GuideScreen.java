package com.xtri6.grimpipes.client;

import java.util.List;

import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Reaper Field Guide: a short in-game manual, one topic per page. Text lives in the lang file. */
public class GuideScreen extends Screen {
    private static final int W = 230;
    private static final int H = 200;
    private static final int PAGES = 10;
    private static int lastPage;

    private int page = lastPage;
    private int left;
    private int top;
    private Button prev;
    private Button next;

    public GuideScreen() {
        super(Component.translatable("item.grimpipes.field_guide"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(left + 8, top + H - 22, 20, 16).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(left + W - 28, top + H - 22, 20, 16).build());
        addRenderableWidget(GuiStyle.themeButton(left + W - 16, top + 5));
        updateButtons();
    }

    private void turn(int by) {
        page = Math.max(0, Math.min(PAGES - 1, page + by));
        lastPage = page;
        updateButtons();
    }

    private void updateButtons() {
        prev.active = page > 0;
        next.active = page < PAGES - 1;
    }

    private static ItemStack icon(int page) {
        return switch (page) {
            case 0 -> new ItemStack(Registration.FIELD_GUIDE.get());
            case 1 -> new ItemStack(Registration.pipeItem(PipeType.OMNI));
            case 2 -> new ItemStack(Registration.WRENCH.get());
            case 3 -> new ItemStack(Registration.UPGRADES.get(PipeTier.SOULFORGED).get());
            case 4 -> new ItemStack(Items.HOPPER);
            case 5 -> new ItemStack(Registration.TAG_FILTER.get());
            case 6 -> new ItemStack(Items.COMPARATOR);
            case 7 -> new ItemStack(Registration.IMPRINT.get());
            case 8 -> new ItemStack(Registration.SOUL_LINK.get());
            default -> new ItemStack(Items.DEEPSLATE_TILES);
        };
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GuiStyle.panel(g, left, top, left + W, top + H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left + 10;
        g.renderItem(icon(page), x, top + 6);
        g.drawString(font, Component.translatable("guide.grimpipes." + page + ".title"), x + 20, top + 10, GuiStyle.TEXT_ACCENT, false);
        g.fill(x, top + 26, left + W - 10, top + 27, GuiStyle.ACCENT);
        List<FormattedCharSequence> lines = font.split(Component.translatable("guide.grimpipes." + page + ".text"), W - 20);
        int y = top + 33;
        for (FormattedCharSequence line : lines) {
            if (y > top + H - 34) {
                break;
            }
            g.drawString(font, line, x, y, GuiStyle.TEXT, false);
            y += 10;
        }
        GuiStyle.centered(g, font, (page + 1) + " / " + PAGES, left + W / 2, top + H - 18, GuiStyle.TEXT_DIM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
