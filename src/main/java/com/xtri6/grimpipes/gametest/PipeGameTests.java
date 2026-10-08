package com.xtri6.grimpipes.gametest;

import com.xtri6.grimpipes.GrimPipes;
import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.block.EndConfig;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.network.NetworkManager;
import com.xtri6.grimpipes.pipe.ConnectionMode;
import com.xtri6.grimpipes.pipe.FilterMode;
import com.xtri6.grimpipes.pipe.PipeGeometry;
import com.xtri6.grimpipes.pipe.PipeKey;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Test-only (never shipped). Run with: gradlew runGameTestServer */
@GameTestHolder(GrimPipes.MODID)
@PrefixGameTestTemplate(false)
public final class PipeGameTests {
    private PipeGameTests() {
    }

    private static PipeBlockEntity pipe(GameTestHelper h, int x, int y, int z, PipeType... types) {
        BlockPos rel = new BlockPos(x, y, z);
        if (!(h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof PipeBlockEntity)) {
            h.setBlock(rel, Registration.PIPE.get());
        }
        PipeBlockEntity be = h.getBlockEntity(rel);
        for (PipeType t : types) {
            be.addType(t);
        }
        return be;
    }

    private static void card(PipeBlockEntity be, PipeType type, Direction side, PipeTier tier) {
        be.swapCard(type.ordinal(), side, new ItemStack(Registration.UPGRADES.get(tier).get()));
    }

    private static void filter(PipeBlockEntity be, PipeType type, Direction side, FilterMode mode, Item... items) {
        EndConfig cfg = be.endConfig(type.ordinal(), side);
        cfg.setMode(mode);
        for (int i = 0; i < items.length; i++) {
            cfg.filter.setStackInSlot(i, new ItemStack(items[i]));
        }
    }

    private static Container chest(GameTestHelper h, int x, int y, int z) {
        h.setBlock(new BlockPos(x, y, z), Blocks.CHEST);
        return h.getBlockEntity(new BlockPos(x, y, z));
    }

