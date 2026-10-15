package com.xtri6.grimpipes.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.Config;
import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.block.PipeBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

/**
 * Soul Link: joins two pipes in the same dimension without pipes in between. Click one pipe, then another;
 * every strand both pipes hold becomes one network. Sneak-click a linked pipe to break its link.
 * Both pipes must be loaded for the link to carry anything, since pipes never load chunks.
 */
public class SoulLinkItem extends Item {
    public SoulLinkItem(Properties properties) {
        super(properties);
    }

    @Nullable
    private static GlobalPos bound(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains("Pos")) {
            return null;
        }
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dim"));
        return dim == null ? null : GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(tag.getLong("Pos")));
    }

    private static void bind(ItemStack stack, @Nullable GlobalPos pos) {
        if (pos == null) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", pos.dimension().location().toString());
        tag.putLong("Pos", pos.pos().asLong());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Called from the pipe block, server side. */
    public static void use(ItemStack stack, PipeBlockEntity be, Player player) {
        ServerLevel level = (ServerLevel) be.getLevel();
        BlockPos here = be.getBlockPos();
        if (level == null) {
            return;
        }
        if (player.isShiftKeyDown()) {
            BlockPos old = be.soulLink();
            if (old == null) {
                player.displayClientMessage(Component.translatable("message.grimpipes.soul_link.none").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            be.setSoulLink(null);
            if (level.isLoaded(old) && level.getBlockEntity(old) instanceof PipeBlockEntity other && here.equals(other.soulLink())) {
                other.setSoulLink(null);
            }
            level.playSound(null, here, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.6f, 1.4f);
            player.displayClientMessage(Component.translatable("message.grimpipes.soul_link.broken").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        GlobalPos first = bound(stack);
        if (first == null || !first.dimension().equals(level.dimension())) {
            bind(stack, GlobalPos.of(level.dimension(), here));
            level.playSound(null, here, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 0.8f);
            player.displayClientMessage(Component.translatable("message.grimpipes.soul_link.bound",
                    here.getX(), here.getY(), here.getZ()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        BlockPos there = first.pos();
        if (there.equals(here)) {
            player.displayClientMessage(Component.translatable("message.grimpipes.soul_link.same").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int range = Config.SOUL_LINK_RANGE.get();
        if (range > 0 && !here.closerThan(there, range + 0.5)) {
            player.displayClientMessage(Component.translatable("message.grimpipes.soul_link.range", range).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!level.isLoaded(there) || !(level.getBlockEntity(there) instanceof PipeBlockEntity other)) {
            bind(stack, null);
            player.displayClientMessage(Component.translatable("message.grimpipes.soul_link.gone").withStyle(ChatFormatting.RED), true);
            return;
        }
        // Each pipe has one link; replacing it frees the old partners.
        unlinkPartner(level, be);
        unlinkPartner(level, other);
        be.setSoulLink(there);
        other.setSoulLink(here);
        bind(stack, null);
        level.playSound(null, here, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.7f, 1.2f);
        level.playSound(null, there, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.7f, 1.2f);
        player.displayClientMessage(Component.translatable("message.grimpipes.soul_link.linked",
                there.getX(), there.getY(), there.getZ()).withStyle(ChatFormatting.GOLD), true);
    }

    private static void unlinkPartner(ServerLevel level, PipeBlockEntity be) {
        BlockPos old = be.soulLink();
        if (old != null && level.isLoaded(old) && level.getBlockEntity(old) instanceof PipeBlockEntity p && be.getBlockPos().equals(p.soulLink())) {
            p.setSoulLink(null);
        }
    }

    /** Sneak-use in the air forgets the first pipe. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && bound(stack) != null) {
            if (!level.isClientSide) {
                bind(stack, null);
                player.displayClientMessage(Component.translatable("message.grimpipes.soul_link.cleared").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockState(pos).is(Registration.PIPE.get());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return bound(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos pos = bound(stack);
        if (pos != null) {
            tooltip.add(Component.translatable("tooltip.grimpipes.soul_link.bound", pos.pos().getX(), pos.pos().getY(), pos.pos().getZ())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.grimpipes.soul_link.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimpipes.soul_link.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimpipes.soul_link.3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
