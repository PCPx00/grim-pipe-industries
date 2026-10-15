package com.xtri6.grimpipes.pipe;

import com.xtri6.grimpipes.Config;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Pipe speed tiers, named and colored to match the Grim Soul Industries Soul Card tiers.
 * Every pipe starts Hollow; upgrades raise it. A pipe's tier sets how fast its own ends pull and push.
 */
public enum PipeTier {
    HOLLOW("hollow", ChatFormatting.GRAY, 0x9A98A8),
    WHISPERING("whispering", ChatFormatting.AQUA, 0x5EE8FF),
    RESTLESS("restless", ChatFormatting.DARK_PURPLE, 0x9A62F0),
    WRAITHBOUND("wraithbound", ChatFormatting.LIGHT_PURPLE, 0xFF6BE6),
    SOULFORGED("soulforged", ChatFormatting.GOLD, 0xFFB02E);

    public static final PipeTier[] VALUES = values();

    private final String name;
    private final ChatFormatting color;
    private final int rgb;

    PipeTier(String name, ChatFormatting color, int rgb) {
        this.name = name;
        this.color = color;
        this.rgb = rgb;
    }

    public String id() {
        return name;
    }

    public ChatFormatting color() {
        return color;
    }

    public int rgb() {
        return rgb;
    }

    public Component displayName() {
        return Component.translatable("tier.grimpipes." + name).withStyle(color);
    }

    public static PipeTier byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : HOLLOW;
    }

    // ---- Rates (from config) ----

    /** Items moved per operation from one Input end. */
    public int itemsPerOperation() {
        int s = SyncedRates.get(ordinal(), 0);
        return s >= 0 ? s : Config.ITEMS_PER_OP.get(ordinal()).get();
    }

    /** Ticks between item operations on one Input end. */
    public int itemInterval() {
        int s = SyncedRates.get(ordinal(), 1);
        return s >= 0 ? s : Config.ITEM_INTERVAL.get(ordinal()).get();
    }

    /** Fluid moved per tick (mB) by one end. */
    public int fluidPerTick() {
        int s = SyncedRates.get(ordinal(), 2);
        return s >= 0 ? s : Config.FLUID_RATE.get(ordinal()).get();
    }

    /** Energy moved per tick (FE) by one end. */
    public int energyPerTick() {
        int s = SyncedRates.get(ordinal(), 3);
        return s >= 0 ? s : Config.ENERGY_RATE.get(ordinal()).get();
    }

    /** Chemical moved per tick (mB) by one end. */
    public long chemicalPerTick() {
        int s = SyncedRates.get(ordinal(), 4);
        return s >= 0 ? s : Config.CHEMICAL_RATE.get(ordinal()).get();
    }
}
