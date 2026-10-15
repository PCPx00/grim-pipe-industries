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

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
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
    private final TextureAtlasSprite flow;
    private final Map<PipeKey, List<BakedQuad>> cache = new ConcurrentHashMap<>();

    public PipeBakedModel(TextureAtlasSprite[] strand, TextureAtlasSprite[] node, TextureAtlasSprite tipOutput,
                          TextureAtlasSprite tipInput, TextureAtlasSprite tierOverlay, TextureAtlasSprite flow) {
        this.flow = flow;
        this.strand = strand;
        this.node = node;
        this.tipOutput = tipOutput;
        this.tipInput = tipInput;
        this.tierOverlay = tierOverlay;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        BlockState facade = data.get(PipeBlockEntity.MODEL_FACADE);
        if (facade != null) {
            BakedModel model = facadeModel(facade);
            if (renderType != null && !model.getRenderTypes(facade, rand, ModelData.EMPTY).contains(renderType)) {
                return List.of();
            }
            return retint(model.getQuads(facade, side, rand, ModelData.EMPTY, renderType));
        }
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
        BlockState facade = data.get(PipeBlockEntity.MODEL_FACADE);
        return facade != null ? facadeModel(facade).getRenderTypes(facade, rand, ModelData.EMPTY) : RENDER_TYPES;
    }

    @Override
    public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        BlockState facade = data.get(PipeBlockEntity.MODEL_FACADE);
        if (facade != null) {
            return facadeModel(facade).useAmbientOcclusion(facade, ModelData.EMPTY, renderType);
        }
        return net.neoforged.neoforge.common.util.TriState.FALSE;
    }

    private static BakedModel facadeModel(BlockState facade) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(facade);
    }

    /** Facade tints are offset so the block color handler can tell them from the tier tints and ask the facade's own handler. */
    public static final int FACADE_TINT_OFFSET = 100;

    private static List<BakedQuad> retint(List<BakedQuad> quads) {
        boolean any = false;
        for (BakedQuad q : quads) {
            if (q.isTinted()) {
                any = true;
                break;
            }
        }
        if (!any) {
            return quads;
        }
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            out.add(q.isTinted() ? new BakedQuad(q.getVertices(), FACADE_TINT_OFFSET + q.getTintIndex(), q.getDirection(),
                    q.getSprite(), q.isShade(), q.hasAmbientOcclusion()) : q);
        }
        return out;
    }

    private List<BakedQuad> build(PipeKey key) {
        List<BakedQuad> out = new ArrayList<>();
        List<PipeGeometry.Part> parts = PipeGeometry.layout(key).parts();
        boolean showFlow = !ClientConfig.SPEC.isLoaded() || ClientConfig.SHOW_FLOW.get();
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
                        case ARM -> {
                            QuadBuilder.box(out, strand[type], band[0], band[1], face, dir.getAxis().ordinal(), -1, 0);
                            if (showFlow && key.flowing(type)) {
                                // Animated pulses along a strand that is moving something.
                                QuadBuilder.box(out, flow, band[0], band[1], face, dir.getAxis().ordinal(), -1, 0.01f);
                            }
                        }
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
        BlockState facade = data.get(PipeBlockEntity.MODEL_FACADE);
        if (facade != null) {
            return facadeModel(facade).getParticleIcon(ModelData.EMPTY);
        }
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
