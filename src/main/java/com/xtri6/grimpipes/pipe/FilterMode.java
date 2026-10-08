package com.xtri6.grimpipes.pipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** How an Input end's 9-slot filter is used. */
public enum FilterMode {
    /** Only pull what is in the filter. */
    WHITELIST("whitelist", ChatFormatting.GREEN),
    /** Pull everything except what is in the filter (an empty blacklist pulls everything). */
    BLACKLIST("blacklist", ChatFormatting.RED),
    /** Pull everything; anything in the filter is deleted instead of sent on. */
    VOID("void", ChatFormatting.DARK_PURPLE);

    public static final FilterMode[] VALUES = values();

    private final String id;
    private final ChatFormatting color;

    FilterMode(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("gui.grimpipes.mode." + id).withStyle(color);
    }

    public FilterMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static FilterMode byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : BLACKLIST;
    }

    /** What to do with one thing an Input end could pull. */
    public enum Decision { PASS, BLOCK, VOID }
}
