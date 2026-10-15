package com.xtri6.grimpipes.block;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.item.ImprintItem;
import com.xtri6.grimpipes.item.PipeItem;
import com.xtri6.grimpipes.item.SoulLinkItem;
import com.xtri6.grimpipes.item.UpgradeItem;
import com.xtri6.grimpipes.menu.EndMenu;
import com.xtri6.grimpipes.pipe.ConnectionMode;
import com.xtri6.grimpipes.pipe.PipeGeometry;
import com.xtri6.grimpipes.pipe.PipeKey;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The one pipe block. It can hold any mix of the five strand types in a single block space, which is how
 * two (or more) pipes share the same side of a chest or machine.
 */
public class PipeBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<PipeBlock> CODEC = simpleCodec(PipeBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public PipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ---- Shape ----

    private static PipeKey keyAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PipeBlockEntity be ? be.key() : PipeKey.EMPTY;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PipeGeometry.shape(keyAt(level, pos));
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ---- Water ----

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(WATERLOGGED, water);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    // ---- Placement and neighbors ----

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && stack.getItem() instanceof PipeItem pipeItem
                && level.getBlockEntity(pos) instanceof PipeBlockEntity be) {
            be.addType(pipeItem.type());
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity be) {
            be.refreshConnections();
        }
    }

    // ---- Drops / pick block ----

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        return be instanceof PipeBlockEntity pipe ? pipe.contentsAsItems() : List.of();
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity be) {
            PipeGeometry.Hit part = pick(be, pos, target.getLocation());
            if (part != null) {
                return new ItemStack(Registration.pipeItem(PipeType.byIndex(part.type())));
            }
            List<PipeType> types = be.types();
            if (!types.isEmpty()) {
                return new ItemStack(Registration.pipeItem(types.get(0)));
            }
        }
        return new ItemStack(Registration.pipeItem(PipeType.ITEM));
    }

    /** The strand part at a world-space hit point, or null. */
    @Nullable
    public static PipeGeometry.Hit pick(PipeBlockEntity be, BlockPos pos, Vec3 worldHit) {
        Vec3 local = worldHit.subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16);
        return PipeGeometry.pick(be.key(), local);
    }

    // ---- Interaction ----

    public static boolean isWrench(ItemStack stack) {
        return stack.is(Registration.WRENCH.get()) || stack.is(Registration.WRENCH_TAG);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // A disguised pipe: the wrench takes the facade off, everything else leaves it alone.
        if (be.facade() != null) {
            if (isWrench(stack)) {
                if (!level.isClientSide) {
                    removeFacade(be, level, pos, player);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Sneak-click with a full block: hide the pipe inside it.
        if (player.isShiftKeyDown() && stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && !(stack.getItem() instanceof PipeItem)) {
            BlockState disguise = blockItem.getBlock().defaultBlockState();
            if (!canBeFacade(disguise, level, pos)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                be.setFacade(disguise);
                stack.consume(1, player);
                net.minecraft.world.level.block.SoundType sound = disguise.getSoundType();
                level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // Copy and paste end settings.
        if (stack.getItem() instanceof ImprintItem) {
            if (!level.isClientSide) {
                PipeGeometry.Hit part = pick(be, pos, hit.getLocation());
                ImprintItem.use(stack, be, part, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // Wireless link between two pipes.
        if (stack.getItem() instanceof SoulLinkItem) {
            if (!level.isClientSide) {
                SoulLinkItem.use(stack, be, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // Add another strand into this block.
        if (stack.getItem() instanceof PipeItem pipeItem) {
            if (be.has(pipeItem.type())) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // place a new pipe next to it instead
            }
            if (!level.isClientSide) {
                be.addType(pipeItem.type());
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 1f, 1.1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // Speed cards go into the Input end you click.
        if (stack.getItem() instanceof UpgradeItem upgrade) {
            if (!level.isClientSide) {
                applyCard(be, upgrade.tier(), stack, player, level, pos, hit);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (isWrench(stack)) {
            if (!level.isClientSide) {
                useWrench(be, (ServerLevel) level, pos, player, hit);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** The Input end under the crosshair, or null. */
    @Nullable
    private static PipeGeometry.Hit inputEndAt(PipeBlockEntity be, BlockPos pos, Vec3 hit) {
        PipeGeometry.Hit part = pick(be, pos, hit);
        return part != null && part.kind() != PipeGeometry.PartKind.CORE && part.arm() == PipeKey.ARM_INPUT ? part : null;
    }

    private static void applyCard(PipeBlockEntity be, PipeTier tier, ItemStack stack, Player player, Level level, BlockPos pos,
                                  BlockHitResult hit) {
        PipeGeometry.Hit end = inputEndAt(be, pos, hit.getLocation());
        if (end == null || end.dir() == null) {
            player.displayClientMessage(Component.translatable("message.grimpipes.upgrade.input_only").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        PipeTier current = be.endTier(end.type(), end.dir());
        if (current == tier) {
            player.displayClientMessage(Component.translatable("message.grimpipes.upgrade.already", current.displayName()), true);
            return;
        }
        ItemStack old = be.swapCard(end.type(), end.dir(), stack);
        stack.consume(1, player);
        if (!old.isEmpty() && !player.getAbilities().instabuild) {
            if (!player.getInventory().add(old)) {
                player.drop(old, false);
            }
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 0.8f + tier.ordinal() * 0.15f);
        player.displayClientMessage(Component.translatable("message.grimpipes.upgrade.done", tier.displayName()), true);
    }

    /** Empty hand on an Input end opens its card and filter screen. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity be)) {
            return InteractionResult.PASS;
        }
        PipeGeometry.Hit part = be.facade() == null ? pick(be, pos, hit.getLocation()) : null;
        boolean onEnd = part != null && part.kind() != PipeGeometry.PartKind.CORE && part.dir() != null && part.arm() != PipeKey.ARM_LINK;
        // Sneaking, a core, or a disguised pipe: the screen with every end of this block.
        if (player.isShiftKeyDown() || !onEnd) {
            if (level.isClientSide) {
                com.xtri6.grimpipes.client.ClientHooks.openOverview(pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player instanceof ServerPlayer sp) {
            openEnd(sp, be, PipeType.byIndex(part.type()), part.dir());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Opens the settings screen of one end, if it is an Input or Output end. */
    public static void openEnd(ServerPlayer player, PipeBlockEntity be, PipeType type, Direction side) {
        int arm = be.key().arm(type.ordinal(), side);
        if (arm != PipeKey.ARM_INPUT && arm != PipeKey.ARM_OUTPUT) {
            return;
        }
        Component title = Component.translatable("item.grimpipes." + type.id() + "_pipe");
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new EndMenu(id, inv, be, type, side), title),
                buf -> EndMenu.writeOpenData(buf, be, type, side));
    }

    /** Switches an end between Output and Input (the wrench click, also used by the overview screen). */
    public static void toggleEnd(PipeBlockEntity be, Player player, int t, Direction dir) {
        int arm = be.key().arm(t, dir);
        if (arm != PipeKey.ARM_INPUT && arm != PipeKey.ARM_OUTPUT) {
            return;
        }
        ConnectionMode next = be.mode(t, dir) == ConnectionMode.INPUT ? ConnectionMode.OUTPUT : ConnectionMode.INPUT;
        be.setMode(t, dir, next);
        Level level = be.getLevel();
        if (level != null) {
            click(level, be.getBlockPos(), next == ConnectionMode.INPUT ? 1.4f : 1.0f);
        }
    }

    // ---- Facades ----

    /** Full, plain blocks only: no block entities, no odd shapes, not another pipe. */
    public static boolean canBeFacade(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.isAir() && !state.hasBlockEntity() && !(state.getBlock() instanceof PipeBlock)
                && state.getRenderShape() == RenderShape.MODEL
                && Block.isShapeFullBlock(state.getShape(level, pos))
                && Block.isShapeFullBlock(state.getCollisionShape(level, pos));
    }

    private static void removeFacade(PipeBlockEntity be, Level level, BlockPos pos, Player player) {
        BlockState facade = be.facade();
        if (facade == null) {
            return;
        }
        be.setFacade(null);
        if (!player.getAbilities().instabuild) {
            ItemStack back = new ItemStack(facade.getBlock());
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
        }
        net.minecraft.world.level.block.SoundType sound = facade.getSoundType();
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
    }

    /** A disguised pipe looks, sounds and lights like its facade. */
    @Override
    public net.minecraft.world.level.block.SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos,
                                                                  @Nullable net.minecraft.world.entity.Entity entity) {
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity be && be.facade() != null) {
            return be.facade().getSoundType(level, pos, entity);
        }
        return super.getSoundType(state, level, pos, entity);
    }

    @Override
    public BlockState getAppearance(BlockState state, net.minecraft.world.level.BlockAndTintGetter level, BlockPos pos, Direction side,
                                    @Nullable BlockState queryState, @Nullable BlockPos queryPos) {
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity be && be.facade() != null) {
            return be.facade();
        }
        return state;
    }

    /**
     * Wrench controls, aimed at the exact strand and arm you click:
     * <ul>
     *     <li>Click an end: toggle Output / Input.</li>
     *     <li>Click a link between two pipes, or sneak-click an end: disconnect that side.</li>
     *     <li>Click a strand's core on a side with no arm: reconnect that side.</li>
     *     <li>Sneak-click a core: take that strand out of the block.</li>
     * </ul>
     */
    private static void useWrench(PipeBlockEntity be, ServerLevel level, BlockPos pos, Player player, BlockHitResult hit) {
        PipeGeometry.Hit part = pick(be, pos, hit.getLocation());
        if (part == null) {
            return;
        }
        PipeType type = PipeType.byIndex(part.type());
        int t = type.ordinal();
        Component typeName = Component.translatable("item.grimpipes." + type.id() + "_pipe");
        boolean sneak = player.isShiftKeyDown();

        if (part.kind() == PipeGeometry.PartKind.CORE) {
            if (sneak) {
                removeStrand(be, level, pos, player, type);
                return;
            }
            Direction face = hit.getDirection();
            if (be.mode(t, face) == ConnectionMode.DISABLED) {
                be.setMode(t, face, ConnectionMode.OUTPUT);
                BlockPos np = pos.relative(face);
                if (level.getBlockEntity(np) instanceof PipeBlockEntity other && other.has(type)
                        && other.mode(t, face.getOpposite()) == ConnectionMode.DISABLED) {
                    other.setMode(t, face.getOpposite(), ConnectionMode.OUTPUT);
                }
                click(level, pos, 1.2f);
                player.displayClientMessage(Component.translatable("message.grimpipes.wrench.reconnected", typeName,
                        sideName(face)), true);
            } else {
                player.displayClientMessage(Component.translatable("message.grimpipes.wrench.nothing", typeName,
                        sideName(face)).withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }

        Direction dir = part.dir();
        if (dir == null) {
            return;
        }
        if (part.arm() == PipeKey.ARM_LINK || sneak) {
            be.setMode(t, dir, ConnectionMode.DISABLED);
            if (part.arm() == PipeKey.ARM_LINK && level.getBlockEntity(pos.relative(dir)) instanceof PipeBlockEntity other) {
                other.setMode(t, dir.getOpposite(), ConnectionMode.DISABLED);
            }
            click(level, pos, 0.7f);
            player.displayClientMessage(Component.translatable("message.grimpipes.wrench.disconnected", typeName,
                    sideName(dir)), true);
            return;
        }

        ConnectionMode next = be.mode(t, dir) == ConnectionMode.INPUT ? ConnectionMode.OUTPUT : ConnectionMode.INPUT;
        be.setMode(t, dir, next);
        click(level, pos, next == ConnectionMode.INPUT ? 1.4f : 1.0f);
        Component target = level.getBlockState(pos.relative(dir)).getBlock().getName();
        String key = next == ConnectionMode.INPUT ? "message.grimpipes.wrench.input" : "message.grimpipes.wrench.output";
        player.displayClientMessage(Component.translatable(key, typeName, target)
                .withStyle(next == ConnectionMode.INPUT ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void removeStrand(PipeBlockEntity be, ServerLevel level, BlockPos pos, Player player, PipeType type) {
        if (be.types().size() <= 1) {
            // Last strand: break the whole block (drops the pipe and any upgrade).
            if (!player.getAbilities().instabuild) {
                for (ItemStack drop : be.contentsAsItems()) {
                    if (!player.getInventory().add(drop)) {
                        player.drop(drop, false);
                    }
                }
            }
            level.removeBlock(pos, false);
        } else {
            List<ItemStack> back = new java.util.ArrayList<>(be.removeType(type));
            back.add(new ItemStack(Registration.pipeItem(type)));
            if (!player.getAbilities().instabuild) {
                for (ItemStack drop : back) {
                    if (!player.getInventory().add(drop)) {
                        player.drop(drop, false);
                    }
                }
            }
        }
        level.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 1f, 1f);
    }

    private static void click(Level level, BlockPos pos, float pitch) {
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.35f, pitch);
    }

    private static Component sideName(Direction dir) {
        return Component.translatable("side.grimpipes." + dir.getName());
    }
}
