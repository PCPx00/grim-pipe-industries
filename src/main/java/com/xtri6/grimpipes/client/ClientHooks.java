package com.xtri6.grimpipes.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;

/** Entry points common code calls on the client only (always behind a level.isClientSide check). */
public final class ClientHooks {
    /** The pipe whose overview an end screen was opened from, so its Back button can return there. */
    @Nullable
    static BlockPos overviewReturn;

    private ClientHooks() {
    }

    public static void openOverview(BlockPos pos) {
        Minecraft.getInstance().setScreen(new OverviewScreen(pos.immutable()));
    }

    public static void openTagFilter(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new TagFilterScreen(hand));
    }

    public static void openGuide() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }
}
