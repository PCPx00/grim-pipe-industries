package com.xtri6.grimpipes.compat;

import com.xtri6.grimpipes.block.PipeBlock;
import com.xtri6.grimpipes.block.PipeBlockEntity;

import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;

/** WTHIT tooltips (see {@link PipeInfo}). Only loaded by WTHIT, through waila_plugins.json. */
public final class WthitPlugin implements IWailaPlugin {
    @Override
    public void register(IRegistrar registrar) {
        registrar.addBlockData(EndData.INSTANCE, PipeBlockEntity.class);
        registrar.addComponent(Tooltip.INSTANCE, TooltipPosition.BODY, PipeBlock.class);
    }

    enum EndData implements IDataProvider<PipeBlockEntity> {
        INSTANCE;

        @Override
        public void appendData(IDataWriter data, IServerAccessor<PipeBlockEntity> accessor, IPluginConfig config) {
            HitResult hit = accessor.getHitResult();
            PipeBlockEntity be = accessor.getTarget();
            PipeInfo.writeServerData(data.raw(), be, be.getBlockPos(), hit == null ? null : hit.getLocation());
        }
    }

    enum Tooltip implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockEntity() instanceof PipeBlockEntity be) {
                for (Component line : PipeInfo.lines(be, accessor.getPosition(), accessor.getBlockHitResult().getLocation(),
                        accessor.getData().raw())) {
                    tooltip.addLine(line);
                }
            }
        }
    }
}
