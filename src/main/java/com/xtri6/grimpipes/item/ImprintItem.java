package com.xtri6.grimpipes.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.block.EndConfig;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.pipe.ConnectionMode;
import com.xtri6.grimpipes.pipe.PipeGeometry;
import com.xtri6.grimpipes.pipe.PipeKey;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * Reaper Imprint: sneak-click an end to copy its settings (Input or Output, filter, filter mode, redstone,
 * priority, distribution), click other ends to paste them. Speed cards are never copied.
 */
public class ImprintItem extends Item {
    public ImprintItem(Properties properties) {
        super(properties);
    }

    @Nullable
    private static CompoundTag stored(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains("Settings") ? tag : null;
    }

    /** Called from the pipe block (server side) with the part under the crosshair. */
    public static void use(ItemStack stack, PipeBlockEntity be, @Nullable PipeGeometry.Hit part, Player player) {
        if (part == null || part.kind() == PipeGeometry.PartKind.CORE || part.dir() == null
                || (part.arm() != PipeKey.ARM_INPUT && part.arm() != PipeKey.ARM_OUTPUT)) {
            player.displayClientMessage(Component.translatable("message.grimpipes.imprint.aim").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int t = part.type();
        PipeType type = PipeType.byIndex(t);
        Component typeName = Component.translatable("item.grimpipes." + type.id() + "_pipe");
        var registries = player.level().registryAccess();
        if (player.isShiftKeyDown()) {
            CompoundTag tag = new CompoundTag();
            tag.put("Settings", be.endConfig(t, part.dir()).saveSettings(registries));
            tag.putBoolean("Input", part.arm() == PipeKey.ARM_INPUT);
            tag.putString("From", type.id());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            player.level().playSound(null, be.getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.2f);
            player.displayClientMessage(Component.translatable("message.grimpipes.imprint.copied", typeName)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        CompoundTag tag = stored(stack);
        if (tag == null) {
            player.displayClientMessage(Component.translatable("message.grimpipes.imprint.empty").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        boolean input = tag.getBoolean("Input");
        be.setMode(t, part.dir(), input ? ConnectionMode.INPUT : ConnectionMode.OUTPUT);
        EndConfig cfg = be.endConfig(t, part.dir());
        cfg.loadSettings(tag.getCompound("Settings"), registries);
        BlockPos pos = be.getBlockPos();
        player.level().playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5f, 1.6f);
        player.displayClientMessage(Component.translatable("message.grimpipes.imprint.pasted", typeName)
                .withStyle(ChatFormatting.GOLD), true);
    }

    /** Sneak-use in the air clears the imprint. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stored(stack) != null) {
            if (!level.isClientSide) {
                stack.remove(DataComponents.CUSTOM_DATA);
                player.displayClientMessage(Component.translatable("message.grimpipes.imprint.cleared").withStyle(ChatFormatting.GRAY), true);
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
        return stored(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stored(stack);
        if (tag != null) {
            Component from = Component.translatable("item.grimpipes." + tag.getString("From") + "_pipe");
            Component kind = Component.translatable(tag.getBoolean("Input") ? "tooltip.grimpipes.imprint.input" : "tooltip.grimpipes.imprint.output");
            tooltip.add(Component.translatable("tooltip.grimpipes.imprint.holds", kind, from).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.grimpipes.imprint.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimpipes.imprint.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimpipes.imprint.3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
