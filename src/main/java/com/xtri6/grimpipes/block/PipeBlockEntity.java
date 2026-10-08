package com.xtri6.grimpipes.block;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.xtri6.grimpipes.Registration;
import com.xtri6.grimpipes.compat.Chemicals;
import com.xtri6.grimpipes.network.Endpoint;
import com.xtri6.grimpipes.network.InboundEnergy;
import com.xtri6.grimpipes.network.InboundFluids;
import com.xtri6.grimpipes.network.InboundItems;
import com.xtri6.grimpipes.network.NetworkManager;
import com.xtri6.grimpipes.pipe.ConnectionMode;
import com.xtri6.grimpipes.pipe.PipeKey;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;
import com.xtri6.grimpipes.pipe.ResourceKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Data holder for one pipe block. It never ticks: all transport is done by {@link NetworkManager}.
 * It stores which strands are present, each strand's mode per side, and each end's card and filter. From that and its
 * neighbors it works out a {@link PipeKey}, which drives rendering, hitboxes and network building.
 */
public class PipeBlockEntity extends BlockEntity {
    public static final ModelProperty<PipeKey> MODEL_KEY = new ModelProperty<>();
    private static final int SLOTS = PipeType.COUNT * 6;

    private int typeMask;
    private final byte[] modes = new byte[SLOTS];
    private final EndConfig[] ends = new EndConfig[SLOTS];
    private PipeKey key = PipeKey.EMPTY;
    private boolean powered;

