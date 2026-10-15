package com.xtri6.grimpipes.client;

import java.util.ArrayList;
import java.util.List;

import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.packet.Packets;
import com.xtri6.grimpipes.pipe.PipeKey;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Every end of one pipe block on one screen: a row per strand, a cell per side. Click an Input or Output cell
 * to open its settings, right-click it to switch between Input and Output. Opened by sneak-right-clicking a pipe
 * with an empty hand (or clicking its core, or a disguised pipe).
 */
public class OverviewScreen extends Screen {
    private static final Direction[] SIDES = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int CELL = 22;
    private static final int ROW_H = 24;
    private static final int LEFT = 104;
    private static final int TOP = 44;
    private static final int WIDTH = LEFT + CELL * 6 + 10;

    private final BlockPos pos;
    private int left;
    private int top;
    private int panelH;

    public OverviewScreen(BlockPos pos) {
        super(Component.translatable("gui.grimpipes.overview"));
        this.pos = pos;
    }

    private PipeBlockEntity pipe() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof PipeBlockEntity be ? be : null;
    }

    private List<PipeType> rows(PipeBlockEntity be) {
        return be == null ? List.of() : be.types();
    }

    @Override
    protected void init() {
        PipeBlockEntity be = pipe();
        panelH = TOP + Math.max(1, rows(be).size()) * ROW_H + 8 + footer(be).size() * 10 + 6;
        left = (width - WIDTH) / 2;
        top = (height - panelH) / 2;
        addRenderableWidget(GuiStyle.themeButton(left + WIDTH - 16, top + 5));
    }

    @Override
    public void tick() {
        PipeBlockEntity be = pipe();
        Minecraft mc = Minecraft.getInstance();
        if (be == null || mc.player == null || mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GuiStyle.panel(g, left, top, left + WIDTH, top + panelH);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        PipeBlockEntity be = pipe();
        if (be == null) {
            return;
        }
        int x0 = left + 8;
        g.drawString(font, title, x0, top + 7, GuiStyle.TEXT_ACCENT, false);
        Component sub = Component.translatable("gui.grimpipes.overview.subtitle", pos.getX(), pos.getY(), pos.getZ());
        g.drawString(font, sub, x0, top + 18, GuiStyle.TEXT_DIM, false);
        g.fill(x0, top + 29, left + WIDTH - 8, top + 30, GuiStyle.ACCENT);

        for (int i = 0; i < 6; i++) {
            GuiStyle.centered(g, font, Component.translatable("gui.grimpipes.side_short." + SIDES[i].getName()),
                    left + LEFT + i * CELL + CELL / 2, top + TOP - 10, GuiStyle.TEXT_DIM, false);
        }

        PipeKey key = be.key();
        List<Component> tooltip = null;
        List<PipeType> rows = rows(be);
        for (int r = 0; r < rows.size(); r++) {
            PipeType type = rows.get(r);
            int t = type.ordinal();
            int y = top + TOP + r * ROW_H;
            g.renderItem(new ItemStack(Registration.pipeItem(type)), x0, y + 2);
            String name = Component.translatable("item.grimpipes." + type.id() + "_pipe").getString().replace("Reaper ", "");
            g.drawString(font, font.plainSubstrByWidth(name, LEFT - 30), x0 + 20, y + 6, GuiStyle.TEXT, false);
            for (int i = 0; i < 6; i++) {
                Direction d = SIDES[i];
                int cx = left + LEFT + i * CELL;
                int arm = key.arm(t, d);
                boolean hover = mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= y && mouseY < y + ROW_H - 4;
                drawCell(g, cx, y, arm, key.endTier(t, d), hover);
                if (hover) {
                    tooltip = cellTooltip(be, type, d, arm);
                }
            }
        }

        int fy = top + TOP + Math.max(1, rows.size()) * ROW_H + 8;
        for (net.minecraft.util.FormattedCharSequence line : footer(be)) {
            g.drawString(font, line, x0, fy, GuiStyle.TEXT_DIM, false);
            fy += 10;
        }
        if (tooltip != null) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    /** Facade, link and hint lines under the grid, wrapped to the panel width. */
    private List<net.minecraft.util.FormattedCharSequence> footer(PipeBlockEntity be) {
        List<net.minecraft.util.FormattedCharSequence> out = new java.util.ArrayList<>();
        int w = WIDTH - 16;
        if (be != null && be.facade() != null) {
            out.addAll(font.split(Component.translatable("gui.grimpipes.overview.facade", be.facade().getBlock().getName()), w));
        }
        if (be != null && be.hasSoulLink()) {
            out.addAll(font.split(Component.translatable("gui.grimpipes.overview.linked").withColor(GuiStyle.TEXT_ACCENT), w));
        }
        out.addAll(font.split(Component.translatable("gui.grimpipes.overview.hint"), w));
        return out;
    }

    private void drawCell(GuiGraphics g, int x, int y, int arm, int tier, boolean hover) {
        int w = CELL - 2;
        int h = ROW_H - 4;
        int fill = switch (arm) {
            case PipeKey.ARM_INPUT -> 0xFFE0A030;
            case PipeKey.ARM_OUTPUT -> 0xFF8A4FE0;
            case PipeKey.ARM_LINK -> 0xFF6C6A78;
            default -> 0x00000000;
        };
        g.fill(x, y, x + w, y + h, hover ? GuiStyle.ACCENT_GLOW : GuiStyle.SLOT_DARK_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, GuiStyle.SLOT_BG);
        if (arm != PipeKey.ARM_NONE) {
            g.fill(x + 3, y + 3, x + w - 3, y + h - 3, fill);
        }
        String label = switch (arm) {
            case PipeKey.ARM_INPUT -> "IN";
            case PipeKey.ARM_OUTPUT -> "OUT";
            case PipeKey.ARM_LINK -> "~";
            default -> "";
        };
        if (!label.isEmpty()) {
            GuiStyle.centered(g, font, label, x + w / 2, y + 6, 0xFFFFFFFF);
        }
        if (arm == PipeKey.ARM_INPUT && tier > 0) {
            int c = 0xFF000000 | PipeTier.byIndex(tier).rgb();
            g.fill(x + w - 6, y + 2, x + w - 2, y + 6, c);
        }
    }

    private List<Component> cellTooltip(PipeBlockEntity be, PipeType type, Direction d, int arm) {
        List<Component> lines = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        Component side = Component.translatable("side.grimpipes." + d.getName());
        Component neighbor = mc.level == null ? Component.empty() : mc.level.getBlockState(pos.relative(d)).getBlock().getName();
        switch (arm) {
            case PipeKey.ARM_INPUT, PipeKey.ARM_OUTPUT -> {
                boolean input = arm == PipeKey.ARM_INPUT;
                lines.add(Component.translatable(input ? "gui.grimpipes.overview.input" : "gui.grimpipes.overview.output", neighbor)
                        .withStyle(input ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE));
                if (input) {
                    lines.add(Component.translatable("gui.grimpipes.overview.card", PipeTier.byIndex(be.key().endTier(type.ordinal(), d)).displayName())
                            .withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable("gui.grimpipes.overview.click").withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("gui.grimpipes.overview.right_click").withStyle(ChatFormatting.DARK_GRAY));
            }
            case PipeKey.ARM_LINK -> lines.add(Component.translatable("gui.grimpipes.overview.link", side).withStyle(ChatFormatting.GRAY));
            default -> lines.add(Component.translatable("gui.grimpipes.overview.none", side).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(0, Component.translatable("gui.grimpipes.overview.side", side).withStyle(ChatFormatting.WHITE));
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PipeBlockEntity be = pipe();
        if (be != null) {
            List<PipeType> rows = rows(be);
            for (int r = 0; r < rows.size(); r++) {
                int y = top + TOP + r * ROW_H;
                for (int i = 0; i < 6; i++) {
                    int cx = left + LEFT + i * CELL;
                    if (mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= y && mouseY < y + ROW_H - 4) {
                        int t = rows.get(r).ordinal();
                        Direction d = SIDES[i];
                        int arm = be.key().arm(t, d);
                        if (arm != PipeKey.ARM_INPUT && arm != PipeKey.ARM_OUTPUT) {
                            return true;
                        }
                        if (button == 1) {
                            PacketDistributor.sendToServer(new Packets.ToggleEnd(pos, t, d.get3DDataValue()));
                        } else {
                            ClientHooks.overviewReturn = pos;
                            PacketDistributor.sendToServer(new Packets.OpenEnd(pos, t, d.get3DDataValue()));
                        }
                        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
