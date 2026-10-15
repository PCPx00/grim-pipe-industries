package com.xtri6.grimpipes.pipe;

import net.minecraft.core.Direction;

/**
 * Everything needed to draw a pipe block or build its hitbox, packed into two longs so it is cheap to sync,
 * cache and compare.
 * <pre>
 * a: bits 0-4   strand types present (one bit per PipeType)
 *    bits 5-34  per direction (5 bits each): types shared with a neighboring pipe on that side (layout key)
 *    bits 35-39 strand types currently moving something (flow animation)
 *    bit  40    hidden behind a facade
 * b: 2 bits per (type, direction): 0 none, 1 link to another pipe, 2 end in Output mode, 3 end in Input mode
 * c, d: 3 bits per (type, direction): speed-card tier of that end (slots 0-20 in c, 21-29 in d)
 * </pre>
 */
public record PipeKey(long a, long b, long c, long d) {
    public static final int ARM_NONE = 0;
    public static final int ARM_LINK = 1;
    public static final int ARM_OUTPUT = 2;
    public static final int ARM_INPUT = 3;

    public static final PipeKey EMPTY = new PipeKey(0, 0, 0, 0);

    public int typeMask() {
        return (int) (a & 31);
    }

    /** Whether this strand is moving something right now (drawn with the flow animation). */
    public boolean flowing(int type) {
        return ((a >>> (35 + type)) & 1) != 0;
    }

    public int flowMask() {
        return (int) ((a >>> 35) & 31);
    }

    public PipeKey withFlow(int mask) {
        return new PipeKey((a & ~(31L << 35)) | ((long) (mask & 31) << 35), b, c, d);
    }

    public boolean hasFacade() {
        return ((a >>> 40) & 1) != 0;
    }

    public int sharedMask(Direction dir) {
        return (int) ((a >>> (5 + 5 * dir.get3DDataValue())) & 31);
    }

    /** Speed-card tier of one end (0 = Hollow / no card). */
    public int endTier(int type, Direction dir) {
        int slot = type * 6 + dir.get3DDataValue();
        return slot < 21 ? (int) ((c >>> (slot * 3)) & 7) : (int) ((d >>> ((slot - 21) * 3)) & 7);
    }

    public int arm(int type, Direction dir) {
        return (int) ((b >>> ((type * 6 + dir.get3DDataValue()) * 2)) & 3);
    }

    public boolean has(PipeType type) {
        return type.isIn(typeMask());
    }

    public static final class Builder {
        private long a;
        private long b;
        private long c;
        private long d;

        public Builder types(int mask) {
            a = (a & ~31L) | (mask & 31);
            return this;
        }

        public Builder shared(Direction dir, int mask) {
            int shift = 5 + 5 * dir.get3DDataValue();
            a = (a & ~(31L << shift)) | ((long) (mask & 31) << shift);
            return this;
        }

        public Builder flow(int mask) {
            a = (a & ~(31L << 35)) | ((long) (mask & 31) << 35);
            return this;
        }

        public Builder facade(boolean hidden) {
            a = hidden ? a | (1L << 40) : a & ~(1L << 40);
            return this;
        }

        public Builder endTier(int type, Direction dir, int tier) {
            int slot = type * 6 + dir.get3DDataValue();
            if (slot < 21) {
                int shift = slot * 3;
                c = (c & ~(7L << shift)) | ((long) (tier & 7) << shift);
            } else {
                int shift = (slot - 21) * 3;
                d = (d & ~(7L << shift)) | ((long) (tier & 7) << shift);
            }
            return this;
        }

        public Builder arm(int type, Direction dir, int state) {
            int shift = (type * 6 + dir.get3DDataValue()) * 2;
            b = (b & ~(3L << shift)) | ((long) (state & 3) << shift);
            return this;
        }

        public PipeKey build() {
            return new PipeKey(a, b, c, d);
        }
    }
}
