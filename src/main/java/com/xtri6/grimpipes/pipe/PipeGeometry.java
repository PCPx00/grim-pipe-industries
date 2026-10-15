package com.xtri6.grimpipes.pipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lays out a pipe block. Used by the server for hitboxes and wrench targeting and by the client for the
 * model, so what you see is exactly what you click.
 *
 * <p>Every pipe uses one casing of the same size, whether it holds one strand or five. Strands that share
 * a block run side by side as colored bands inside that casing, always in the same order, so a bundle
 * lines up perfectly with a single pipe next to it and nothing shifts when a strand is added.</p>
 *
 * <p>All coordinates are in pixels (0-16).</p>
 */
public final class PipeGeometry {
    public static final float CASING_MIN = 5f;
    public static final float CASING_MAX = 11f;
    public static final float TIP_LENGTH = 2f;
    public static final float TIP_EXPAND = 1f;

    public enum PartKind { CORE, ARM, TIP }

    /**
     * One box of the casing. {@code types} are the strands drawn as bands in it (ascending type order),
     * {@code arms} their arm states. {@code dir} is null for the core.
     */
    public record Part(PartKind kind, @Nullable Direction dir, int[] types, int[] arms, float[] min, float[] max) {
        /** Axis the part runs along, or -1 for the core. */
        public int lengthAxis() {
            return dir == null ? -1 : dir.getAxis().ordinal();
        }

        public boolean contains(Vec3 p, float pad) {
            return p.x >= min[0] - pad && p.x <= max[0] + pad
                    && p.y >= min[1] - pad && p.y <= max[1] + pad
                    && p.z >= min[2] - pad && p.z <= max[2] + pad;
        }

        public int bands() {
            return types.length;
        }

        /** Axis that a face of this part is split into bands along. */
        public int bandAxis(Direction face) {
            int n = face.getAxis().ordinal();
            int l = lengthAxis();
            if (l >= 0 && n != l) {
                return 3 - n - l; // the axis that is neither the length nor the face normal
            }
            return n == 0 ? 2 : 0;
        }

        /** The sub-box of band {@code i} as seen on {@code face}. */
        public float[][] band(Direction face, int i) {
            int axis = bandAxis(face);
            float lo = min[axis];
            float w = (max[axis] - lo) / types.length;
            float[] bMin = min.clone();
            float[] bMax = max.clone();
            bMin[axis] = lo + w * i;
            bMax[axis] = lo + w * (i + 1);
            return new float[][]{bMin, bMax};
        }
    }

    /** What the crosshair is on: one strand of one part. */
    public record Hit(PartKind kind, @Nullable Direction dir, int type, int arm) {
    }

    public record Layout(List<Part> parts, VoxelShape shape) {
    }

    private static final Map<PipeKey, Layout> CACHE = new ConcurrentHashMap<>();
    private static final VoxelShape FALLBACK = Shapes.box(CASING_MIN / 16.0, CASING_MIN / 16.0, CASING_MIN / 16.0,
            CASING_MAX / 16.0, CASING_MAX / 16.0, CASING_MAX / 16.0);

    private PipeGeometry() {
    }

    public static Layout layout(PipeKey key) {
        key = key.withFlow(0); // flow only changes the look, not the shape
        Layout cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (CACHE.size() > 8192) {
            CACHE.clear();
        }
        Layout built = build(key);
        CACHE.put(key, built);
        return built;
    }

    public static VoxelShape shape(PipeKey key) {
        if (key.hasFacade()) {
            return Shapes.block();
        }
        return key.typeMask() == 0 ? FALLBACK : layout(key).shape();
    }