    // ---- Server-only runtime state (never saved) ----
    private final Endpoint[] endpoints = new Endpoint[SLOTS];
    private final Object[] inbound = new Object[SLOTS * ResourceKind.values().length];

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.PIPE_ENTITY.get(), pos, state);
    }

    private static int slot(int type, Direction dir) {
        return type * 6 + dir.get3DDataValue();
    }

    // ---- Reads ----

    public int typeMask() {
        return typeMask;
    }

    public boolean has(PipeType type) {
        return type.isIn(typeMask);
    }

    public ConnectionMode mode(int type, Direction dir) {
        return ConnectionMode.byIndex(modes[slot(type, dir)]);
    }

    /** Speed-card tier of one end; Hollow when it has no card. */
    public PipeTier endTier(int type, Direction dir) {
        EndConfig e = ends[slot(type, dir)];
        return e == null ? PipeTier.HOLLOW : e.tier();
    }

    /** Card and filter of one end, or null if it was never configured (= no card, pull everything). */
    @Nullable
    public EndConfig endConfigOrNull(int type, Direction dir) {
        return ends[slot(type, dir)];
    }

    /** Card and filter of one end, created on first use. */
    public EndConfig endConfig(int type, Direction dir) {
        int i = slot(type, dir);
        EndConfig e = ends[i];
        if (e == null) {
            e = new EndConfig(() -> {
                onCardChanged();
                wakeEnd(type, dir);
            }, () -> {
                setChanged();
                wakeEnd(type, dir);
            }, () -> {
                if (level instanceof ServerLevel server) {
                    NetworkManager.get(server).invalidate(worldPosition); // re-sort outputs by priority
                }
            });
            ends[i] = e;
        }
        return e;
    }

    private void wakeEnd(int type, Direction dir) {
        Endpoint e = endpoint(type, dir);
        if (e != null) {
            e.wake();
        }
    }

    private void onCardChanged() {
        setChanged();
        if (level instanceof ServerLevel) {
            recomputeKey(true); // the end plate shows the card's tier color
        }
    }

    public PipeKey key() {
        return key;
    }

    /** Whether a redstone signal reaches this pipe block (cached; updated on neighbor changes). */
    public boolean isPowered() {
        return powered;
    }

    public List<PipeType> types() {
        List<PipeType> list = new ArrayList<>();
        for (PipeType t : PipeType.VALUES) {
            if (has(t)) {
                list.add(t);
            }
        }
        return list;
    }

    // ---- Edits (server side; each one updates the world, clients and networks) ----

    public boolean addType(PipeType type) {
        if (has(type)) {
            return false;
        }
        typeMask |= type.bit();
        for (Direction d : Direction.values()) {
            modes[slot(type.ordinal(), d)] = (byte) ConnectionMode.OUTPUT.ordinal();
        }
        onEdited();
        return true;
    }

    /** Removes a strand. Returns the cards that were in its ends so they can be given back. */
    public List<ItemStack> removeType(PipeType type) {
        List<ItemStack> cards = new ArrayList<>();
        if (!has(type)) {
            return cards;
        }
        for (Direction d : Direction.values()) {
            int i = slot(type.ordinal(), d);
            if (ends[i] != null) {
                ItemStack card = ends[i].card.getStackInSlot(0);
                if (!card.isEmpty()) {
                    cards.add(card.copy());
                }
                ends[i] = null;
            }
        }
        typeMask &= ~type.bit();
        onEdited();
        return cards;
    }

    public void setMode(int type, Direction dir, ConnectionMode mode) {
        if (mode(type, dir) == mode) {
            return;
        }
        modes[slot(type, dir)] = (byte) mode.ordinal();
        onEdited();
    }

    /** Puts a speed card straight into an end (used by right-clicking a pipe end with a card). Returns the old card. */
    public ItemStack swapCard(int type, Direction dir, ItemStack newCard) {
        EndConfig e = endConfig(type, dir);
        ItemStack old = e.card.getStackInSlot(0).copy();
        e.card.setStackInSlot(0, newCard.copyWithCount(1));
        return old;
    }

    /** Copies everything needed to rebuild this pipe as dropped items. */
    public List<ItemStack> contentsAsItems() {
        List<ItemStack> drops = new ArrayList<>();
        for (PipeType t : types()) {
            drops.add(new ItemStack(Registration.pipeItem(t)));
        }
        for (int t = 0; t < PipeType.COUNT; t++) {
            if ((typeMask & (1 << t)) == 0) {
                continue;
            }
            for (Direction d : Direction.values()) {
                EndConfig e = ends[slot(t, d)];
                if (e != null && !e.card.getStackInSlot(0).isEmpty()) {
                    drops.add(e.card.getStackInSlot(0).copy());
                }
            }
        }
        return drops;
    }

    private void onEdited() {
        setChanged();
        if (level instanceof ServerLevel server) {
            recomputeKey(true);
            // Neighboring pipes draw their arms from our strands and modes, so let them re-check.
            for (Direction d : Direction.values()) {
                BlockPos np = worldPosition.relative(d);
                if (server.isLoaded(np) && server.getBlockEntity(np) instanceof PipeBlockEntity other) {
                    other.refreshConnections();
                }
            }
            server.invalidateCapabilities(worldPosition);
            NetworkManager.get(server).invalidate(worldPosition);
        }
    }

    // ---- Connection detection ----

    /** Re-checks all six neighbors. Cheap, and only called when something nearby actually changed. */
    public void refreshConnections() {
        if (level instanceof ServerLevel) {
            recomputeKey(false);
        }
    }

    private void recomputeKey(boolean forceSync) {
        ServerLevel server = (ServerLevel) level;
        boolean nowPowered = server.hasNeighborSignal(worldPosition);
        if (nowPowered != powered) {
            powered = nowPowered;
            for (Endpoint e : endpoints) {
                if (e != null) {
                    e.wake();
                }
            }
        }
        PipeKey.Builder b = new PipeKey.Builder().types(typeMask);
        for (Direction d : Direction.values()) {
            BlockPos np = worldPosition.relative(d);
            boolean loaded = server.isLoaded(np);
            BlockEntity nbe = loaded ? server.getBlockEntity(np) : null;
            PipeBlockEntity neighborPipe = nbe instanceof PipeBlockEntity p && !p.isRemoved() ? p : null;
            b.shared(d, neighborPipe != null ? (typeMask & neighborPipe.typeMask) : typeMask);

            for (PipeType t : PipeType.VALUES) {
                if (!has(t)) {
                    continue;
                }
                int ti = t.ordinal();
                ConnectionMode mode = mode(ti, d);
                int arm = PipeKey.ARM_NONE;
                if (mode != ConnectionMode.DISABLED) {
                    if (!loaded) {
                        arm = key.arm(ti, d); // keep what we had until that chunk is back
                    } else if (neighborPipe != null) {
                        if (neighborPipe.has(t) && neighborPipe.mode(ti, d.getOpposite()) != ConnectionMode.DISABLED) {
                            arm = PipeKey.ARM_LINK;
                        }
                    } else if (nbe != null || !server.getBlockState(np).isAir()) {
                        if (hasAnyCapability(server, t, np, d.getOpposite())) {
                            arm = mode == ConnectionMode.INPUT ? PipeKey.ARM_INPUT : PipeKey.ARM_OUTPUT;
                        }
                    }
                }
                b.arm(ti, d, arm);
                if (arm == PipeKey.ARM_INPUT) {
                    b.endTier(ti, d, endTier(ti, d).ordinal());
                }
            }
        }
        PipeKey next = b.build();
        boolean changed = !next.equals(key);
        if (changed) {
            key = next;
            setChanged();
            server.invalidateCapabilities(worldPosition);
            NetworkManager.get(server).invalidate(worldPosition);
        }
        if (changed || forceSync) {
            BlockState state = getBlockState();
            server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private static boolean hasAnyCapability(ServerLevel level, PipeType type, BlockPos pos, Direction side) {
        for (ResourceKind kind : type.kinds()) {
            boolean found = switch (kind) {
                case ITEM -> level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) != null;
                case FLUID -> level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side) != null;
                case ENERGY -> level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) != null;
                case CHEMICAL -> Chemicals.bridge() != null && Chemicals.bridge().hasHandler(level, pos, side);
            };
            if (found) {
                return true;
            }
        }
        return false;
    }

    // ---- Network hooks ----

    public void setEndpoint(int type, Direction dir, @Nullable Endpoint endpoint) {
        endpoints[slot(type, dir)] = endpoint;
    }

    @Nullable
    public Endpoint endpoint(int type, Direction dir) {
        Endpoint e = endpoints[slot(type, dir)];
        return e == null || e.network().isDestroyed() ? null : e;
    }

    // ---- Capabilities: pipes only accept pushes on sides set to Input ----

    private int inputStrand(@Nullable Direction side, ResourceKind kind) {
        if (side == null) {
            return -1;
        }
        for (PipeType t : PipeType.VALUES) {
            if (has(t) && t.carries(kind) && key.arm(t.ordinal(), side) == PipeKey.ARM_INPUT) {
                return t.ordinal();
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private <T> T inboundCached(int type, Direction side, ResourceKind kind, java.util.function.Supplier<T> factory) {
        int i = slot(type, side) * ResourceKind.values().length + kind.ordinal();
        Object o = inbound[i];
        if (o == null) {
            o = factory.get();
            inbound[i] = o;
        }
        return (T) o;
    }

    @Nullable
    public IItemHandler inboundItems(@Nullable Direction side) {
        int t = inputStrand(side, ResourceKind.ITEM);
        return t < 0 || level == null || level.isClientSide ? null
                : inboundCached(t, side, ResourceKind.ITEM, () -> new InboundItems(this, t, side));
    }

    @Nullable
    public IFluidHandler inboundFluids(@Nullable Direction side) {
        int t = inputStrand(side, ResourceKind.FLUID);
        return t < 0 || level == null || level.isClientSide ? null
                : inboundCached(t, side, ResourceKind.FLUID, () -> new InboundFluids(this, t, side));
    }

    @Nullable
    public IEnergyStorage inboundEnergy(@Nullable Direction side) {
        int t = inputStrand(side, ResourceKind.ENERGY);
        return t < 0 || level == null || level.isClientSide ? null
                : inboundCached(t, side, ResourceKind.ENERGY, () -> new InboundEnergy(this, t, side));
    }

    @Nullable
    public Object inboundChemicals(@Nullable Direction side) {
        int t = inputStrand(side, ResourceKind.CHEMICAL);
        if (t < 0 || level == null || level.isClientSide || Chemicals.bridge() == null) {
            return null;
        }
        return inboundCached(t, side, ResourceKind.CHEMICAL, () -> Chemicals.bridge().createInbound(this, t, side));
    }

    // ---- Lifecycle ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            NetworkManager.get(server).queueRefresh(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            NetworkManager.get(server).invalidate(worldPosition);
        }
    }

    // ---- Save / sync ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Types", typeMask);
        tag.putByteArray("Modes", modes.clone());
        CompoundTag endTag = new CompoundTag();
        for (int i = 0; i < SLOTS; i++) {
            if (ends[i] != null && !ends[i].isDefault()) {
                endTag.put(String.valueOf(i), ends[i].save(registries));
            }
        }
        if (!endTag.isEmpty()) {
            tag.put("Ends", endTag);
        }
        tag.putLong("KeyA", key.a());
        tag.putLong("KeyB", key.b());
        tag.putLong("KeyC", key.c());
        tag.putLong("KeyD", key.d());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        typeMask = tag.getInt("Types") & 31;
        byte[] saved = tag.getByteArray("Modes");
        System.arraycopy(saved, 0, modes, 0, Math.min(saved.length, modes.length));
        CompoundTag endTag = tag.getCompound("Ends");
        for (int i = 0; i < SLOTS && !tag.getBoolean("SyncOnly"); i++) {
            String k = String.valueOf(i);
            if (endTag.contains(k)) {
                int type = i / 6;
                Direction dir = Direction.from3DDataValue(i % 6);
                endConfig(type, dir).load(endTag.getCompound(k), registries);
            } else if (ends[i] != null) {
                ends[i] = null;
            }
        }
        PipeKey loaded = new PipeKey(tag.getLong("KeyA"), tag.getLong("KeyB"), tag.getLong("KeyC"), tag.getLong("KeyD"));
        boolean changed = !loaded.equals(key);
        key = loaded;
        if (changed && level != null && level.isClientSide) {
            requestModelDataUpdate();
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_IMMEDIATE);
        }
    }

    /** Clients only need the strands and the render key, not card or filter contents. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Types", typeMask);
        tag.putByteArray("Modes", modes.clone());
        tag.putLong("KeyA", key.a());
        tag.putLong("KeyB", key.b());
        tag.putLong("KeyC", key.c());
        tag.putLong("KeyD", key.d());
        tag.putBoolean("SyncOnly", true);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.of(MODEL_KEY, key);
    }

    @Nullable
    public Level levelOrNull() {
        return level;
    }
}
