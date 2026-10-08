package com.xtri6.grimpipes.item;

import java.util.List;
import java.util.Map;

import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.compat.Chemicals;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;
import com.xtri6.grimpipes.pipe.ResourceKind;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A pipe strand. Places a pipe block, or, used on a pipe (or on the face of a block that already has a
 * pipe in front of it), adds this strand into that same block space.
 */
public class PipeItem extends BlockItem {
    private final PipeType type;
    private String descriptionId;

    public PipeItem(Block block, PipeType type, Properties properties) {
        super(block, properties);
        this.type = type;
    }

    public PipeType type() {
        return type;
    }

    @Override
    public String getDescriptionId() {
        if (descriptionId == null) {
            descriptionId = net.minecraft.Util.makeDescriptionId("item", BuiltInRegistries.ITEM.getKey(this));
        }
        return descriptionId;
    }

    /** Only the Item Pipe claims the shared block in Item.BY_BLOCK, so the block has one canonical item. */
    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
        if (type == PipeType.ITEM) {
            super.registerBlocks(map, item);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Clicking the face of a chest where a pipe already sits in front: merge into that pipe.
        Level level = context.getLevel();
        BlockPos front = context.getClickedPos().relative(context.getClickedFace());
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof PipeBlockEntity)
                && level.getBlockEntity(front) instanceof PipeBlockEntity be && !be.has(type)) {
            if (!level.isClientSide) {
                be.addType(type);
                context.getItemInHand().consume(1, context.getPlayer());
                level.playSound(null, front, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 1f, 1.1f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.grimpipes." + type.id() + "_pipe").withStyle(ChatFormatting.GRAY));
        if (type.carries(ResourceKind.CHEMICAL) && Chemicals.bridge() == null) {
            tooltip.add(Component.translatable("tooltip.grimpipes.needs_mekanism").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.grimpipes.pipe.bundle").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.grimpipes.pipe.cards").withStyle(ChatFormatting.DARK_PURPLE));
        UpgradeItem.appendRates(PipeTier.HOLLOW, type, tooltip);
    }
}
