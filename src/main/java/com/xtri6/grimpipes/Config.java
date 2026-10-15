package com.xtri6.grimpipes;

import java.util.ArrayList;
import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Pipe speeds per tier. Pack devs can edit these in config/grimpipes-common.toml.
 * Tier order: hollow, whispering, restless, wraithbound, soulforged.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final String[] TIERS = {"hollow", "whispering", "restless", "wraithbound", "soulforged"};

    public static final List<ModConfigSpec.IntValue> ITEMS_PER_OP = new ArrayList<>();
    public static final List<ModConfigSpec.IntValue> ITEM_INTERVAL = new ArrayList<>();
    public static final List<ModConfigSpec.IntValue> FLUID_RATE = new ArrayList<>();
    public static final List<ModConfigSpec.IntValue> ENERGY_RATE = new ArrayList<>();
    public static final List<ModConfigSpec.IntValue> CHEMICAL_RATE = new ArrayList<>();

    private static final int[] D_ITEMS = {4, 16, 32, 64, 256};
    private static final int[] D_INTERVAL = {10, 10, 5, 2, 1};
    private static final int[] D_FLUID = {100, 400, 1_600, 6_400, 25_600};
    private static final int[] D_ENERGY = {1_000, 8_000, 64_000, 512_000, 4_096_000};
    private static final int[] D_CHEMICAL = {200, 800, 3_200, 12_800, 51_200};

    public static final ModConfigSpec.IntValue IDLE_BACKOFF_MAX;
    public static final ModConfigSpec.IntValue FLUID_INTERVAL;
    public static final ModConfigSpec.BooleanValue FLOW_ANIMATION;
    public static final ModConfigSpec.IntValue SOUL_LINK_RANGE;

    static {
        BUILDER.comment("Speeds for each pipe tier. Each value applies to ONE pipe end (one side of one pipe).").push("tiers");
        for (int i = 0; i < TIERS.length; i++) {
            BUILDER.push(TIERS[i]);
            ITEMS_PER_OP.add(BUILDER.comment("Items moved per operation by one Input end.")
                    .defineInRange("itemsPerOperation", D_ITEMS[i], 1, 4096));
            ITEM_INTERVAL.add(BUILDER.comment("Ticks between item operations (20 ticks = 1 second).")
                    .defineInRange("itemIntervalTicks", D_INTERVAL[i], 1, 200));
            FLUID_RATE.add(BUILDER.comment("Fluid moved per tick, in mB.")
                    .defineInRange("fluidPerTick", D_FLUID[i], 1, Integer.MAX_VALUE));
            ENERGY_RATE.add(BUILDER.comment("Energy moved per tick, in FE.")
                    .defineInRange("energyPerTick", D_ENERGY[i], 1, Integer.MAX_VALUE));
            CHEMICAL_RATE.add(BUILDER.comment("Mekanism chemicals moved per tick, in mB.")
                    .defineInRange("chemicalPerTick", D_CHEMICAL[i], 1, Integer.MAX_VALUE));
            BUILDER.pop();
        }
        BUILDER.pop();

        BUILDER.push("performance");
        IDLE_BACKOFF_MAX = BUILDER
                .comment("When an Input end finds nothing to move, it waits longer before trying again, up to this many ticks.",
                        "Higher = less work on idle pipes, slower to notice new items. Set to 1 to disable backoff.")
                .defineInRange("idleBackoffMaxTicks", 40, 1, 400);
        FLUID_INTERVAL = BUILDER
                .comment("Fluids and chemicals are moved in batches every this many ticks (the per-tick rate is multiplied to match).",
                        "Energy always moves every tick.")
                .defineInRange("fluidBatchTicks", 2, 1, 20);
        BUILDER.pop();

        BUILDER.push("features");
        FLOW_ANIMATION = BUILDER
                .comment("Pipes show a subtle flow animation while they are moving something.",
                        "Pipes switch it on or off at most once a second, with a 2-second hold, so it costs almost nothing.")
                .define("flowAnimation", true);
        SOUL_LINK_RANGE = BUILDER
                .comment("Furthest a Soul Link can reach, in blocks. 0 = anywhere in the same dimension.",
                        "Both linked pipes must be loaded for the link to carry anything; pipes never load chunks.")
                .defineInRange("soulLinkRange", 0, 0, 1_000_000);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
