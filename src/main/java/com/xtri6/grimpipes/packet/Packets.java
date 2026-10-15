package com.xtri6.grimpipes.packet;

import com.xtri6.grimpipes.GrimPipes;
import com.xtri6.grimpipes.block.PipeBlock;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.item.TagFilterItem;
import com.xtri6.grimpipes.pipe.PipeType;
import com.xtri6.grimpipes.pipe.SyncedRates;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The few small packets the mod sends. Everything else rides on vanilla menus and block updates. */
public final class Packets {
    private Packets() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GrimPipes.MODID, path);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(Packets::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(OpenEnd.TYPE, OpenEnd.CODEC, Packets::handleOpenEnd);
        r.playToServer(ToggleEnd.TYPE, ToggleEnd.CODEC, Packets::handleToggleEnd);
        r.playToServer(SetTagFilter.TYPE, SetTagFilter.CODEC, Packets::handleSetTagFilter);
        r.playToClient(Rates.TYPE, Rates.CODEC, (msg, ctx) -> SyncedRates.apply(msg.values()));
    }

    // ---- Open one end's screen from the all-sides overview ----

    public record OpenEnd(BlockPos pos, int strand, int dir) implements CustomPacketPayload {
        public static final Type<OpenEnd> TYPE = new Type<>(id("open_end"));
        public static final StreamCodec<ByteBuf, OpenEnd> CODEC = StreamCodec.of(
                (buf, m) -> {
                    BlockPos.STREAM_CODEC.encode(buf, m.pos());
                    ByteBufCodecs.VAR_INT.encode(buf, m.strand());
                    ByteBufCodecs.VAR_INT.encode(buf, m.dir());
                },
                buf -> new OpenEnd(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The pipe a player may configure from a packet: loaded, close by, and actually holding that strand. */
    private static PipeBlockEntity reachablePipe(ServerPlayer player, BlockPos pos, int type) {
        if (!player.level().isLoaded(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
            return null;
        }
        if (player.level().getBlockEntity(pos) instanceof PipeBlockEntity be && be.has(PipeType.byIndex(type))) {
            return be;
        }
        return null;
    }

    private static void handleOpenEnd(OpenEnd msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && msg.strand() >= 0 && msg.strand() < PipeType.COUNT) {
            PipeBlockEntity be = reachablePipe(player, msg.pos(), msg.strand());
            if (be != null) {
                PipeBlock.openEnd(player, be, PipeType.byIndex(msg.strand()), Direction.from3DDataValue(msg.dir()));
            }
        }
    }

    // ---- Flip an end between Input and Output from the overview ----

    public record ToggleEnd(BlockPos pos, int strand, int dir) implements CustomPacketPayload {
        public static final Type<ToggleEnd> TYPE = new Type<>(id("toggle_end"));
        public static final StreamCodec<ByteBuf, ToggleEnd> CODEC = StreamCodec.of(
                (buf, m) -> {
                    BlockPos.STREAM_CODEC.encode(buf, m.pos());
                    ByteBufCodecs.VAR_INT.encode(buf, m.strand());
                    ByteBufCodecs.VAR_INT.encode(buf, m.dir());
                },
                buf -> new ToggleEnd(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void handleToggleEnd(ToggleEnd msg, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && msg.strand() >= 0 && msg.strand() < PipeType.COUNT) {
            PipeBlockEntity be = reachablePipe(player, msg.pos(), msg.strand());
            if (be != null) {
                PipeBlock.toggleEnd(be, player, msg.strand(), Direction.from3DDataValue(msg.dir()));
            }
        }
    }

    // ---- Save the rules typed into a Tag Filter ----

    public record SetTagFilter(boolean mainHand, String text) implements CustomPacketPayload {
        public static final Type<SetTagFilter> TYPE = new Type<>(id("set_tag_filter"));
        public static final StreamCodec<ByteBuf, SetTagFilter> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SetTagFilter::mainHand, ByteBufCodecs.stringUtf8(TagFilterItem.MAX_LENGTH), SetTagFilter::text,
                SetTagFilter::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void handleSetTagFilter(SetTagFilter msg, IPayloadContext ctx) {
        ItemStack held = ctx.player().getItemInHand(msg.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (held.getItem() instanceof TagFilterItem) {
            TagFilterItem.setPatterns(held, msg.text());
        }
    }

    // ---- Server speeds, so screens and tooltips show the server's numbers ----

    public record Rates(int[] values) implements CustomPacketPayload {
        public static final Type<Rates> TYPE = new Type<>(id("rates"));
        public static final StreamCodec<ByteBuf, Rates> CODEC = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64))
                .map(list -> new Rates(list.stream().mapToInt(Integer::intValue).toArray()),
                        r -> java.util.Arrays.stream(r.values()).boxed().toList());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static Rates currentRates() {
        return new Rates(SyncedRates.fromConfig());
    }

    public static void sendRates(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, currentRates());
    }

    public static void sendRatesToAll() {
        PacketDistributor.sendToAllPlayers(currentRates());
    }
}
