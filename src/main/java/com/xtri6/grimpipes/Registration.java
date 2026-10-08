package com.xtri6.grimpipes;

import java.util.EnumMap;
import java.util.Map;

import com.xtri6.grimpipes.block.PipeBlock;
import com.xtri6.grimpipes.block.PipeBlockEntity;
import com.xtri6.grimpipes.item.PipeItem;
import com.xtri6.grimpipes.item.UpgradeItem;
import com.xtri6.grimpipes.item.WrenchItem;
import com.xtri6.grimpipes.menu.EndMenu;
import com.xtri6.grimpipes.pipe.PipeTier;
import com.xtri6.grimpipes.pipe.PipeType;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every block, item and block entity the mod adds. */
public final class Registration {
    private static final String MODID = GrimPipes.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Tag other mods use for their wrenches; any wrench in it can configure Reaper pipes. */
    public static final TagKey<Item> WRENCH_TAG = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));

    // ---- The one pipe block (it holds any mix of strands) ----
    public static final DeferredBlock<PipeBlock> PIPE = BLOCKS.register("reaper_pipe",
            () -> new PipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(0.6f, 6.0f)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .dynamicShape()
                    .pushReaction(PushReaction.BLOCK)
                    .isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PipeBlockEntity>> PIPE_ENTITY = BLOCK_ENTITIES.register("reaper_pipe",
            () -> BlockEntityType.Builder.of(PipeBlockEntity::new, PIPE.get()).build(null));

    // ---- Items ----
    public static final Map<PipeType, DeferredItem<PipeItem>> PIPE_ITEMS = new EnumMap<>(PipeType.class);
    public static final Map<PipeTier, DeferredItem<UpgradeItem>> UPGRADES = new EnumMap<>(PipeTier.class);

    static {
        for (PipeType type : PipeType.VALUES) {
            PIPE_ITEMS.put(type, ITEMS.register(type.id() + "_pipe", () -> new PipeItem(PIPE.get(), type, new Item.Properties())));
        }
        for (PipeTier tier : PipeTier.VALUES) {
            if (tier != PipeTier.HOLLOW) {
                UPGRADES.put(tier, ITEMS.register(tier.id() + "_upgrade", () -> new UpgradeItem(tier, new Item.Properties().stacksTo(16))));
            }
        }
    }

    public static final DeferredHolder<MenuType<?>, MenuType<EndMenu>> END_MENU = MENUS.register("pipe_end",
            () -> IMenuTypeExtension.create(EndMenu::new));

    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.register("reaper_wrench", () -> new WrenchItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.grimpipes"))
            .icon(() -> PIPE_ITEMS.get(PipeType.OMNI).get().getDefaultInstance())
            .displayItems((params, output) -> {
                for (PipeType type : PipeType.VALUES) {
                    output.accept(PIPE_ITEMS.get(type).get());
                }
                output.accept(WRENCH.get());
                for (PipeTier tier : PipeTier.VALUES) {
                    if (tier != PipeTier.HOLLOW) {
                        output.accept(UPGRADES.get(tier).get());
                    }
                }
            })
            .build());

    public static PipeItem pipeItem(PipeType type) {
        return PIPE_ITEMS.get(type).get();
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
    }

    private Registration() {
    }
}
