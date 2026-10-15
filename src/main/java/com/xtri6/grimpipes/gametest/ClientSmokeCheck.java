package com.xtri6.grimpipes.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.NativeImage;
import com.xtri6.grimpipes.GrimPipes;
import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.block.PipeBlock;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.client.ClientHooks;
import com.xtri6.grimpipes.pipe.ConnectionMode;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev only (never shipped): drives a real client through every new screen and render path, logs any crash,
 * and saves a few frames for a visual check. Enabled with -Dgrimpipes.smoke=true.
 */
public final class ClientSmokeCheck {
    private record Step(int frames, Consumer<MinecraftServer> server, Runnable client, String shot) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int step = -1;
    private static int wait;
    private static int ready;
    private static final BlockPos O = new BlockPos(0, -60, 0);

    private ClientSmokeCheck() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ClientSmokeCheck::tick);
        STEPS.add(new Step(40, ClientSmokeCheck::build, () -> Minecraft.getInstance().options.hideGui = true, null));
        STEPS.add(new Step(60, s -> look(s, 3.5, -57.5, -2.5, 3.5, -59.5, 1.5), () -> { }, "smoke_world"));
        STEPS.add(new Step(30, s -> { }, () -> { }, "smoke_world_later"));
        STEPS.add(new Step(40, s -> look(s, 2.5, -58.2, 1.9, 2.5, -59.5, 0.5),
                () -> Minecraft.getInstance().options.hideGui = false, "smoke_lookat"));
        STEPS.add(new Step(30, s -> { }, ClientHooks::openGuide, "smoke_guide"));
        STEPS.add(new Step(30, s -> { }, () -> ClientHooks.openOverview(O.east(1)), "smoke_overview"));
        STEPS.add(new Step(30, s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            PipeBlock.openEnd(p, (PipeBlockEntity) s.overworld().getBlockEntity(O.east(1)), PipeType.ITEM, Direction.WEST);
        }, () -> { }, "smoke_end_input"));
        STEPS.add(new Step(10, s -> {
            ServerPlayer p = s.getPlayerList().getPlayers().get(0);
            p.closeContainer();
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.TAG_FILTER.get()));
        }, () -> { }, null));
        STEPS.add(new Step(30, s -> { }, () -> ClientHooks.openTagFilter(InteractionHand.MAIN_HAND), "smoke_tag_filter"));
        STEPS.add(new Step(10, s -> { }, () -> Minecraft.getInstance().setScreen(null), null));
    }

    private static void look(MinecraftServer server, double ex, double ey, double ez, double tx, double ty, double tz) {
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        double dx = tx - ex, dy = ty - ey, dz = tz - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleportTo(p.serverLevel(), ex, ey - 1.62, ez, yaw, pitch);
    }

    private static PipeBlockEntity pipe(ServerLevel level, BlockPos pos, PipeType type) {
        if (!level.getBlockState(pos).is(Registration.PIPE.get())) {
            level.setBlockAndUpdate(pos, Registration.PIPE.get().defaultBlockState());
        }
        PipeBlockEntity be = (PipeBlockEntity) level.getBlockEntity(pos);
        be.addType(type);
        return be;
    }

    /** chest | input pipe | pipes (one hidden in a facade) | chest, plus a fluid strand bundled alongside. */
    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int x = -2; x <= 9; x++) {
            for (int z = -3; z <= 4; z++) {
                for (int y = 0; y <= 4; y++) {
                    level.setBlockAndUpdate(O.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(O.offset(x, -1, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(O, Blocks.CHEST.defaultBlockState());
        Container src = (Container) level.getBlockEntity(O);
        for (int i = 0; i < 27; i++) {
            src.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        level.setBlockAndUpdate(O.east(7), Blocks.CHEST.defaultBlockState());
        for (int x = 1; x <= 6; x++) {
            pipe(level, O.east(x), PipeType.ITEM);
        }
        PipeBlockEntity in = pipe(level, O.east(1), PipeType.ITEM);
        in.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        in.swapCard(PipeType.ITEM.ordinal(), Direction.WEST, new ItemStack(Registration.UPGRADES.get(PipeTier.WHISPERING).get()));
        pipe(level, O.east(2), PipeType.FLUID);
        pipe(level, O.east(3), PipeType.FLUID);
        pipe(level, O.east(4), PipeType.ENERGY);
        pipe(level, O.east(5), PipeType.ENERGY);
        ((PipeBlockEntity) level.getBlockEntity(O.east(4))).setFacade(Blocks.STONE_BRICKS.defaultBlockState());
        level.setDayTime(4000);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        if (ready < 100) {
            ready++;
            return;
        }
        if (wait > 0) {
            wait--;
            if (wait == 0 && STEPS.get(step).shot() != null) {
                String name = STEPS.get(step).shot();
                try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    java.nio.file.Files.createDirectories(mc.gameDirectory.toPath().resolve("screenshots"));
                    img.writeToFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name + ".png"));
                } catch (Exception e) {
                    GrimPipes.LOGGER.error("[smoke] screenshot failed", e);
                }
                GrimPipes.LOGGER.info("[smoke] {} ok, screen={}", name, mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
            }
            return;
        }
        step++;
        if (step >= STEPS.size()) {
            GrimPipes.LOGGER.info("[smoke] RESULT PASS: all client steps ran");
            mc.stop();
            return;
        }
        Step s = STEPS.get(step);
        server.execute(() -> s.server().accept(server));
        s.client().run();
        wait = s.frames();
    }
}
