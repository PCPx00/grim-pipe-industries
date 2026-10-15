package com.xtri6.grimpipes.client;

import java.util.ArrayList;
import java.util.List;

import com.xtri6.grimpipes.item.TagFilterItem;
import com.xtri6.grimpipes.packet.Packets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Where you type a Tag Filter's rules, with a live preview of what they match. */
public class TagFilterScreen extends Screen {
    private static final int W = 220;
    private static final int H = 176;
    private final InteractionHand hand;
    private EditBox box;
    private int left;
    private int top;
    private final List<ItemStack> preview = new ArrayList<>();
    private int matchCount;
    private String previewFor = null;

    public TagFilterScreen(InteractionHand hand) {
        super(Component.translatable("gui.grimpipes.tag_filter"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        ItemStack held = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getItemInHand(hand);
        box = new EditBox(font, left + 10, top + 34, W - 20, 16, Component.translatable("gui.grimpipes.tag_filter"));
        box.setMaxLength(TagFilterItem.MAX_LENGTH);
        box.setValue(box.getValue().isEmpty() ? TagFilterItem.patterns(held) : box.getValue());
        box.setHint(Component.literal("#c:ingots, @mekanism, *_log").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.translatable("gui.grimpipes.save"), b -> save())
                .bounds(left + W - 70, top + H - 24, 60, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + W - 134, top + H - 24, 60, 16).build());
        addRenderableWidget(GuiStyle.themeButton(left + W - 16, top + 5));
    }

    private void save() {
        PacketDistributor.sendToServer(new Packets.SetTagFilter(hand == InteractionHand.MAIN_HAND, box.getValue()));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) { // enter
            save();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void refreshPreview() {
        String text = box.getValue();
        if (text.equals(previewFor)) {
            return;
        }
        previewFor = text;
        preview.clear();
        matchCount = 0;
        if (text.isBlank()) {
            return;
        }
        ItemStack probe = new ItemStack(com.xtri6.grimpipes.Registration.TAG_FILTER.get());
        TagFilterItem.setPatterns(probe, text);
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack s = item.getDefaultInstance();
            if (!s.isEmpty() && TagFilterItem.matches(probe, s)) {
                matchCount++;
                if (preview.size() < 20) {
                    preview.add(s);
                }
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GuiStyle.panel(g, left, top, left + W, top + H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        refreshPreview();
        int x = left + 10;
        g.drawString(font, title, x, top + 7, GuiStyle.TEXT_ACCENT, false);
        g.drawString(font, Component.translatable("gui.grimpipes.tag_filter.sub"), x, top + 18, GuiStyle.TEXT_DIM, false);
        int y = top + 56;
        for (int i = 1; i <= 4; i++) {
            g.drawString(font, Component.translatable("gui.grimpipes.tag_filter.help" + i), x, y, GuiStyle.TEXT_ACCENT, false);
            g.drawString(font, Component.translatable("gui.grimpipes.tag_filter.help" + i + ".desc"), x + 64, y, GuiStyle.TEXT, false);
            y += 10;
        }
        y += 4;
        g.drawString(font, Component.translatable("gui.grimpipes.tag_filter.count", matchCount), x, y, GuiStyle.TEXT_DIM, false);
        y += 11;
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < preview.size(); i++) {
            int ix = x + (i % 10) * 20;
            int iy = y + (i / 10) * 18;
            g.renderItem(preview.get(i), ix, iy);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                hovered = preview.get(i);
            }
        }
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
