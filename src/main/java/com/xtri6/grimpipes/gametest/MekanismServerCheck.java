package com.xtri6.grimpipes.gametest;

import com.xtri6.grimpipes.GrimPipes;
import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.pipe.ConnectionMode;
import com.xtri6.grimpipes.pipe.PipeKey;
import com.xtri6.grimpipes.pipe.PipeType;

import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dev-only (never shipped): on a real dedicated server with Mekanism, places tanks the way a player does,
 * pipes them together and checks hydrogen actually moves. Enabled with -Dgrimpipes.mekcheck=true.
 */
public final class MekanismServerCheck {
    private static final BlockCapability<IChemicalHandler, Direction> CHEMICAL = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);
    private static int ticks = -1;
    private static long start;
    private static final BlockPos BASE = new BlockPos(0, 120, 0);
    private static final BlockPos OMNI = new BlockPos(0, 120, 6);
    private static long omniStart;

    private MekanismServerCheck() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(MekanismServerCheck::started);
        NeoForge.EVENT_BUS.addListener(MekanismServerCheck::tick);
    }

    private static void place(ServerLevel level, BlockPos pos, String id) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mekanism", id));
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        block.setPlacedBy(level, pos, level.getBlockState(pos), FakePlayerFactory.getMinecraft(level), new ItemStack(block.asItem()));
    }

    private static String sides(ServerLevel level, BlockPos pos) {
        StringBuilder sb = new StringBuilder();
        for (Direction d : Direction.values()) {
            sb.append(d).append('=').append(level.getCapability(CHEMICAL, pos, d) != null).append(' ');
        }
        return sb.toString();
    }

    private static long amount(ServerLevel level, BlockPos pos) {
        IChemicalHandler h = level.getCapability(CHEMICAL, pos, null);
        long t = 0;
        if (h != null) {
            for (int i = 0; i < h.getChemicalTanks(); i++) {
                t += h.getChemicalInTank(i).getAmount();
            }
        }
        return t;
    }

    /** Sets every chemical side of a Mekanism machine to one data type, like a player using the Configurator. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void configureSides(ServerLevel level, BlockPos pos, String dataType) {
        try {
            Object be = level.getBlockEntity(pos);
            Object config = be.getClass().getMethod("getConfig").invoke(be);
            Class tt = Class.forName("mekanism.common.lib.transmitter.TransmissionType");
            Object info = config.getClass().getMethod("getConfig", tt).invoke(config, Enum.valueOf(tt, "CHEMICAL"));
            Class dt = Class.forName("mekanism.common.tile.component.config.DataType");
            Class rs = Class.forName("mekanism.api.RelativeSide");
            java.lang.reflect.Method set = info.getClass().getMethod("setDataType", dt, rs);
            GrimPipes.LOGGER.info("[mekcheck] supported types: {}", info.getClass().getMethod("getSupportedDataTypes").invoke(info));
            for (Object side : rs.getEnumConstants()) {
                set.invoke(info, Enum.valueOf(dt, dataType), side);
            }
            level.invalidateCapabilities(pos);
            GrimPipes.LOGGER.info("[mekcheck] {} now: {}", pos, info.getClass().getMethod("getSideConfig").invoke(info));
        } catch (Throwable t) {
            GrimPipes.LOGGER.error("[mekcheck] configure failed", t);
        }
    }

    private static void started(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        level.getChunkAt(BASE);
        place(level, BASE, "basic_chemical_tank");
        place(level, BASE.east(4), "basic_chemical_tank");
        place(level, BASE.south(2), "electrolytic_separator");
        configureSides(level, BASE, "OUTPUT");
        configureSides(level, BASE.east(4), "INPUT");
        place(level, OMNI, "basic_chemical_tank");
        place(level, OMNI.east(4), "basic_chemical_tank");
        configureSides(level, OMNI, "OUTPUT");
        configureSides(level, OMNI.east(4), "INPUT");
        ticks = 0;
    }

    private static void tick(ServerTickEvent.Post event) {
        if (ticks < 0) {
            return;
        }
        ticks++;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (ticks == 10) {
            GrimPipes.LOGGER.info("[mekcheck] tank sides: {}", sides(level, BASE));
            GrimPipes.LOGGER.info("[mekcheck] separator sides: {}", sides(level, BASE.south(2)));
            try {
                Object be = level.getBlockEntity(BASE);
                Object config = be.getClass().getMethod("getConfig").invoke(be);
                for (java.lang.reflect.Method m : config.getClass().getMethods()) {
                    if (m.getName().equals("isCapabilityDisabled")) {
                        StringBuilder sb = new StringBuilder();
                        for (Direction d : Direction.values()) {
                            sb.append(d).append('=').append(m.invoke(config, CHEMICAL, d)).append(' ');
                        }
                        GrimPipes.LOGGER.info("[mekcheck] config.isCapabilityDisabled: {}", sb);
                    }
                }
                for (java.lang.reflect.Method m : be.getClass().getMethods()) {
                    if (m.getName().equals("isCapabilityDisabled") || m.getName().equals("getCapability")) {
                        GrimPipes.LOGGER.info("[mekcheck] tile method {}{}", m.getName(), java.util.Arrays.toString(m.getParameterTypes()));
                    }
                }
                Object chemConfig = config.getClass().getMethod("getConfig", Class.forName("mekanism.common.lib.transmitter.TransmissionType"))
                        .invoke(config, Enum.valueOf((Class) Class.forName("mekanism.common.lib.transmitter.TransmissionType"), "CHEMICAL"));
                GrimPipes.LOGGER.info("[mekcheck] chemical ConfigInfo: {} sides={}", chemConfig,
                        chemConfig == null ? null : chemConfig.getClass().getMethod("getSideConfig").invoke(chemConfig));
            } catch (Throwable t) {
                GrimPipes.LOGGER.error("[mekcheck] reflect failed", t);
            }
            GrimPipes.LOGGER.info("[mekcheck] tank sides after configuring: {}", sides(level, BASE));
            ChemicalStack h2 = new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.get(ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen")), 8000);
            // Fill through Mekanism's internal (side = null) access, like a machine producing gas.
            ((mekanism.api.chemical.IMekanismChemicalHandler) level.getBlockEntity(BASE)).insertChemical(0, h2, null, Action.EXECUTE);
            start = amount(level, BASE);
            ((mekanism.api.chemical.IMekanismChemicalHandler) level.getBlockEntity(OMNI)).insertChemical(0,
                    new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.get(ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen")), 1000), null, Action.EXECUTE);
            omniStart = amount(level, OMNI);
            for (int i = 1; i <= 3; i++) {
                level.setBlockAndUpdate(OMNI.east(i), Registration.PIPE.get().defaultBlockState());
                ((PipeBlockEntity) level.getBlockEntity(OMNI.east(i))).addType(PipeType.OMNI);
            }
            ((PipeBlockEntity) level.getBlockEntity(OMNI.east(1))).setMode(PipeType.OMNI.ordinal(), Direction.WEST, ConnectionMode.INPUT);
            GrimPipes.LOGGER.info("[mekcheck] source tank filled with {} mB", start);
            for (int i = 1; i <= 3; i++) {
                BlockPos p = BASE.east(i);
                level.setBlockAndUpdate(p, Registration.PIPE.get().defaultBlockState());
                ((PipeBlockEntity) level.getBlockEntity(p)).addType(i == 2 ? PipeType.OMNI : PipeType.CHEMICAL);
            }
            // middle pipe gets both strands so chemical runs through as a bundle
            ((PipeBlockEntity) level.getBlockEntity(BASE.east(2))).addType(PipeType.CHEMICAL);
            ((PipeBlockEntity) level.getBlockEntity(BASE.east(1))).setMode(PipeType.CHEMICAL.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        }
        if (ticks == 200) {
            PipeBlockEntity p1 = (PipeBlockEntity) level.getBlockEntity(BASE.east(1));
            PipeBlockEntity p3 = (PipeBlockEntity) level.getBlockEntity(BASE.east(3));
            boolean in = p1.key().arm(PipeType.CHEMICAL.ordinal(), Direction.WEST) == PipeKey.ARM_INPUT;
            boolean out = p3.key().arm(PipeType.CHEMICAL.ordinal(), Direction.EAST) == PipeKey.ARM_OUTPUT;
            long moved = amount(level, BASE.east(4));
            var net = com.xtri6.grimpipes.network.NetworkManager.get(level).networkAt(BASE.east(1), PipeType.CHEMICAL.ordinal());
            GrimPipes.LOGGER.info("[mekcheck] network={} inputs={} outputs={} members={}", net,
                    net == null ? -1 : net.inputs().size(), net == null ? -1 : net.outputs().size(), net == null ? -1 : net.members().size());
            IChemicalHandler srcSide = level.getCapability(CHEMICAL, BASE, Direction.EAST);
            IChemicalHandler dstSide = level.getCapability(CHEMICAL, BASE.east(4), Direction.WEST);
            GrimPipes.LOGGER.info("[mekcheck] source still {} mB; extract sim via EAST side = {}; dest insert sim via WEST = {}",
                    amount(level, BASE), srcSide == null ? "no handler" : srcSide.extractChemical(1000, Action.SIMULATE),
                    dstSide == null ? "no handler" : dstSide.insertChemical(new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.get(
                            ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen")), 1000), Action.SIMULATE));
            long omniMoved = amount(level, OMNI.east(4));
            GrimPipes.LOGGER.info("[mekcheck] omni line moved {} of {} mB oxygen", omniMoved, omniStart);
            boolean pass = in && out && start > 0 && moved == start && omniStart > 0 && omniMoved == omniStart;
            GrimPipes.LOGGER.info("[mekcheck] RESULT {}: inputConnected={} outputConnected={} moved {} of {} mB",
                    pass ? "PASS" : "FAIL", in, out, moved, start);
            server.halt(false);
        }
    }
}