    /** Finds the strand under a hit point given in pixels relative to the block. Arms and plates win over the core. */
    @Nullable
    public static Hit pick(PipeKey key, Vec3 p) {
        Part best = null;
        for (Part part : layout(key).parts()) {
            if (part.contains(p, 0.05f) && (best == null || (best.kind() == PartKind.CORE && part.kind() != PartKind.CORE))) {
                best = part;
            }
        }
        if (best == null) {
            return null;
        }
        Direction face = nearestFace(best, p);
        int axis = best.bandAxis(face);
        double lo = best.min()[axis];
        double w = (best.max()[axis] - lo) / best.bands();
        double coord = axis == 0 ? p.x : axis == 1 ? p.y : p.z;
        int i = (int) Math.floor((coord - lo) / w);
        i = Math.max(0, Math.min(best.bands() - 1, i));
        return new Hit(best.kind(), best.dir(), best.types()[i], best.arms()[i]);
    }

    /** The face of a part the point lies on (the closest one, ignoring the faces at the part's ends). */
    private static Direction nearestFace(Part part, Vec3 p) {
        double[] c = {p.x, p.y, p.z};
        Direction best = Direction.UP;
        double bestDist = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            int a = d.getAxis().ordinal();
            if (part.kind() != PartKind.CORE && a == part.lengthAxis() && part.kind() == PartKind.ARM) {
                continue;
            }
            double plane = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? part.max()[a] : part.min()[a];
            double dist = Math.abs(c[a] - plane);
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    private static Layout build(PipeKey key) {
        List<Part> parts = new ArrayList<>();
        int own = key.typeMask();
        int[] allTypes = typesIn(own);
        if (allTypes.length > 0) {
            parts.add(new Part(PartKind.CORE, null, allTypes, new int[allTypes.length],
                    new float[]{CASING_MIN, CASING_MIN, CASING_MIN}, new float[]{CASING_MAX, CASING_MAX, CASING_MAX}));
        }
        for (Direction dir : Direction.values()) {
            int mask = 0;
            boolean link = false;
            for (int t : allTypes) {
                int arm = key.arm(t, dir);
                if (arm != PipeKey.ARM_NONE) {
                    mask |= 1 << t;
                    link |= arm == PipeKey.ARM_LINK;
                }
            }
            if (mask == 0) {
                continue;
            }
            int[] types = typesIn(mask);
            int[] arms = new int[types.length];
            for (int i = 0; i < types.length; i++) {
                arms[i] = key.arm(types[i], dir);
            }
            int axis = dir.getAxis().ordinal();
            boolean positive = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            float armStart = positive ? CASING_MAX : CASING_MIN;
            float armEnd = link ? (positive ? 16 : 0) : (positive ? 16 - TIP_LENGTH : TIP_LENGTH);

            float[] min = {CASING_MIN, CASING_MIN, CASING_MIN};
            float[] max = {CASING_MAX, CASING_MAX, CASING_MAX};
            min[axis] = Math.min(armStart, armEnd);
            max[axis] = Math.max(armStart, armEnd);
            parts.add(new Part(PartKind.ARM, dir, types, arms, min, max));

            if (!link) {
                float[] tMin = {CASING_MIN - TIP_EXPAND, CASING_MIN - TIP_EXPAND, CASING_MIN - TIP_EXPAND};
                float[] tMax = {CASING_MAX + TIP_EXPAND, CASING_MAX + TIP_EXPAND, CASING_MAX + TIP_EXPAND};
                tMin[axis] = positive ? 16 - TIP_LENGTH : 0;
                tMax[axis] = positive ? 16 : TIP_LENGTH;
                parts.add(new Part(PartKind.TIP, dir, types, arms, tMin, tMax));
            }
        }

        VoxelShape shape = Shapes.empty();
        for (Part p : parts) {
            shape = Shapes.joinUnoptimized(shape, Shapes.box(
                    p.min()[0] / 16.0, p.min()[1] / 16.0, p.min()[2] / 16.0,
                    p.max()[0] / 16.0, p.max()[1] / 16.0, p.max()[2] / 16.0), BooleanOp.OR);
        }
        return new Layout(List.copyOf(parts), parts.isEmpty() ? FALLBACK : shape.optimize());
    }

    private static int[] typesIn(int mask) {
        int[] out = new int[Integer.bitCount(mask)];
        int n = 0;
        for (int t = 0; t < PipeType.COUNT; t++) {
            if ((mask & (1 << t)) != 0) {
                out[n++] = t;
            }
        }
        return out;
    }
}
