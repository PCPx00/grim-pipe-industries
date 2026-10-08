package com.xtri6.grimpipes.client;

import java.util.List;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/** Writes raw block-format quads for axis-aligned boxes, with explicit per-vertex UVs. */
final class QuadBuilder {
    private QuadBuilder() {
    }

    /**
     * Adds one face of a box (coordinates in pixels).
     *
     * @param lengthAxis for arms, the axis the arm runs along: its texture u follows world position along that
     *                   axis so stripes line up from block to block. -1 stretches the texture over the face.
     * @param tint       tint index, or -1 for none
     * @param inflate    pushes the face outward by this many pixels (for overlays)
     */
    static void box(List<BakedQuad> out, TextureAtlasSprite sprite, float[] min, float[] max, Direction face,
                    int lengthAxis, int tint, float inflate) {
        float x0 = min[0] - inflate, y0 = min[1] - inflate, z0 = min[2] - inflate;
        float x1 = max[0] + inflate, y1 = max[1] + inflate, z1 = max[2] + inflate;
        float[][] v = switch (face) {
            case DOWN -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case UP -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case NORTH -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
            case SOUTH -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
            case WEST -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
            case EAST -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
        };

        int normalAxis = face.getAxis().ordinal();
        // The two in-plane axes for this face.
        int a1 = normalAxis == 0 ? 2 : 0;
        int a2 = normalAxis == 1 ? 2 : 1;
        float[] lo = {x0, y0, z0};
        float[] hi = {x1, y1, z1};

        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            float[] p = v[i];
            float u;
            float w;
            if (lengthAxis >= 0 && lengthAxis != normalAxis) {
                int cross = lengthAxis == a1 ? a2 : a1;
                u = p[lengthAxis] / 16f;
                w = norm(p[cross], lo[cross], hi[cross]);
            } else {
                u = norm(p[a1], lo[a1], hi[a1]);
                w = 1f - norm(p[a2], lo[a2], hi[a2]);
            }
            int base = i * 8;
            data[base] = Float.floatToRawIntBits(p[0] / 16f);
            data[base + 1] = Float.floatToRawIntBits(p[1] / 16f);
            data[base + 2] = Float.floatToRawIntBits(p[2] / 16f);
            data[base + 3] = 0xFFFFFFFF;
            data[base + 4] = Float.floatToRawIntBits(sprite.getU(u));
            data[base + 5] = Float.floatToRawIntBits(sprite.getV(w));
            data[base + 6] = 0;
            data[base + 7] = packNormal(face);
        }
        out.add(new BakedQuad(data, tint, face, sprite, true));
    }

    private static float norm(float value, float lo, float hi) {
        float span = hi - lo;
        return span <= 0 ? 0 : Math.max(0f, Math.min(1f, (value - lo) / span));
    }

    private static int packNormal(Direction face) {
        int x = (byte) (face.getStepX() * 127) & 0xFF;
        int y = (byte) (face.getStepY() * 127) & 0xFF;
        int z = (byte) (face.getStepZ() * 127) & 0xFF;
        return x | (y << 8) | (z << 16);
    }
}
