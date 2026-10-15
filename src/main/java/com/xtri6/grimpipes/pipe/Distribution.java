package com.xtri6.grimpipes.pipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** How an Input end spreads what it pulls across Output ends that share the same priority. */
public enum Distribution {
    /** Takes turns, so every output gets its share. */
    ROUND_ROBIN("round_robin", ChatFormatting.LIGHT_PURPLE),
    /** Fills the closest output (by pipe distance) first. */
    NEAREST("nearest", ChatFormatting.AQUA),
    /** Picks a random output each time. */
    RANDOM("random", ChatFormatting.GOLD);

    public static final Distribution[] VALUES = values();

    private final String id;
    private final ChatFormatting color;

    Distribution(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("gui.grimpipes.distribution." + id).withStyle(color);
    }

    public Distribution next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static Distribution byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : ROUND_ROBIN;
    }
}
