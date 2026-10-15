package com.xtri6.grimpipes.pipe;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.Config;

/**
 * Pipe speeds as the server has them. A client on a server with edited speeds would otherwise show its own
 * local config in screens and tooltips. Only clients ever set this; dedicated servers always read the config.
 */
public final class SyncedRates {
    /** Per tier: items per operation, item interval, fluid, energy, chemical. */
    private static final int FIELDS = 5;
    @Nullable
    private static volatile int[] synced;

    private SyncedRates() {
    }

    public static int[] fromConfig() {
        int[] out = new int[PipeTier.VALUES.length * FIELDS];
        for (int t = 0; t < PipeTier.VALUES.length; t++) {
            out[t * FIELDS] = Config.ITEMS_PER_OP.get(t).get();
            out[t * FIELDS + 1] = Config.ITEM_INTERVAL.get(t).get();
            out[t * FIELDS + 2] = Config.FLUID_RATE.get(t).get();
            out[t * FIELDS + 3] = Config.ENERGY_RATE.get(t).get();
            out[t * FIELDS + 4] = Config.CHEMICAL_RATE.get(t).get();
        }
        return out;
    }

    /** Called on the client when the server sends its speeds. */
    public static void apply(int[] values) {
        synced = values.length == PipeTier.VALUES.length * FIELDS ? values.clone() : null;
    }

    /** Called on the client when leaving a world or server. */
    public static void clear() {
        synced = null;
    }

    /** The synced value if there is one, otherwise -1 (read the config). */
    static int get(int tier, int field) {
        int[] s = synced;
        return s == null ? -1 : s[tier * FIELDS + field];
    }
}
