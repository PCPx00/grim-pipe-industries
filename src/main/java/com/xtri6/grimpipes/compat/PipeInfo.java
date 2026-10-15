package com.xtri6.grimpipes.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import com.xtri6.grimpipes.block.EndConfig;
import com.xtri6.grimpipes.block.PipeBlock;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.pipe.PipeGeometry;
import com.xtri6.grimpipes.pipe.PipeKey;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

/**
 * What a look-at tooltip (Jade or WTHIT) shows for a pipe: its strands, and the settings of the end under the
 * crosshair. Shared by both plugins so they always say the same thing.
 */
public final class PipeInfo {
    private PipeInfo() {
    }

    private static PipeGeometry.Hit lookedAt(PipeBlockEntity be, BlockPos pos, Vec3 hit) {
        if (be.facade() != null || hit == null) {
            return null;
        }
        return PipeBlock.pick(be, pos, hit);
    }

    /** Server side: end settings (cards and filters never reach clients otherwise). */
    public static void writeServerData(CompoundTag data, PipeBlockEntity be, BlockPos pos, Vec3 hit) {
        PipeGeometry.Hit part = lookedAt(be, pos, hit);
        if (part == null || part.dir() == null) {
            return;
        }
        EndConfig cfg = be.endConfigOrNull(part.type(), part.dir());
        if (cfg == null) {
            return;
        }
        int filters = 0;
        for (int i = 0; i < EndConfig.FILTER_SLOTS; i++) {
            if (!cfg.filter.getStackInSlot(i).isEmpty()) {
                filters++;
            }
        }
        data.putInt("GpFilters", filters);
        data.putString("GpMode", cfg.mode().id());
        data.putString("GpRedstone", cfg.redstone().id());
        data.putString("GpDist", cfg.distribution().id());
        data.putInt("GpPriority", cfg.priority());
    }

    /** Client side: the tooltip lines. */
    public static List<Component> lines(PipeBlockEntity be, BlockPos pos, Vec3 hit, CompoundTag data) {
        List<Component> out = new ArrayList<>();
        if (be.facade() != null) {
            out.add(Component.translatable("jade.grimpipes.facade").withStyle(ChatFormatting.DARK_PURPLE));
        }
        StringJoiner names = new StringJoiner(", ");
        for (PipeType t : be.types()) {
            names.add(Component.translatable("item.grimpipes." + t.id() + "_pipe").getString().replace("Reaper ", ""));
        }
        out.add(Component.translatable("jade.grimpipes.strands", names.toString()).withStyle(ChatFormatting.GRAY));
        if (be.hasSoulLink()) {
            out.add(Component.translatable("jade.grimpipes.linked").withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        PipeGeometry.Hit part = lookedAt(be, pos, hit);
        if (part == null || part.dir() == null || (part.arm() != PipeKey.ARM_INPUT && part.arm() != PipeKey.ARM_OUTPUT)) {
            return out;
        }
        Direction d = part.dir();
        boolean input = part.arm() == PipeKey.ARM_INPUT;
        MutableComponent kind = Component.translatable(input ? "jade.grimpipes.input" : "jade.grimpipes.output")
                .withStyle(input ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE);
        out.add(Component.translatable("jade.grimpipes.end", Component.translatable("side.grimpipes." + d.getName()), kind));
        if (input) {
            out.add(Component.literal("  ").append(PipeTier.byIndex(be.key().endTier(part.type(), d)).displayName()));
        }
        if (data != null && data.contains("GpMode")) {
            MutableComponent line = Component.literal("  ");
            if (data.getInt("GpFilters") > 0) {
                line.append(Component.translatable("gui.grimpipes.mode." + data.getString("GpMode")))
                        .append(" (" + data.getInt("GpFilters") + ")  ");
            }
            if (input) {
                line.append(Component.translatable("gui.grimpipes.redstone." + data.getString("GpRedstone"))).append("  ")
                        .append(Component.translatable("gui.grimpipes.distribution." + data.getString("GpDist")));
            } else {
                int p = data.getInt("GpPriority");
                line.append(Component.translatable("gui.grimpipes.priority")).append(" " + (p > 0 ? "+" : "") + p);
            }
            out.add(line.withStyle(ChatFormatting.GRAY));
        }
        return out;
    }
}
