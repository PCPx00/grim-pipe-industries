package com.xtri6.grimpipes.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.pipe.PipeGeometry;
import com.xtri6.grimpipes.pipe.PipeKey;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Builds pipe geometry from the block's {@link PipeKey}. Quads are generated once per distinct key and
 * cached, and they are baked into the chunk mesh like any normal block: zero per-frame cost.
 */
public final class PipeBakedModel implements IDynamicBakedModel {
    private static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(RenderType.cutout());

    private final TextureAtlasSprite[] strand;
    private final TextureAtlasSprite[] node;
    private final TextureAtlasSprite tipOutput;
    private final TextureAtlasSprite tipInput;
    private final TextureAtlasSprite tierOverlay;
    private final Map<PipeKey, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    public PipeBakedModel(TextureAtlasSprite[] strand, TextureAtlasSprite[] node, TextureAtlasSprite tipOutput,
                          TextureAtlasSprite tipInput, TextureAtlasSprite tierOverlay) {
        this.strand = strand;
        this.node = node;
        this.tipOutput = tipOutput;
        this.tipInput = tipInput;
        this.tierOverlay = tierOverlay;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        if (side != null) {
            return List.of(); // nothing lies on the block boundary, so nothing is face-culled
        }
        PipeKey key = data.get(PipeBlockEntity.MODEL_KEY);
        if (key == null || key.typeMask() == 0) {
            return List.of();
        }
        List<BakedQuad> quads = cache.get(key);
        if (quads == null) {
            if (cache.size() > 4096) {
                cache.clear();
            }
            quads = build(key);
            cache.put(key, quads);
        }
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return RENDER_TYPES;
    }

    private List<BakedQuad> build(PipeKey key) {
        List<BakedQuad> out = new ArrayList<>();
        List<PipeGeometry.Part> parts = PipeGeometry.layout(key).parts();
        // Core faces hidden behind an arm are skipped.
        boolean[] armOn = new boolean[6];
        for (PipeGeometry.Part part : parts) {
            if (part.kind() == PipeGeometry.PartKind.ARM) {
                armOn[part.dir().get3DDataValue()] = true;
            }
        }
        for (PipeGeometry.Part part : parts) {
            Direction dir = part.dir();
            for (Direction face : Direction.values()) {
                switch (part.kind()) {
                    case CORE -> {
                        if (armOn[face.get3DDataValue()]) {
                            continue;
                        }
                    }
                    case ARM -> {
                        if (face.getAxis() == dir.getAxis()) {
                            continue; // ends are hidden by the core, the next pipe, or the end plate
                        }
                    }
                    case TIP -> {
                        if (face == dir) {
                            continue; // pressed against the block it feeds
                        }
                    }
                }
                for (int i = 0; i < part.bands(); i++) {
                    float[][] band = part.band(face, i);
                    int type = part.types()[i];
                    switch (part.kind()) {
                        case CORE -> QuadBuilder.box(out, node[type], band[0], band[1], face, -1, -1, 0);
                        case ARM -> QuadBuilder.box(out, strand[type], band[0], band[1], face, dir.getAxis().ordinal(), -1, 0);
                        case TIP -> {
                            boolean input = part.arms()[i] == PipeKey.ARM_INPUT;
                            QuadBuilder.box(out, input ? tipInput : tipOutput, band[0], band[1], face, -1, -1, 0);
                            if (input) {
                                // Speed-card tier corners, tinted by the block color handler.
                                int tint = 1 + key.endTier(type, dir);
                                QuadBuilder.box(out, tierOverlay, band[0], band[1], face, -1, tint, 0.02f);
                            }
                        }
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return node[PipeType.ITEM.ordinal()];
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        PipeKey key = data.get(PipeBlockEntity.MODEL_KEY);
        if (key != null && key.typeMask() != 0) {
            return node[Integer.numberOfTrailingZeros(key.typeMask())];
        }
        return getParticleIcon();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
