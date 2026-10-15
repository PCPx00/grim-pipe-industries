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

    // ---------------- 1.3.0: redstone, output priority, output filters ----------------

    private static Container[] splitSetup(GameTestHelper h) {
        Container a = chest(h, 0, 1, 2);
        Container low = chest(h, 2, 1, 3);   // south of the splitter
        Container high = chest(h, 2, 1, 1);  // north of the splitter
        PipeBlockEntity in = pipe(h, 1, 1, 2, PipeType.ITEM);
        pipe(h, 2, 1, 2, PipeType.ITEM);
        in.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        card(in, PipeType.ITEM, Direction.WEST, PipeTier.WRAITHBOUND);
        return new Container[]{a, low, high};
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void redstoneNeedsSignal(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 2, 1, 0);
        a.setItem(0, new ItemStack(Items.STICK, 16));
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        p.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        p.endConfig(PipeType.ITEM.ordinal(), Direction.WEST).setRedstone(com.xtri6.grimpipes.pipe.RedstoneMode.HIGH);
        h.runAfterDelay(40, () -> {
            h.assertTrue(count(b, Items.STICK) == 0, "moved without a redstone signal");
            h.setBlock(1, 2, 0, Blocks.REDSTONE_BLOCK);
            h.succeedWhen(() -> h.assertTrue(count(b, Items.STICK) == 16, "signal on, moved " + count(b, Items.STICK)));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void redstoneStopsWithSignal(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 2, 1, 0);
        a.setItem(0, new ItemStack(Items.STICK, 16));
        h.setBlock(1, 2, 0, Blocks.REDSTONE_BLOCK);
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        p.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        p.endConfig(PipeType.ITEM.ordinal(), Direction.WEST).setRedstone(com.xtri6.grimpipes.pipe.RedstoneMode.LOW);
        h.runAfterDelay(40, () -> {
            h.assertTrue(count(b, Items.STICK) == 0, "moved while powered");
            h.setBlock(1, 2, 0, Blocks.AIR);
            h.succeedWhen(() -> h.assertTrue(count(b, Items.STICK) == 16, "signal off, moved " + count(b, Items.STICK)));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void outputPriorityFillsHigherFirst(GameTestHelper h) {
        Container[] c = splitSetup(h);
        c[0].setItem(0, new ItemStack(Items.STICK, 32));
        PipeBlockEntity splitter = h.getBlockEntity(new BlockPos(2, 1, 2));
        splitter.endConfig(PipeType.ITEM.ordinal(), Direction.NORTH).setPriority(5);
        h.succeedWhen(() -> {
            h.assertTrue(count(c[2], Items.STICK) == 32, "high-priority chest has " + count(c[2], Items.STICK));
            h.assertTrue(count(c[1], Items.STICK) == 0, "low-priority chest got " + count(c[1], Items.STICK));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void outputFiltersRouteByItem(GameTestHelper h) {
        Container[] c = splitSetup(h);
        c[0].setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        c[0].setItem(1, new ItemStack(Items.DIRT, 16));
        PipeBlockEntity splitter = h.getBlockEntity(new BlockPos(2, 1, 2));
        filter(splitter, PipeType.ITEM, Direction.NORTH, FilterMode.WHITELIST, Items.COBBLESTONE);
        filter(splitter, PipeType.ITEM, Direction.SOUTH, FilterMode.BLACKLIST, Items.COBBLESTONE);
        h.succeedWhen(() -> {
            h.assertTrue(count(c[2], Items.COBBLESTONE) == 16 && count(c[2], Items.DIRT) == 0, "whitelist chest wrong");
            h.assertTrue(count(c[1], Items.DIRT) == 16 && count(c[1], Items.COBBLESTONE) == 0, "blacklist chest wrong");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void energyPriority(GameTestHelper h) {
        h.setBlock(0, 1, 2, Blocks.COPPER_BLOCK);
        h.setBlock(2, 1, 3, Blocks.IRON_BLOCK);
        h.setBlock(2, 1, 1, Blocks.IRON_BLOCK);
        PipeBlockEntity in = pipe(h, 1, 1, 2, PipeType.ENERGY);
        PipeBlockEntity splitter = pipe(h, 2, 1, 2, PipeType.ENERGY);
        in.setMode(PipeType.ENERGY.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        splitter.endConfig(PipeType.ENERGY.ordinal(), Direction.NORTH).setPriority(1);
        BlockPos high = h.absolutePos(new BlockPos(2, 1, 1));
        BlockPos low = h.absolutePos(new BlockPos(2, 1, 3));
        h.succeedWhen(() -> {
            int hi = TestCaps.ENERGY.containsKey(high) ? TestCaps.ENERGY.get(high).getEnergyStored() : 0;
            int lo = TestCaps.ENERGY.containsKey(low) ? TestCaps.ENERGY.get(low).getEnergyStored() : 0;
            h.assertTrue(hi == 100_000 && lo == 0, "high " + hi + " FE, low " + lo + " FE");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void outputEndMenuWorks(GameTestHelper h) {
        chest(h, 0, 1, 0);
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        net.minecraft.world.entity.player.Player player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.moveTo(h.absolutePos(new BlockPos(1, 2, 1)).getCenter());
        com.xtri6.grimpipes.menu.EndMenu menu = new com.xtri6.grimpipes.menu.EndMenu(0, player.getInventory(), p, PipeType.ITEM, Direction.WEST);
        h.assertTrue(!menu.isInput(), "should be an Output end");
        h.assertTrue(menu.filterStart() == 0, "Output ends must not have a card slot");
        for (int i = 0; i < 3; i++) {
            menu.clickMenuButton(player, com.xtri6.grimpipes.menu.EndMenu.BTN_PRIORITY_UP);
        }
        menu.clickMenuButton(player, com.xtri6.grimpipes.menu.EndMenu.BTN_PRIORITY_DOWN_10);
        h.assertTrue(p.endConfig(PipeType.ITEM.ordinal(), Direction.WEST).priority() == -7, "priority is "
                + p.endConfig(PipeType.ITEM.ordinal(), Direction.WEST).priority());
        menu.clickMenuButton(player, com.xtri6.grimpipes.menu.EndMenu.BTN_MODE); // Blacklist -> (skip Void) -> Whitelist
        h.assertTrue(menu.mode() == FilterMode.WHITELIST, "Output mode went to " + menu.mode());
        h.assertTrue(!menu.clickMenuButton(player, com.xtri6.grimpipes.menu.EndMenu.BTN_REDSTONE), "Output ends have no redstone mode");
        h.succeed();
    }

    /**
     * Clicks a pipe the way a real player does. Mirrors Minecraft's own rule in ServerPlayerGameMode#useItemOn:
     * while sneaking with an item in hand, the block only gets the click if the item opts in with
     * doesSneakBypassUse (which is exactly what was broken before 1.3.1).
     */
    private static void playerClick(GameTestHelper h, net.minecraft.world.entity.player.Player player, BlockPos rel,
                                    double px, double py, double pz, Direction face, boolean sneak) {
        BlockPos abs = h.absolutePos(rel);
        player.setShiftKeyDown(sneak);
        ItemStack held = player.getMainHandItem();
        boolean blockGetsClick = !player.isSecondaryUseActive() || held.isEmpty()
                || held.doesSneakBypassUse(h.getLevel(), abs, player);
        if (blockGetsClick) {
            Vec3 hitVec = new Vec3(abs.getX() + px / 16.0, abs.getY() + py / 16.0, abs.getZ() + pz / 16.0);
            h.getLevel().getBlockState(abs).useItemOn(held, h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(hitVec, face, abs, false));
        }
        player.setShiftKeyDown(false);
    }

    private static net.minecraft.world.entity.player.Player wrenchPlayer(GameTestHelper h) {
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Registration.WRENCH.get()));
        return player;
    }

    /** Sneak-clicking a pipe end with the wrench disconnects it from that block, and stays disconnected. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sneakWrenchDisconnectsFromBlock(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 2, 1, 0);
        a.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        p.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        var player = wrenchPlayer(h);

        // Top of the west end plate (x 0-2 px, plate reaches y=12 px).
        playerClick(h, player, new BlockPos(1, 1, 0), 1, 12, 8, Direction.UP, true);
        h.assertTrue(p.mode(PipeType.ITEM.ordinal(), Direction.WEST) == ConnectionMode.DISABLED, "end not disconnected");
        h.assertTrue(p.key().arm(PipeType.ITEM.ordinal(), Direction.WEST) == PipeKey.ARM_NONE, "arm still drawn to the chest");
        int before = count(b, Items.COBBLESTONE);
        h.runAfterDelay(60, () -> {
            h.assertTrue(count(b, Items.COBBLESTONE) == before, "items still pulled from a disconnected block");
            // Plain click on the core facing west reconnects it.
            playerClick(h, player, new BlockPos(1, 1, 0), 5, 8, 8, Direction.WEST, false);
            h.assertTrue(p.mode(PipeType.ITEM.ordinal(), Direction.WEST) == ConnectionMode.OUTPUT, "not reconnected");
            h.assertTrue(p.key().arm(PipeType.ITEM.ordinal(), Direction.WEST) == PipeKey.ARM_OUTPUT, "arm not back");
            h.succeed();
        });
    }

    /** A normal (non-sneak) wrench click on an end still flips Output / Input. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void wrenchClickTogglesEnd(GameTestHelper h) {
        chest(h, 0, 1, 0);
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        var player = wrenchPlayer(h);
        playerClick(h, player, new BlockPos(1, 1, 0), 1, 12, 8, Direction.UP, false);
        h.assertTrue(p.mode(PipeType.ITEM.ordinal(), Direction.WEST) == ConnectionMode.INPUT, "end not set to Input");
        playerClick(h, player, new BlockPos(1, 1, 0), 1, 12, 8, Direction.UP, false);
        h.assertTrue(p.mode(PipeType.ITEM.ordinal(), Direction.WEST) == ConnectionMode.OUTPUT, "end not back to Output");
        h.succeed();
    }

    /** Sneak-clicking a strand's band on the core takes just that strand out and gives it back. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sneakWrenchRemovesOneStrand(GameTestHelper h) {
        PipeBlockEntity p = pipe(h, 1, 1, 1, PipeType.ITEM, PipeType.FLUID);
        var player = wrenchPlayer(h);
        // Core top face is split along x: item band 5-8 px, fluid band 8-11 px.
        playerClick(h, player, new BlockPos(1, 1, 1), 6.5, 11, 8, Direction.UP, true);
        h.assertTrue(!p.has(PipeType.ITEM) && p.has(PipeType.FLUID), "wrong strand removed");
        h.assertTrue(player.getInventory().countItem(Registration.pipeItem(PipeType.ITEM)) == 1, "item pipe not given back");
        // Sneaking with the wrench must still reach the pipe (the bug this guards against).
        h.assertTrue(new ItemStack(Registration.WRENCH.get()).doesSneakBypassUse(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1)), player),
                "wrench does not pass sneak-clicks to pipes");
        h.assertTrue(!new ItemStack(Registration.WRENCH.get()).doesSneakBypassUse(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), player),
                "wrench should only bypass sneak on pipes");
        h.succeed();
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

    // ---- 1.4.0 ----

    /** Input pipe at (1,1,1) pulling chest (0,1,1); a pipe line to x=5; a near chest at (2,1,2) and a far one at (5,1,2). */
    private static Container[] nearFarSetup(GameTestHelper h, com.xtri6.grimpipes.pipe.Distribution dist, int sticks) {
        Container a = chest(h, 0, 1, 1);
        Container near = chest(h, 2, 1, 2);
        Container far = chest(h, 5, 1, 2);
        a.setItem(0, new ItemStack(Items.STICK, sticks));
        PipeBlockEntity in = pipe(h, 1, 1, 1, PipeType.ITEM);
        for (int x = 2; x <= 5; x++) {
            pipe(h, x, 1, 1, PipeType.ITEM);
        }
        in.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        in.endConfig(PipeType.ITEM.ordinal(), Direction.WEST).setDistribution(dist);
        return new Container[]{a, near, far};
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void nearestFirstFillsClosest(GameTestHelper h) {
        Container[] c = nearFarSetup(h, com.xtri6.grimpipes.pipe.Distribution.NEAREST, 32);
        h.succeedWhen(() -> {
            h.assertTrue(count(c[1], Items.STICK) == 32, "near chest has " + count(c[1], Items.STICK));
            h.assertTrue(count(c[2], Items.STICK) == 0, "far chest got " + count(c[2], Items.STICK));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void randomReachesEveryOutput(GameTestHelper h) {
        Container[] c = nearFarSetup(h, com.xtri6.grimpipes.pipe.Distribution.RANDOM, 64);
        h.succeedWhen(() -> {
            int near = count(c[1], Items.STICK);
            int far = count(c[2], Items.STICK);
            h.assertTrue(near + far == 64, "moved " + (near + far));
            h.assertTrue(near > 0 && far > 0, "random never picked one side: " + near + " / " + far);
        });
    }

    private static ItemStack tagFilter(String rules) {
        ItemStack f = new ItemStack(Registration.TAG_FILTER.get());
        com.xtri6.grimpipes.item.TagFilterItem.setPatterns(f, rules);
        return f;
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void tagFilterPullsByTag(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 2, 1, 0);
        a.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
        a.setItem(1, new ItemStack(Items.COBBLESTONE, 16));
        a.setItem(2, new ItemStack(Items.GOLD_INGOT, 8));
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        p.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        EndConfig cfg = p.endConfig(PipeType.ITEM.ordinal(), Direction.WEST);
        cfg.setMode(FilterMode.WHITELIST);
        cfg.filter.setStackInSlot(0, tagFilter("#c:ingots"));
        h.succeedWhen(() -> {
            h.assertTrue(count(b, Items.IRON_INGOT) == 16 && count(b, Items.GOLD_INGOT) == 8, "ingots not all moved");
            h.assertTrue(count(b, Items.COBBLESTONE) == 0, "cobblestone got through a #c:ingots whitelist");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void tagFilterGlobAndMod(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 2, 1, 0);
        a.setItem(0, new ItemStack(Items.OAK_LOG, 8));
        a.setItem(1, new ItemStack(Items.BIRCH_LOG, 8));
        a.setItem(2, new ItemStack(Items.STICK, 8));
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        p.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        EndConfig cfg = p.endConfig(PipeType.ITEM.ordinal(), Direction.WEST);
        cfg.setMode(FilterMode.BLACKLIST);
        cfg.filter.setStackInSlot(0, tagFilter("*_log"));
        // Rule parsing on its own: a mod rule and a fluid tag.
        h.assertTrue(com.xtri6.grimpipes.item.TagFilterItem.matches(tagFilter("@minecraft"), new ItemStack(Items.DIRT)), "@minecraft");
        h.assertTrue(!com.xtri6.grimpipes.item.TagFilterItem.matches(tagFilter("@mekanism"), new ItemStack(Items.DIRT)), "@mekanism");
        h.assertTrue(com.xtri6.grimpipes.item.TagFilterItem.matches(tagFilter("#minecraft:water"),
                new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 100)), "#minecraft:water fluid");
        h.succeedWhen(() -> {
            h.assertTrue(count(b, Items.STICK) == 8, "sticks not moved");
            h.assertTrue(count(b, Items.OAK_LOG) == 0 && count(b, Items.BIRCH_LOG) == 0, "logs got past a *_log blacklist");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void exactMatchChecksComponents(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 2, 1, 0);
        ItemStack worn = new ItemStack(Items.DIAMOND_SWORD);
        worn.setDamageValue(100);
        a.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        a.setItem(1, worn);
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM);
        p.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        EndConfig cfg = p.endConfig(PipeType.ITEM.ordinal(), Direction.WEST);
        cfg.setMode(FilterMode.WHITELIST);
        cfg.filter.setStackInSlot(0, new ItemStack(Items.DIAMOND_SWORD));
        cfg.setMatchComponents(true);
        h.runAfterDelay(60, () -> {
            h.assertTrue(count(b, Items.DIAMOND_SWORD) == 1, "B has " + count(b, Items.DIAMOND_SWORD) + " swords");
            h.assertTrue(!b.getItem(0).isDamaged(), "the worn sword moved");
            cfg.setMatchComponents(false);
            h.succeedWhen(() -> h.assertTrue(count(b, Items.DIAMOND_SWORD) == 2, "worn sword not moved with exact match off"));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void imprintCopiesAndPastes(GameTestHelper h) {
        chest(h, 0, 1, 0);
        chest(h, 0, 1, 2);
        PipeBlockEntity from = pipe(h, 1, 1, 0, PipeType.ITEM);
        PipeBlockEntity to = pipe(h, 1, 1, 2, PipeType.ITEM);
        int t = PipeType.ITEM.ordinal();
        from.setMode(t, Direction.WEST, ConnectionMode.INPUT);
        card(from, PipeType.ITEM, Direction.WEST, PipeTier.RESTLESS);
        filter(from, PipeType.ITEM, Direction.WEST, FilterMode.WHITELIST, Items.STICK, Items.DIAMOND);
        from.endConfig(t, Direction.WEST).setRedstone(com.xtri6.grimpipes.pipe.RedstoneMode.HIGH);
        from.endConfig(t, Direction.WEST).setDistribution(com.xtri6.grimpipes.pipe.Distribution.NEAREST);

        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack imprint = new ItemStack(Registration.IMPRINT.get());
        player.setShiftKeyDown(true);
        com.xtri6.grimpipes.item.ImprintItem.use(imprint, from,
                new PipeGeometry.Hit(PipeGeometry.PartKind.TIP, Direction.WEST, t, PipeKey.ARM_INPUT), player);
        player.setShiftKeyDown(false);
        com.xtri6.grimpipes.item.ImprintItem.use(imprint, to,
                new PipeGeometry.Hit(PipeGeometry.PartKind.TIP, Direction.WEST, t, PipeKey.ARM_OUTPUT), player);

        EndConfig pasted = to.endConfig(t, Direction.WEST);
        h.assertTrue(to.mode(t, Direction.WEST) == ConnectionMode.INPUT, "mode not pasted");
        h.assertTrue(pasted.mode() == FilterMode.WHITELIST, "filter mode not pasted");
        h.assertTrue(pasted.filter.getStackInSlot(1).is(Items.DIAMOND), "filter not pasted");
        h.assertTrue(pasted.redstone() == com.xtri6.grimpipes.pipe.RedstoneMode.HIGH, "redstone not pasted");
        h.assertTrue(pasted.distribution() == com.xtri6.grimpipes.pipe.Distribution.NEAREST, "distribution not pasted");
        h.assertTrue(pasted.tier() == PipeTier.HOLLOW, "speed card was copied");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void soulLinkJoinsNetworks(GameTestHelper h) {
        Container a = chest(h, 0, 1, 1);
        Container b = chest(h, 6, 1, 1);
        a.setItem(0, new ItemStack(Items.STICK, 32));
        PipeBlockEntity left = pipe(h, 1, 1, 1, PipeType.ITEM);
        PipeBlockEntity right = pipe(h, 5, 1, 1, PipeType.ITEM);
        left.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        card(left, PipeType.ITEM, Direction.WEST, PipeTier.RESTLESS);

        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack link = new ItemStack(Registration.SOUL_LINK.get());
        h.runAfterDelay(20, () -> {
            h.assertTrue(count(b, Items.STICK) == 0, "moved before linking");
            com.xtri6.grimpipes.item.SoulLinkItem.use(link, left, player);
            com.xtri6.grimpipes.item.SoulLinkItem.use(link, right, player);
            h.assertTrue(h.absolutePos(new BlockPos(5, 1, 1)).equals(left.soulLink()), "left not linked");
            h.succeedWhen(() -> h.assertTrue(count(b, Items.STICK) == 32, "B has " + count(b, Items.STICK)));
        });
    }

    /** A click through Minecraft's real rules, including the RightClickBlock event and the sneak rule. */
    private static void realClick(GameTestHelper h, net.minecraft.world.entity.player.Player player, BlockPos rel, Direction face, boolean sneak) {
        BlockPos abs = h.absolutePos(rel);
        player.setShiftKeyDown(sneak);
        var hit = new net.minecraft.world.phys.BlockHitResult(abs.getCenter().relative(face, 0.5), face, abs, false);
        var event = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, net.minecraft.world.InteractionHand.MAIN_HAND, abs, hit);
        ItemStack held = player.getMainHandItem();
        boolean skip = player.isSecondaryUseActive() && !held.isEmpty() && !held.doesSneakBypassUse(h.getLevel(), abs, player);
        if (!event.isCanceled() && (event.getUseBlock().isTrue() || (event.getUseBlock().isDefault() && !skip))) {
            h.getLevel().getBlockState(abs).useItemOn(held, h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        }
        player.setShiftKeyDown(false);
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void facadeHidesPipeAndKeepsWorking(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        Container b = chest(h, 3, 1, 0);
        a.setItem(0, new ItemStack(Items.STICK, 16));
        PipeBlockEntity p1 = pipe(h, 1, 1, 0, PipeType.ITEM);
        PipeBlockEntity p2 = pipe(h, 2, 1, 0, PipeType.ITEM);
        p1.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);

        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_BRICKS, 3));
        realClick(h, player, new BlockPos(2, 1, 0), Direction.UP, true);
        h.assertTrue(p2.facade() != null && p2.facade().is(Blocks.STONE_BRICKS), "facade not applied");
        h.assertTrue(player.getMainHandItem().getCount() == 2, "facade block not used up");
        h.assertTrue(PipeGeometry.shape(p2.key()) == net.minecraft.world.phys.shapes.Shapes.block(), "hidden pipe is not a full block");
        // A normal click with a block still places blocks as usual (not a facade).
        realClick(h, player, new BlockPos(1, 1, 0), Direction.UP, false);
        h.assertTrue(p1.facade() == null, "a plain click turned a pipe into a facade");

        h.succeedWhen(() -> {
            h.assertTrue(count(b, Items.STICK) == 16, "items stopped flowing through the hidden pipe");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Registration.WRENCH.get()));
            realClick(h, player, new BlockPos(2, 1, 0), Direction.UP, false);
            h.assertTrue(p2.facade() == null, "wrench did not remove the facade");
            h.assertTrue(player.getInventory().countItem(Items.STONE_BRICKS) >= 1, "facade block not given back");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void flowAnimationFollowsActivity(GameTestHelper h) {
        Container a = chest(h, 0, 1, 0);
        chest(h, 3, 1, 0);
        a.setItem(0, new ItemStack(Items.STICK, 64));
        PipeBlockEntity p1 = pipe(h, 1, 1, 0, PipeType.ITEM);
        PipeBlockEntity p2 = pipe(h, 2, 1, 0, PipeType.ITEM);
        p1.setMode(PipeType.ITEM.ordinal(), Direction.WEST, ConnectionMode.INPUT);
        int t = PipeType.ITEM.ordinal();
        h.runAfterDelay(30, () -> {
            h.assertTrue(p1.key().flowing(t) && p2.key().flowing(t), "no flow while moving");
            // Stop it: the source is emptied, so the line goes idle and the animation must switch off.
            a.clearContent();
            h.succeedWhen(() -> h.assertTrue(!p1.key().flowing(t) && !p2.key().flowing(t), "still flowing while idle"));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void overviewToggleSwitchesEnd(GameTestHelper h) {
        chest(h, 0, 1, 0);
        PipeBlockEntity p = pipe(h, 1, 1, 0, PipeType.ITEM, PipeType.FLUID);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        com.xtri6.grimpipes.block.PipeBlock.toggleEnd(p, player, PipeType.ITEM.ordinal(), Direction.WEST);
        h.assertTrue(p.mode(PipeType.ITEM.ordinal(), Direction.WEST) == ConnectionMode.INPUT, "not switched to Input");
        h.assertTrue(p.mode(PipeType.FLUID.ordinal(), Direction.WEST) == ConnectionMode.OUTPUT, "the other strand changed");
        // Links and empty sides are left alone.
        com.xtri6.grimpipes.block.PipeBlock.toggleEnd(p, player, PipeType.ITEM.ordinal(), Direction.UP);
        h.assertTrue(p.mode(PipeType.ITEM.ordinal(), Direction.UP) == ConnectionMode.OUTPUT, "an empty side was changed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void syncedRatesOverrideLocalConfig(GameTestHelper h) {
        int[] rates = com.xtri6.grimpipes.pipe.SyncedRates.fromConfig();
        int before = PipeTier.HOLLOW.itemsPerOperation();
        rates[0] = before + 3;
        com.xtri6.grimpipes.pipe.SyncedRates.apply(rates);
        int synced = PipeTier.HOLLOW.itemsPerOperation();
        com.xtri6.grimpipes.pipe.SyncedRates.clear();
        h.assertTrue(synced == before + 3, "synced speed not used: " + synced);
        h.assertTrue(PipeTier.HOLLOW.itemsPerOperation() == before, "config not restored");
        h.succeed();
    }
}