    private static int count(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) {
                n += c.getItem(i).getCount();
            }
        }
        return n;
    }

    /** Chest -> two item pipes -> chest, pulling with the wrench-set Input end. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void itemsFlowChestToChest(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 3, 1, 0);
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        a.setItem(5, new ItemStack(Items.COBBLESTONE, 64));
        PipeBlockEntity p1 = pipe(h, 1, 1, 0, PipeType.ITEM);
        pipe(h, 2, 1, 0, PipeType.ITEM);
        p1.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        h.succeedWhen(() -> {
            h.assertTrue(count(b, Items.COBBLESTONE) == 128, "B has " + count(b, Items.COBBLESTONE));
            h.assertTrue(count(a, Items.COBBLESTONE) == 0, "A still has items");
        });
    }

    /**
     * Two different pipes on the SAME side of one chest: the Omni strand fills chest A from chest C while the
     * Item strand in the same block pulls chest A out into chest B.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void twoPipesShareOneChestSide(GameTestHelper h) {
        Container a = chest(h, 2, 1, 0);
        Container b = chest(h, 4, 1, 1);
        Container c = chest(h, 0, 1, 1);
        c.setItem(3, new ItemStack(Items.DIAMOND, 32));

        PipeBlockEntity bundle = pipe(h, 2, 1, 1, PipeType.ITEM, PipeType.OMNI);
        pipe(h, 3, 1, 1, PipeType.ITEM);
        PipeBlockEntity omniIn = pipe(h, 1, 1, 1, PipeType.OMNI);
        bundle.setMode(PipeType.ITEM.ordinal(), Direction.NORTH, ConnectionMode.INPUT);
        omniIn.setMode(PipeType.OMNI.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        card(bundle, PipeType.ITEM, Direction.NORTH, PipeTier.RESTLESS);
        card(omniIn, PipeType.OMNI, Direction.WEST, PipeTier.RESTLESS);

        h.succeedWhen(() -> {
            PipeKey key = bundle.key();
            h.assertTrue(key.arm(PipeType.ITEM.ordinal(), Direction.NORTH) == PipeKey.ARM_INPUT, "item strand not Input on chest");
            h.assertTrue(key.arm(PipeType.OMNI.ordinal(), Direction.NORTH) == PipeKey.ARM_OUTPUT, "omni strand not Output on chest");
            h.assertTrue(count(b, Items.DIAMOND) == 32, "B has " + count(b, Items.DIAMOND) + " diamonds");
            h.assertTrue(count(a, Items.DIAMOND) == 0 && count(c, Items.DIAMOND) == 0, "diamonds left behind");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void energyFlows(GameTestHelper h) {
        h.setBlock(0, 1, 0, Blocks.COPPER_BLOCK);
        h.setBlock(3, 1, 0, Blocks.IRON_BLOCK);
        PipeBlockEntity p1 = pipe(h, 1, 1, 0, PipeType.ENERGY);
        pipe(h, 2, 1, 0, PipeType.ENERGY);
        p1.setMode(PipeType.ENERGY.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        BlockPos sink = h.absolutePos(new BlockPos(3, 1, 0));
        h.succeedWhen(() -> {
            int stored = TestCaps.ENERGY.containsKey(sink) ? TestCaps.ENERGY.get(sink).getEnergyStored() : 0;
            h.assertTrue(stored == 100_000, "sink has " + stored + " FE");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void fluidsFlow(GameTestHelper h) {
        h.setBlock(0, 1, 0, Blocks.GOLD_BLOCK);
        h.setBlock(3, 1, 0, Blocks.LAPIS_BLOCK);
        PipeBlockEntity p1 = pipe(h, 1, 1, 0, PipeType.FLUID);
        pipe(h, 2, 1, 0, PipeType.FLUID);
        p1.setMode(PipeType.FLUID.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        BlockPos sink = h.absolutePos(new BlockPos(3, 1, 0));
        h.succeedWhen(() -> {
            int stored = TestCaps.FLUID.containsKey(sink) ? TestCaps.FLUID.get(sink).getFluidAmount() : 0;
            h.assertTrue(stored == 10_000, "sink has " + stored + " mB");
        });
    }

    /** A hopper pushing into a pipe side set to Input gets routed onward with no extraction needed. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void hopperPushesIntoInputSide(GameTestHelper h) {
        Container b = chest(h, 2, 1, 0);
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        h.setBlock(1, 2, 0, Blocks.HOPPER);
        Container hopper = h.getBlockEntity(new BlockPos(1, 2, 0));
        hopper.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        p.setMode(PipeType.ITEM.ordinal(), Direction.UP, ConnectionMode.INPUT);
        h.succeedWhen(() -> h.assertTrue(count(b, Items.IRON_INGOT) == 10, "chest has " + count(b, Items.IRON_INGOT)));
    }

    /**
     * A Soulforged card in ONLY the Input end moves a whole chest in a couple of seconds, through a line of
     * plain pipes with no cards at all.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cardOnlyNeededOnInputEnd(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 5, 1, 0);
        for (int i = 0; i < 27; i++) {
            a.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        for (int x = 2; x <= 4; x++) {
            pipe(h, x, 1, 0, PipeType.ITEM);
        }
        p.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        card(p, PipeType.ITEM, Direction.WEST, PipeTier.SOULFORGED);
        h.succeedWhen(() -> h.assertTrue(count(b, Items.COBBLESTONE) == 27 * 64, "moved " + count(b, Items.COBBLESTONE)));
    }

    /** Hollow is the slowest tier: 4 items every 10 ticks. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hollowIsRateLimited(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 2, 1, 0);
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        p.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        h.runAfterDelay(41, () -> {
            int moved = count(b, Items.COBBLESTONE);
            h.assertTrue(moved > 0 && moved <= 20, "Hollow moved " + moved + " in 2 seconds");
            h.succeed();
        });
    }

    private static Container[] filterSetup(GameTestHelper h, FilterMode mode, Item... filtered) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 2, 1, 0);
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        a.setItem(1, new ItemStack(Items.DIRT, 16));
        a.setItem(2, new ItemStack(Items.SAND, 16));
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        p.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        card(p, PipeType.ITEM, Direction.WEST, PipeTier.WRAITHBOUND);
        filter(p, PipeType.ITEM, Direction.WEST, mode, filtered);
        return new Container[]{a, b};
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void whitelistPullsOnlyListed(GameTestHelper h) {
        Container[] c = filterSetup(h, FilterMode.WHITELIST, Items.COBBLESTONE);
        h.runAfterDelay(60, () -> {
            h.assertTrue(count(c[1], Items.COBBLESTONE) == 16, "cobble not pulled");
            h.assertTrue(count(c[1], Items.DIRT) == 0 && count(c[1], Items.SAND) == 0, "whitelist leaked");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void blacklistSkipsListed(GameTestHelper h) {
        Container[] c = filterSetup(h, FilterMode.BLACKLIST, Items.DIRT);
        h.runAfterDelay(60, () -> {
            h.assertTrue(count(c[1], Items.COBBLESTONE) == 16 && count(c[1], Items.SAND) == 16, "allowed items not pulled");
            h.assertTrue(count(c[0], Items.DIRT) == 16, "blacklisted dirt was pulled");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void voidDeletesListed(GameTestHelper h) {
        Container[] c = filterSetup(h, FilterMode.VOID, Items.DIRT);
        h.runAfterDelay(60, () -> {
            h.assertTrue(count(c[1], Items.COBBLESTONE) == 16 && count(c[1], Items.SAND) == 16, "other items not sent on");
            h.assertTrue(count(c[0], Items.DIRT) == 0 && count(c[1], Items.DIRT) == 0, "dirt was not voided");
            h.succeed();
        });
    }

    /** A fluid filter uses buckets: a water bucket in a whitelist lets only water through. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void fluidWhitelistBlocksOtherFluids(GameTestHelper h) {
        h.setBlock(0, 1, 0, Blocks.GOLD_BLOCK); // holds water
        h.setBlock(2, 1, 0, Blocks.LAPIS_BLOCK);
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.FLUID);
        p.setMode(PipeType.FLUID.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        filter(p, PipeType.FLUID, Direction.WEST, FilterMode.WHITELIST, Items.LAVA_BUCKET);
        BlockPos sink = h.absolutePos(new BlockPos(2, 1, 0));
        h.runAfterDelay(40, () -> {
            int stored = TestCaps.FLUID.containsKey(sink) ? TestCaps.FLUID.get(sink).getFluidAmount() : 0;
            h.assertTrue(stored == 0, "water passed a lava-only whitelist: " + stored);
            filter(p, PipeType.FLUID, Direction.WEST, FilterMode.WHITELIST, Items.WATER_BUCKET);
            h.succeedWhen(() -> h.assertTrue(TestCaps.FLUID.get(sink).getFluidAmount() == 10_000, "water not moved"));
        });
    }

    /** Drives the Input-end screen's server side like a player would. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void endMenuWorks(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 2, 1, 0);
        a.setItem(0, new ItemStack(Items.DIRT, 8));
        a.setItem(1, new ItemStack(Items.COBBLESTONE, 8));
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        p.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);

        net.minecraft.world.entity.player.Player player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.moveTo(h.absolutePos(new BlockPos(1, 2, 1)).getCenter());
        player.getInventory().setItem(0, new ItemStack(Registration.UPGRADES.get(PipeTier.SOULFORGED).get()));
        player.getInventory().setItem(1, new ItemStack(Items.DIRT, 5));
        com.xtri6.grimpipes.menu.EndMenu menu = new com.xtri6.grimpipes.menu.EndMenu(0, player.getInventory(), p, PipeType.ITEM, Direction.WEST);
        int hotbar0 = menu.slots.size() - 9;

        menu.quickMoveStack(player, hotbar0);            // shift-click the card in
        h.assertTrue(p.endTier(PipeType.ITEM.ordinal(), Direction.WEST) == PipeTier.SOULFORGED, "card not installed");
        h.assertTrue(player.getInventory().getItem(0).isEmpty(), "card still in inventory");

        menu.quickMoveStack(player, hotbar0 + 1);        // shift-click dirt into the filter (copy only)
        h.assertTrue(player.getInventory().getItem(1).getCount() == 5, "filter ate the real item");
        h.assertTrue(menu.slots.get(1).getItem().is(Items.DIRT), "dirt not in filter");

        menu.clickMenuButton(player, com.xtri6.grimpipes.menu.EndMenu.BTN_MODE);  // Blacklist -> Void
        h.assertTrue(menu.mode() == FilterMode.VOID, "mode is " + menu.mode());
        h.assertTrue(menu.stillValid(player), "menu closed");

        h.succeedWhen(() -> {
            h.assertTrue(count(b, Items.COBBLESTONE) == 8, "cobble not delivered");
            h.assertTrue(count(a, Items.DIRT) == 0 && count(b, Items.DIRT) == 0, "dirt not voided");
            // Switching the end to Output closes the screen.
            p.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.OUTPUT);
            h.assertTrue(!menu.stillValid(player), "menu should close when the end is no longer Input");
        });
    }

    /** Disconnecting the link between two pipes stops flow; reconnecting resumes it. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void wrenchDisconnectAndReconnect(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 3, 1, 0);
        a.setItem(0, new ItemStack(Items.STICK, 16));
        PipeBlockEntity p1 = pipe(h, 1, 1, 0, PipeType.ITEM);
        PipeBlockEntity p2 = pipe(h, 2, 1, 0, PipeType.ITEM);
        p1.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        p1.setMode(PipeType.ITEM.ordinal(), Direction.EAST, ConnectionMode.DISABLED);
        p2.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.DISABLED);
        h.runAfterDelay(60, () -> {
            h.assertTrue(count(b, Items.STICK) == 0, "items crossed a disconnected link");
            h.assertTrue(p1.key().arm(PipeType.ITEM.ordinal(), Direction.EAST) == PipeKey.ARM_NONE, "arm still drawn");
            p1.setMode(PipeType.ITEM.ordinal(), Direction.EAST, ConnectionMode.OUTPUT);
            p2.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.OUTPUT);
            h.succeedWhen(() -> h.assertTrue(count(b, Items.STICK) == 16, "after reconnect B has " + count(b, Items.STICK)));
        });
    }

    /** The wrench targets the exact strand band under the crosshair, on every face of every part of a 5-strand bundle. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wrenchPicksTheRightStrand(GameTestHelper h) {
        PipeKey.Builder kb = new PipeKey.Builder().types(31);
        for (PipeType t : PipeType.VALUES) {
            kb.arm(t.ordinal(), Direction.NORTH, t.ordinal() % 2 == 0 ? PipeKey.ARM_INPUT : PipeKey.ARM_OUTPUT);
            kb.arm(t.ordinal(), Direction.EAST, PipeKey.ARM_LINK);
        }
        PipeKey key = kb.build();
        int checked = 0;
        for (PipeGeometry.Part part : PipeGeometry.layout(key).parts()) {
            for (Direction face : Direction.values()) {
                if (part.dir() != null && face.getAxis() == part.dir().getAxis()) {
                    continue; // part ends are not clickable surfaces
                }
                if (part.kind() == PipeGeometry.PartKind.CORE && (face == Direction.NORTH || face == Direction.EAST)) {
                    continue; // covered by arms
                }
                for (int i = 0; i < part.bands(); i++) {
                    float[][] b = part.band(face, i);
                    double[] c = {(b[0][0] + b[1][0]) / 2, (b[0][1] + b[1][1]) / 2, (b[0][2] + b[1][2]) / 2};
                    int a = face.getAxis().ordinal();
                    c[a] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? b[1][a] : b[0][a];
                    PipeGeometry.Hit hit = PipeGeometry.pick(key, new Vec3(c[0], c[1], c[2]));
                    h.assertTrue(hit != null && hit.type() == part.types()[i] && hit.kind() == part.kind(),
                            "wrong pick on " + part.kind() + " " + part.dir() + " face " + face + " band " + i + ": " + hit);
                    checked++;
                }
            }
        }
        h.assertTrue(checked > 50, "only checked " + checked);
        h.succeed();
    }

    /** A lone pipe and a 5-strand bundle use exactly the same casing, so they line up at the block face. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bundlesMatchSinglePipes(GameTestHelper h) {
        PipeKey single = new PipeKey.Builder().types(PipeType.ITEM.bit()).arm(PipeType.ITEM.ordinal(), Direction.EAST, PipeKey.ARM_LINK).build();
        PipeKey.Builder kb = new PipeKey.Builder().types(31);
        kb.arm(PipeType.ITEM.ordinal(), Direction.WEST, PipeKey.ARM_LINK);
        for (PipeType t : PipeType.VALUES) {
            kb.arm(t.ordinal(), Direction.NORTH, PipeKey.ARM_OUTPUT);
        }
        PipeKey bundle = kb.build();
        float[][] a = armBox(single, Direction.EAST);
        float[][] b = armBox(bundle, Direction.WEST);
        for (int i = 1; i < 3; i++) { // compare the cross-section (y, z)
            h.assertTrue(a[0][i] == b[0][i] && a[1][i] == b[1][i], "arm sizes differ on axis " + i);
        }
        h.succeed();
    }

    private static float[][] armBox(PipeKey key, Direction dir) {
        for (PipeGeometry.Part p : PipeGeometry.layout(key).parts()) {
            if (p.kind() == PipeGeometry.PartKind.ARM && p.dir() == dir) {
                return new float[][]{p.min(), p.max()};
            }
        }
        throw new IllegalStateException("no arm " + dir);
    }

    /**
     * Stress: a 32x32 grid of 1024 pipes forming one network, 16 source chests and 16 sink chests.
     * Fails if pipe logic averages more than 1 ms per tick.
     */
    @GameTest(template = "big", timeoutTicks = 1200, batch = "perf")
    public static void bigNetworkStaysCheap(GameTestHelper h) {
        Container[] sinks = new Container[16];
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                pipe(h, x + 1, 1, z + 1, PipeType.ITEM);
            }
        }
        for (int i = 0; i < 16; i++) {
            Container src = chest(h, 1 + i * 2, 2, 1);
            src.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
            PipeBlockEntity under = h.getBlockEntity(new BlockPos(1 + i * 2, 1, 1));
            under.setMode(PipeType.ITEM.ordinal(), Direction.UP, ConnectionMode.INPUT);
            sinks[i] = chest(h, 1 + i * 2, 2, 32);
        }
        NetworkManager manager = NetworkManager.get(h.getLevel());
        h.runAfterDelay(5, manager::resetProfile);
        h.succeedWhen(() -> {
            int total = 0;
            for (Container s : sinks) {
                total += count(s, Items.COBBLESTONE);
            }
            h.assertTrue(total == 16 * 64, "delivered " + total);
            long avg = manager.averageTickNanos();
            GrimPipes.LOGGER.info("[perf] 1024-pipe network, 32 ends: average {} us per tick (all networks in level)", avg / 1000.0);
            h.assertTrue(avg < 1_000_000, "pipe logic too slow: " + avg / 1000 + " us/tick");
        });
    }
}
