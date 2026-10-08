package com.xtri6.grimpipes.client;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Shared look for Grim Pipe Industries screens (same themes as Grim Soul Industries). Three themes, switched with the small button in the
 * top-right corner of any pipe screen (saved in config/grimpipes-client.toml):
 * - REAPER: light gray panels with purple accents (default)
 * - VANILLA: the classic Minecraft container look
 * - DARK: gunmetal and black with purple accents
 */
public final class GuiStyle {
    public enum Theme { REAPER, VANILLA, DARK }

    // Current colors. They change when the theme changes, so they are not constants.
    public static int PANEL_BG, PANEL_EDGE, PANEL_METAL, PANEL_METAL_LIGHT, PANEL_SHADOW, ACCENT, ACCENT_GLOW;
    public static int SLOT_BG, SLOT_BORDER, SLOT_DARK_EDGE, SLOT_LIGHT_EDGE, FILTER_BG, LOCKED, GHOST;
    public static int TEXT, TEXT_ACCENT, TEXT_DIM, TEXT_OK, TEXT_WARN, TEXT_ENERGY, TEXT_IN, TEXT_OUT;
    public static int BAR_BG, BAR_FILL, BAR_FILL_LIGHT;
    private static Theme theme = Theme.REAPER;

    static {
        apply(Theme.REAPER);
    }

    private GuiStyle() {}

    public static Theme theme() {
        return theme;
    }

    public static void apply(Theme newTheme) {
        theme = newTheme;
        switch (newTheme) {
            case REAPER -> {
                PANEL_EDGE = 0xFF2B2534; PANEL_METAL = 0xFF9C98A7; PANEL_METAL_LIGHT = 0xFFCBC8D3; PANEL_SHADOW = 0xFF6E6A7A;
                PANEL_BG = 0xFFDDDBE3; ACCENT = 0xFF8A4FE0; ACCENT_GLOW = 0xFFA66BFF;
                SLOT_BG = 0xFF9F9BAA; SLOT_BORDER = 0xFFF2F0F6; SLOT_DARK_EDGE = 0xFF5D596A; SLOT_LIGHT_EDGE = 0xFFF2F0F6;
                FILTER_BG = 0xFFB59EDB; LOCKED = 0xC06A6676; GHOST = 0xB0C9C6D2;
                TEXT = 0xFF26212E; TEXT_ACCENT = 0xFF6A2FC4; TEXT_DIM = 0xFF5B5666;
                TEXT_OK = 0xFF1E7A2E; TEXT_WARN = 0xFFB25A00; TEXT_ENERGY = 0xFF9A6E00; TEXT_IN = 0xFF2A55C0; TEXT_OUT = 0xFFC0601A;
                BAR_BG = 0xFF4A4655; BAR_FILL = 0xFF8A4FE0; BAR_FILL_LIGHT = 0xFFC9A2FF;
            }
            case VANILLA -> {
                PANEL_EDGE = 0xFF000000; PANEL_METAL = 0xFFC6C6C6; PANEL_METAL_LIGHT = 0xFFFFFFFF; PANEL_SHADOW = 0xFF555555;
                PANEL_BG = 0xFFC6C6C6; ACCENT = 0xFFC6C6C6; ACCENT_GLOW = 0xFF8B8B8B;
                SLOT_BG = 0xFF8B8B8B; SLOT_BORDER = 0xFFC6C6C6; SLOT_DARK_EDGE = 0xFF373737; SLOT_LIGHT_EDGE = 0xFFFFFFFF;
                FILTER_BG = 0xFF9A8BAA; LOCKED = 0xB0555555; GHOST = 0xA08B8B8B;
                TEXT = 0xFF404040; TEXT_ACCENT = 0xFF404040; TEXT_DIM = 0xFF606060;
                TEXT_OK = 0xFF1E6E2A; TEXT_WARN = 0xFF9A4A00; TEXT_ENERGY = 0xFF7A5800; TEXT_IN = 0xFF2A4AA8; TEXT_OUT = 0xFFA8501A;
                BAR_BG = 0xFF373737; BAR_FILL = 0xFF8A4FE0; BAR_FILL_LIGHT = 0xFFC9A2FF;
            }
            case DARK -> {
                PANEL_EDGE = 0xFF0B0A0E; PANEL_METAL = 0xFF4A4853; PANEL_METAL_LIGHT = 0xFF6C6A78; PANEL_SHADOW = 0xFF2A2932;
                PANEL_BG = 0xFF19181E; ACCENT = 0xFF8A4FE0; ACCENT_GLOW = 0xFFB98CFF;
                SLOT_BG = 0xFF26242C; SLOT_BORDER = 0xFF57545F; SLOT_DARK_EDGE = 0xFF111015; SLOT_LIGHT_EDGE = 0xFF57545F;
                FILTER_BG = 0xFF34264B; LOCKED = 0xD00A090D; GHOST = 0xC0343240;
                TEXT = 0xFFE4E0EC; TEXT_ACCENT = 0xFFB98CFF; TEXT_DIM = 0xFF8E8A99;
                TEXT_OK = 0xFF9BE59B; TEXT_WARN = 0xFFFFC266; TEXT_ENERGY = 0xFFE0C23A; TEXT_IN = 0xFF7FA6FF; TEXT_OUT = 0xFFFFB066;
                BAR_BG = 0xFF0E0D12; BAR_FILL = 0xFF8A4FE0; BAR_FILL_LIGHT = 0xFFC9A2FF;
            }
        }
    }

