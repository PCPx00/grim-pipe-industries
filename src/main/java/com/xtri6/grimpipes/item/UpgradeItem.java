package com.xtri6.grimpipes.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.compat.Chemicals;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;
import com.xtri6.grimpipes.pipe.ResourceKind;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Right-click a pipe to raise every strand in that block to this tier. Any higher tier can be applied directly. */
public class UpgradeItem extends Item {
    private final PipeTier tier;

    public UpgradeItem(PipeTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public PipeTier tier() {
        return tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(tier.color());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tier == PipeTier.SOULFORGED;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.grimpipes.upgrade").withStyle(ChatFormatting.GRAY));
        appendRates(tier, null, tooltip);
    }

    /** Adds the tier's speeds; {@code only} limits it to the kinds one pipe type carries. */
    static void appendRates(PipeTier tier, @Nullable PipeType only, List<Component> tooltip) {
        try {
            tooltip.add(Component.translatable("tooltip.grimpipes.rates", tier.displayName()).withStyle(ChatFormatting.DARK_GRAY));
            if (only == null || only.carries(ResourceKind.ITEM)) {
                float perSecond = tier.itemsPerOperation() * 20f / tier.itemInterval();
                tooltip.add(line("tooltip.grimpipes.rate.items", fmt(perSecond)));
            }
            if (only == null || only.carries(ResourceKind.FLUID)) {
                tooltip.add(line("tooltip.grimpipes.rate.fluid", fmt(tier.fluidPerTick())));
            }
            if (only == null || only.carries(ResourceKind.ENERGY)) {
                tooltip.add(line("tooltip.grimpipes.rate.energy", fmt(tier.energyPerTick())));
            }
            if ((only == null || only.carries(ResourceKind.CHEMICAL)) && Chemicals.bridge() != null) {
                tooltip.add(line("tooltip.grimpipes.rate.chemical", fmt(tier.chemicalPerTick())));
            }
        } catch (IllegalStateException configNotLoaded) {
            // Tooltips can be built before configs load (e.g. search tree); just skip the numbers.
        }
    }

    private static Component line(String key, String value) {
        return Component.literal("  ").append(Component.translatable(key, value)).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static String fmt(double v) {
        if (v >= 1_000_000) {
            return trim(v / 1_000_000) + "M";
        }
        if (v >= 10_000) {
            return trim(v / 1_000) + "k";
        }
        return trim(v);
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
