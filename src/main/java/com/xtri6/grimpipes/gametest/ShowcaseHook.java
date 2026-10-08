package com.xtri6.grimpipes.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.xtri6.grimpipes.GrimPipes;
import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.block.EndConfig;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.client.GuiStyle;
import com.xtri6.grimpipes.menu.EndMenu;
import com.xtri6.grimpipes.pipe.ConnectionMode;
import com.xtri6.grimpipes.pipe.FilterMode;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev-only (never shipped): builds a showcase factory in a flat test world, then walks a script of camera
 * shots, GUI shots and animation frames, saving each as a screenshot. Enabled with -Dgrimpipes.shots=true.
 */
public final class ShowcaseHook {
    private static final int Y = -60;

    private record Step(String shot, int frames, Consumer<MinecraftServer> server, Runnable client) {
    }

    private static final List<Step> SCRIPT = new ArrayList<>();
    private static int index = -1;
    private static int framesInStep;
    private static int startDelay = 0;

    private ShowcaseHook() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ShowcaseHook::onFrame);
        buildScript();
    }

    // ---- Script ----

    private static void cam(double ex, double ey, double ez, double tx, double ty, double tz, String shot, int frames, long time) {
        SCRIPT.add(new Step(shot, frames, server -> {
            ServerLevel level = server.overworld();
            level.setDayTime(time);
            look(server, ex, ey, ez, tx, ty, tz);
        }, () -> { }));
    }

    private static void look(MinecraftServer server, double ex, double ey, double ez, double tx, double ty, double tz) {
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        double dx = tx - ex, dy = ty - ey, dz = tz - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleportTo(p.serverLevel(), ex, ey - 1.62, ez, yaw, pitch);
    }

    private static void buildScript() {
        SCRIPT.add(new Step(null, 30, ShowcaseHook::build, () -> Minecraft.getInstance().options.hideGui = true));
        // Let shaders compile and chunks settle.
        cam(-1.5, -55.6, 2.5, 11, -59.5, 10, null, 80, 12_350);

        // Stills
        cam(-1.5, -55.6, 2.5, 11, -59.5, 10, "01_hero_dusk", 25, 12_350);
        cam(21.5, -55.5, 2.5, 10, -60, 10, "02_factory_day", 25, 3_000);
        cam(18.6, -57.4, 6.9, 15.5, -59.7, 11.4, "03_macro_bundle", 25, 4_000);
        cam(6.5, -56.3, 6.4, 6.5, -60, 3.7, "04_tier_row", 25, 4_000);
        cam(13.6, -58.3, 6.6, 13.6, -59.6, 4.5, "05_omni_bundle", 25, 4_000);
        cam(0.3, -58.9, 7.4, 10.0, -59.6, 9.2, "06_main_line", 25, 1_200);
        cam(20.5, -56.6, 13.0, 12.0, -60, 8.5, "07_sunset", 25, 12_150);

        // GUI shots, one per theme
        String[] themes = {"reaper", "vanilla", "dark"};
        GuiStyle.Theme[] values = {GuiStyle.Theme.REAPER, GuiStyle.Theme.VANILLA, GuiStyle.Theme.DARK};
        for (int i = 0; i < 3; i++) {
            GuiStyle.Theme theme = values[i];
            SCRIPT.add(new Step("08_gui_" + themes[i], 25, server -> {
                look(server, 6.5, -57.4, 7.6, 6.5, -60, 4.2);
                openGui(server);
            }, () -> GuiStyle.apply(theme)));
        }
        SCRIPT.add(new Step(null, 5, server -> server.getPlayerList().getPlayers().get(0).closeContainer(), () -> GuiStyle.apply(GuiStyle.Theme.REAPER)));

        // Animation A: orbit around the "two pipes on one machine face" junction.
        int orbit = 36;
        for (int i = 0; i < orbit; i++) {
            double a = Math.PI * 2 * i / orbit - Math.PI / 2;
            double ex = 15.5 + Math.cos(a) * 3.6;
            double ez = 10.5 + Math.sin(a) * 3.6;
            cam(ex, -57.6, ez, 15.5, -59.7, 10.5, String.format("anim_orbit_%02d", i), 6, 4_000);
        }

        // Animation B: building a bundle on one machine face, then flipping its ends.
        SCRIPT.add(new Step(null, 10, server -> {
            look(server, 22.6, -57.9, 2.2, 21.5, -59.6, 4.5);
            ServerLevel level = server.overworld();
            set(level, 21, 6, gsi("simulation_chamber"));
            set(level, 21, 5, Blocks.AIR);
            set(level, 21, 4, Blocks.AIR);
            set(level, 21, 3, Blocks.BARREL);
        }, () -> { }));
        PipeType[] order = {PipeType.ITEM, PipeType.FLUID, PipeType.ENERGY, PipeType.OMNI};
        int frame = 0;
        SCRIPT.add(build(frame++, l -> { }));
        for (int k = 0; k < order.length; k++) {
            PipeType t = order[k];
            SCRIPT.add(build(frame++, l -> pipe(l, 21, 5, t)));
            SCRIPT.add(build(frame++, l -> pipe(l, 21, 4, t)));
        }
        for (PipeType t : new PipeType[]{PipeType.ITEM, PipeType.FLUID, PipeType.ENERGY}) {
            SCRIPT.add(build(frame++, l -> pipe(l, 21, 5).setMode(t.ordinal(), Direction.SOUTH, ConnectionMode.INPUT)));
        }
        SCRIPT.add(build(frame++, l -> pipe(l, 21, 5).swapCard(PipeType.ITEM.ordinal(), Direction.SOUTH,
                new ItemStack(Registration.UPGRADES.get(PipeTier.SOULFORGED).get()))));
        SCRIPT.add(build(frame++, l -> { }));
        SCRIPT.add(new Step(null, 3, server -> { }, () -> {
            GrimPipes.LOGGER.info("[shots] done");
            Minecraft.getInstance().stop();
        }));
    }

    private static Step build(int frame, Consumer<ServerLevel> action) {
        return new Step(String.format("anim_build_%02d", frame), 14, server -> action.accept(server.overworld()), () -> { });
    }

    // ---- Driver ----

    private static void onFrame(RenderFrameEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null || mc.level == null) {
            return;
        }
        if (startDelay < 60) {
            startDelay++;
            return;
        }
        if (index >= 0 && index < SCRIPT.size()) {
            Step step = SCRIPT.get(index);
            framesInStep++;
            if (framesInStep < step.frames()) {
                return;
            }
            if (step.shot() != null) {
                Screenshot.grab(mc.gameDirectory, step.shot() + ".png", mc.getMainRenderTarget(),
                        msg -> GrimPipes.LOGGER.info("[shots] {}", step.shot()));
            }
        }
        index++;
        framesInStep = 0;
        if (index < SCRIPT.size()) {
            Step next = SCRIPT.get(index);
            next.client().run();
            server.execute(() -> next.server().accept(server));
        }
    }

    // ---- World helpers ----

    private static Block gsi(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("grimsoul", id));
    }

    private static PipeBlockEntity pipe(ServerLevel level, int x, int z, PipeType... types) {
        return pipeAt(level, new BlockPos(x, Y, z), types);
    }

    private static PipeBlockEntity pipeAt(ServerLevel level, BlockPos pos, PipeType... types) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity)) {
            level.setBlockAndUpdate(pos, Registration.PIPE.get().defaultBlockState());
        }
        PipeBlockEntity be = (PipeBlockEntity) level.getBlockEntity(pos);
        for (PipeType t : types) {
            be.addType(t);
        }
        return be;
    }

    private static void set(ServerLevel level, int x, int z, Block block) {
        level.setBlockAndUpdate(new BlockPos(x, Y, z), block.defaultBlockState());
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            level.setBlock(p, state, Block.UPDATE_CLIENTS);
        }
    }

    private static void card(PipeBlockEntity be, PipeType type, Direction side, PipeTier tier) {
        be.swapCard(type.ordinal(), side, new ItemStack(Registration.UPGRADES.get(tier).get()));
    }

    private static void openGui(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        PipeBlockEntity be = pipe(level, 8, 4);
        PipeType type = PipeType.ITEM;
        Direction side = Direction.NORTH;
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new EndMenu(id, inv, be, type, side),
                Component.translatable("item.grimpipes.item_pipe")), buf -> {
            buf.writeBlockPos(be.getBlockPos());
            buf.writeVarInt(type.ordinal());
            buf.writeVarInt(side.get3DDataValue());
        });
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        p.setGameMode(GameType.SPECTATOR);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setWeatherParameters(999999, 0, false, false);

        // Hotbar for the GUI shots
        // Clear wandering mobs (flat-world slimes) so they don't photobomb.
        List<net.minecraft.world.entity.Entity> mobs = new ArrayList<>();
        for (var e : level.getAllEntities()) {
            if (e != null && !(e instanceof net.minecraft.world.entity.player.Player)) {
                mobs.add(e);
            }
        }
        mobs.forEach(net.minecraft.world.entity.Entity::discard);
        var inv = p.getInventory();
        inv.selected = 0;
        int s = 1;
        for (PipeType t : PipeType.VALUES) {
            inv.setItem(s++, new ItemStack(Registration.pipeItem(t), 64));
        }
        inv.setItem(s++, new ItemStack(Registration.WRENCH.get()));
        inv.setItem(s++, new ItemStack(Registration.UPGRADES.get(PipeTier.WRAITHBOUND).get(), 8));
        inv.setItem(9 + 8, new ItemStack(Registration.UPGRADES.get(PipeTier.RESTLESS).get(), 16));
        inv.setItem(9, new ItemStack(Items.COBBLESTONE, 64));
        inv.setItem(10, new ItemStack(Items.DIAMOND, 32));

        // ---- Hall: floor, back wall with purple windows, pillars, lights ----
        BlockState tiles = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState polished = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        for (int x = -6; x <= 28; x++) {
            for (int z = -8; z <= 18; z++) {
                level.setBlock(new BlockPos(x, Y - 1, z), ((x + z) & 1) == 0 ? tiles : polished, Block.UPDATE_CLIENTS);
            }
        }
        fill(level, -1, Y, 15, 23, Y + 6, 15, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        for (int x = 1; x <= 21; x += 4) {
            fill(level, x, Y + 2, 15, x + 1, Y + 4, 15, Blocks.PURPLE_STAINED_GLASS.defaultBlockState());
        }
        for (int x = -1; x <= 23; x += 6) {
            fill(level, x, Y, 14, x, Y + 6, 14, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            level.setBlock(new BlockPos(x, Y + 4, 13), Blocks.SOUL_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        fill(level, -1, Y + 6, 13, 23, Y + 6, 14, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        for (int x = 0; x <= 22; x += 3) {
            level.setBlock(new BlockPos(x, Y, -1), Blocks.AMETHYST_CLUSTER.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        // ---- Machines along the back ----
        set(level, 3, 12, gsi("reaper_generator"));
        set(level, 6, 12, gsi("energy_cell"));
        set(level, 9, 12, gsi("xp_tank"));
        set(level, 12, 12, gsi("output_crate"));
        set(level, 15, 12, gsi("simulation_chamber"));
        set(level, 18, 9, Blocks.BARREL);
        set(level, 17, 8, gsi("xp_tank"));
        set(level, 17, 10, gsi("energy_cell"));

        // Main 3-strand bundle line
        for (int x = 2; x <= 17; x++) {
            pipe(level, x, 9, PipeType.ITEM, PipeType.FLUID, PipeType.ENERGY);
        }
        // Branches down to each machine
        pipe(level, 3, 10, PipeType.ENERGY);
        pipe(level, 3, 11, PipeType.ENERGY).setMode(PipeType.ENERGY.ordinal(), Direction.SOUTH, ConnectionMode.INPUT);
        pipe(level, 6, 10, PipeType.ENERGY);
        pipe(level, 6, 11, PipeType.ENERGY);
        pipe(level, 9, 10, PipeType.FLUID);
        PipeBlockEntity tankEnd = pipe(level, 9, 11, PipeType.FLUID);
        tankEnd.setMode(PipeType.FLUID.ordinal(), Direction.SOUTH, ConnectionMode.INPUT);
        card(tankEnd, PipeType.FLUID, Direction.SOUTH, PipeTier.RESTLESS);
        pipe(level, 12, 10, PipeType.ITEM);
        PipeBlockEntity crateEnd = pipe(level, 12, 11, PipeType.ITEM);
        crateEnd.setMode(PipeType.ITEM.ordinal(), Direction.SOUTH, ConnectionMode.INPUT);
        card(crateEnd, PipeType.ITEM, Direction.SOUTH, PipeTier.WRAITHBOUND);
        // Two pipes on ONE face of the Virtualization Chamber: power in, drops out.
        pipe(level, 15, 10, PipeType.ITEM, PipeType.ENERGY);
        PipeBlockEntity chamberEnd = pipe(level, 15, 11, PipeType.ITEM, PipeType.ENERGY);
        chamberEnd.setMode(PipeType.ITEM.ordinal(), Direction.SOUTH, ConnectionMode.INPUT);
        card(chamberEnd, PipeType.ITEM, Direction.SOUTH, PipeTier.SOULFORGED);

        // ---- Tier row: one Input end per tier pulling from barrels ----
        for (int i = 0; i < PipeTier.VALUES.length; i++) {
            set(level, 4 + i, 3, Blocks.BARREL);
            PipeBlockEntity t = pipe(level, 4 + i, 4, PipeType.ITEM);
            t.setMode(PipeType.ITEM.ordinal(), Direction.NORTH, ConnectionMode.INPUT);
            if (i > 0) {
                card(t, PipeType.ITEM, Direction.NORTH, PipeTier.VALUES[i]);
            }
        }
        set(level, 9, 4, Blocks.CHEST);
        PipeBlockEntity guiPipe = pipe(level, 8, 4);
        EndConfig cfg = guiPipe.endConfig(PipeType.ITEM.ordinal(), Direction.NORTH);
        cfg.setMode(FilterMode.WHITELIST);
        cfg.filter.setStackInSlot(0, new ItemStack(Items.DIAMOND));
        cfg.filter.setStackInSlot(1, new ItemStack(Items.EMERALD));
        cfg.filter.setStackInSlot(4, new ItemStack(Items.AMETHYST_SHARD));

        // ---- Five-strand bundle between a barrel and a chest ----
        set(level, 11, 4, Blocks.BARREL);
        set(level, 16, 4, Blocks.CHEST);
        for (int x = 12; x <= 15; x++) {
            pipe(level, x, 4, PipeType.ITEM, PipeType.FLUID, PipeType.ENERGY, PipeType.CHEMICAL, PipeType.OMNI);
        }
        pipe(level, 12, 4).setMode(PipeType.OMNI.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        pipe(level, 12, 4).setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);

        // Stage for the build animation
        set(level, 21, 6, gsi("simulation_chamber"));
        GrimPipes.LOGGER.info("[shots] scene built");
    }
}