    /** Switches to the next theme and remembers it. */
    public static void cycleTheme() {
        Theme next = Theme.values()[(theme.ordinal() + 1) % Theme.values().length];
        apply(next);
        ClientConfig.saveTheme(next);
    }

    /** The small theme switch for a screen's top-right corner. */
    public static Button themeButton(int x, int y) {
        return new ThemeButton(x, y);
    }

    private static final class ThemeButton extends Button {
        ThemeButton(int x, int y) {
            super(x, y, 10, 10, Component.empty(), b -> cycleTheme(), DEFAULT_NARRATION);
            setTooltip(Tooltip.create(Component.translatable("gui.grimpipes.theme.tooltip")));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x1 = getX(), y1 = getY(), x2 = x1 + width, y2 = y1 + height;
            g.fill(x1, y1, x2, y2, isHoveredOrFocused() ? ACCENT_GLOW : PANEL_EDGE);
            // A little palette: one swatch per theme, the current one marked.
            int[] swatches = {0xFFDDDBE3, 0xFFC6C6C6, 0xFF19181E};
            g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xFF8A4FE0);
            for (int i = 0; i < 3; i++) {
                int sx = x1 + 1 + i * 3 - (i == 2 ? 0 : 0);
                g.fill(sx, y1 + 1, sx + 3, y2 - 1, swatches[i]);
            }
            int mark = x1 + 1 + theme.ordinal() * 3;
            g.fill(mark, y2 - 3, mark + 3, y2 - 1, 0xFF8A4FE0);
        }
    }

    /** A framed panel with a purple accent strip along the top (a plain beveled panel in vanilla style). */
    public static void panel(GuiGraphics g, int x1, int y1, int x2, int y2) {
        if (theme == Theme.VANILLA) {
            g.fill(x1 + 1, y1, x2 - 1, y2, PANEL_EDGE);
            g.fill(x1, y1 + 1, x2, y2 - 1, PANEL_EDGE);
            g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, PANEL_BG);
            g.fill(x1 + 1, y1 + 1, x2 - 3, y1 + 3, PANEL_METAL_LIGHT);
            g.fill(x1 + 1, y1 + 1, x1 + 3, y2 - 3, PANEL_METAL_LIGHT);
            g.fill(x1 + 3, y2 - 3, x2 - 1, y2 - 1, PANEL_SHADOW);
            g.fill(x2 - 3, y1 + 3, x2 - 1, y2 - 1, PANEL_SHADOW);
            return;
        }
        g.fill(x1, y1, x2, y2, PANEL_EDGE);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, PANEL_METAL);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, PANEL_METAL_LIGHT);
        g.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, PANEL_SHADOW);
        g.fill(x1 + 3, y1 + 3, x2 - 3, y2 - 3, PANEL_BG);
        g.fill(x1 + 3, y1 + 3, x2 - 3, y1 + 4, ACCENT);
        // Corner rivets
        g.fill(x1 + 1, y1 + 1, x1 + 3, y1 + 3, ACCENT_GLOW);
        g.fill(x2 - 3, y1 + 1, x2 - 1, y1 + 3, ACCENT_GLOW);
        g.fill(x1 + 1, y2 - 3, x1 + 3, y2 - 1, ACCENT_GLOW);
        g.fill(x2 - 3, y2 - 3, x2 - 1, y2 - 1, ACCENT_GLOW);
    }

    /** A recessed item slot: dark top-left edge, light bottom-right edge. */
    public static void slot(GuiGraphics g, int x, int y, int bg) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_LIGHT_EDGE);
        g.fill(x - 1, y - 1, x + 16, y + 16, SLOT_DARK_EDGE);
        g.fill(x, y, x + 16, y + 16, bg);
    }

    /** A horizontal fill bar with a soft shine along its top. */
    public static void bar(GuiGraphics g, int x1, int y1, int x2, int y2, float fraction) {
        g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, SLOT_DARK_EDGE);
        g.fill(x1, y1, x2, y2, BAR_BG);
        int filled = x1 + Math.round((x2 - x1) * Math.max(0.0F, Math.min(1.0F, fraction)));
        if (filled > x1) {
            g.fill(x1, y1, filled, y2, BAR_FILL);
            g.fill(x1, y1, filled, y1 + 2, BAR_FILL_LIGHT);
        }
    }

    /** Text centered on cx, without a shadow (shadows look muddy on the light themes). */
    public static void centered(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    public static void centered(GuiGraphics g, net.minecraft.client.gui.Font font, Component text, int cx, int y, int color, boolean shadow) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, shadow);
    }

    /** Left edge of a centered 9-slot player inventory in a screen of this width. */
    public static int invX(int screenWidth) {
        return (screenWidth - 162) / 2;
    }

    /** An empty card (or core) slot: the item it takes is drawn grayed out until a real one goes in. */
    public static void ghost(GuiGraphics g, net.minecraft.world.item.ItemStack ghost, int x, int y) {
        g.renderFakeItem(ghost, x, y);
        // Items render in front of everything else in the slot, so the gray wash has to sit even further in front.
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 250.0F);
        g.fill(x, y, x + 16, y + 16, GHOST);
        g.pose().popPose();
    }

    // A lightning bolt, row by row: {y, x-from, x-to} inside a 16x16 slot.
    private static final int[][] BOLT = {
            {2, 8, 11}, {3, 7, 10}, {4, 7, 9}, {5, 6, 9}, {6, 5, 8}, {7, 5, 11}, {8, 7, 10},
            {9, 7, 9}, {10, 6, 8}, {11, 6, 7}, {12, 5, 7}, {13, 5, 6}};

    /**
     * Extras for a charge slot: a faint lightning bolt while it's empty, and a thin charge bar under
     * it while an item charges ({@code permille} 0-1000, or below 0 for unknown).
     */
    public static void chargeSlotExtras(GuiGraphics g, int x, int y, boolean hasItem, int permille) {
        if (!hasItem) {
            int color = theme == Theme.DARK ? 0x70E0C23A : 0x909A6E00;
            for (int[] row : BOLT) {
                g.fill(x + row[1], y + row[0], x + row[2], y + row[0] + 1, color);
            }
            return;
        }
        if (permille < 0) {
            return;
        }
        int filled = Math.round(16 * Math.min(1000, permille) / 1000.0F);
        g.fill(x, y + 18, x + 16, y + 20, BAR_BG);
        if (filled > 0) {
            g.fill(x, y + 18, x + filled, y + 20, permille >= 1000 ? 0xFF3FBF4F : 0xFFE0C23A);
        }
    }

    /** Short number for tight spaces: 950, 12.4K, 3.1M. */
    public static String compact(int value) {
        if (value < 10_000) {
            return String.valueOf(value);
        }
        if (value < 1_000_000) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
    }
}
