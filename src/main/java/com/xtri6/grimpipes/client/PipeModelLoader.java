package com.xtri6.grimpipes.client;

import java.util.function.Function;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.xtri6.grimpipes.GrimPipes;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

/** Model loader {@code grimpipes:pipe}, referenced from models/block/reaper_pipe.json. */
public final class PipeModelLoader implements IGeometryLoader<PipeModelLoader.Geometry> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(GrimPipes.MODID, "pipe");

    @Override
    public Geometry read(JsonObject json, JsonDeserializationContext context) {
        return new Geometry();
    }

    public static final class Geometry implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                               ModelState state, ItemOverrides overrides) {
            TextureAtlasSprite[] strand = new TextureAtlasSprite[PipeType.COUNT];
            TextureAtlasSprite[] node = new TextureAtlasSprite[PipeType.COUNT];
            for (PipeType t : PipeType.VALUES) {
                strand[t.ordinal()] = sprites.apply(material("block/pipe_" + t.id()));
                node[t.ordinal()] = sprites.apply(material("block/pipe_" + t.id() + "_node"));
            }
            return new PipeBakedModel(strand, node,
                    sprites.apply(material("block/pipe_tip_output")),
                    sprites.apply(material("block/pipe_tip_input")),
                    sprites.apply(material("block/pipe_tier_overlay")));
        }
    }

    private static Material material(String path) {
        return new Material(InventoryMenu.BLOCK_ATLAS, ResourceLocation.fromNamespaceAndPath(GrimPipes.MODID, path));
    }
}
