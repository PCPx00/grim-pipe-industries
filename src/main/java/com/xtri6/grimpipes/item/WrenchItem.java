package com.xtri6.grimpipes.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Reaper Wrench. All of its behavior lives in PipeBlock#useItemOn so that any wrench in the
 * {@code c:tools/wrench} tag works the same way.
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.grimpipes.wrench.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimpipes.wrench.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimpipes.wrench.3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimpipes.wrench.4").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimpipes.wrench.5").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
