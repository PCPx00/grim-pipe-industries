package com.xtri6.grimpipes.compat;

import com.xtri6.grimpipes.GrimPipes;
import com.xtri6.grimpipes.block.PipeBlock;
import com.xtri6.grimpipes.block.PipeBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade tooltips (see {@link PipeInfo}). Only loaded by Jade. */
@WailaPlugin
public final class JadePlugin implements IWailaPlugin {
    static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(GrimPipes.MODID, "pipe");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(EndData.INSTANCE, PipeBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Tooltip.INSTANCE, PipeBlock.class);
    }

    enum EndData implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof PipeBlockEntity be) {
                PipeInfo.writeServerData(data, be, accessor.getPosition(), accessor.getHitResult().getLocation());
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum Tooltip implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockEntity() instanceof PipeBlockEntity be) {
                for (Component line : PipeInfo.lines(be, accessor.getPosition(), accessor.getHitResult().getLocation(),
                        accessor.getServerData())) {
                    tooltip.add(line);
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
