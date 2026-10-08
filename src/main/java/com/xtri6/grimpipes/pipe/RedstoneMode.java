package com.xtri6.grimpipes.pipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** When an Input end is allowed to run, based on a redstone signal reaching its pipe block. */
public enum RedstoneMode {
    /** Ignores redstone. */
    ALWAYS("always", ChatFormatting.GRAY),
    /** Runs only while the pipe is powered. */
    HIGH("high", ChatFormatting.RED),
    /** Stops while the pipe is powered. */
    LOW("low", ChatFormatting.DARK_RED);

    public static final RedstoneMode[] VALUES = values();

    private final String id;
    private final ChatFormatting color;

    RedstoneMode(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("gui.grimpipes.redstone." + id).withStyle(color);
    }

    public RedstoneMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public boolean allows(boolean powered) {
        return switch (this) {
            case ALWAYS -> true;
            case HIGH -> powered;
            case LOW -> !powered;
        };
    }

    public static RedstoneMode byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : ALWAYS;
    }
}
