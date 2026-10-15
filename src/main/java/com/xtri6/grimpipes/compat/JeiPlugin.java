package com.xtri6.grimpipes.compat;

import java.util.List;

import com.xtri6.grimpipes.GrimPipes;
import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** JEI info pages for every Grim Pipe Industries item. Only loaded by JEI. */
@mezz.jei.api.JeiPlugin
public final class JeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(GrimPipes.MODID, "info");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<ItemStack> pipes = PipeType.VALUES.length == 0 ? List.of()
                : java.util.Arrays.stream(PipeType.VALUES).map(t -> new ItemStack(Registration.pipeItem(t))).toList();
        registration.addItemStackInfo(pipes, Component.translatable("jei.grimpipes.pipe"), Component.translatable("jei.grimpipes.facade"));
        List<ItemStack> cards = java.util.Arrays.stream(PipeTier.VALUES).filter(t -> t != PipeTier.HOLLOW)
                .map(t -> new ItemStack(Registration.UPGRADES.get(t).get())).toList();
        registration.addItemStackInfo(cards, Component.translatable("jei.grimpipes.upgrade"));
        registration.addItemStackInfo(new ItemStack(Registration.WRENCH.get()), Component.translatable("jei.grimpipes.wrench"));
        registration.addItemStackInfo(new ItemStack(Registration.TAG_FILTER.get()), Component.translatable("jei.grimpipes.tag_filter"));
        registration.addItemStackInfo(new ItemStack(Registration.IMPRINT.get()), Component.translatable("jei.grimpipes.imprint"));
        registration.addItemStackInfo(new ItemStack(Registration.SOUL_LINK.get()), Component.translatable("jei.grimpipes.soul_link"));
        registration.addItemStackInfo(new ItemStack(Registration.FIELD_GUIDE.get()), Component.translatable("jei.grimpipes.field_guide"));
    }
}
