package com.xtri6.grimpipes.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.xtri6.grimpipes.Registration;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A filter entry that matches by rule instead of by one exact item. Put it in any filter slot.
 * <pre>
 * #c:ingots          anything in that tag (items or fluids)
 * #c:ores/*          any tag that starts with c:ores/
 * &#64;mekanism          anything from that mod
 * minecraft:*_log    item or fluid ids, * matches anything
 * </pre>
 * Rules are separated by commas or spaces; a thing matches if any rule matches.
 */
public class TagFilterItem extends Item {
    public static final int MAX_LENGTH = 256;
    private static final Map<String, Rule[]> CACHE = new ConcurrentHashMap<>();

    public TagFilterItem(Properties properties) {
        super(properties);
    }

    public static String patterns(ItemStack stack) {
        String s = stack.get(Registration.FILTER_PATTERNS.get());
        return s == null ? "" : s;
    }

    public static void setPatterns(ItemStack stack, String text) {
        String clean = text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
        clean = clean.strip();
        if (clean.isEmpty()) {
            stack.remove(Registration.FILTER_PATTERNS.get());
        } else {
            stack.set(Registration.FILTER_PATTERNS.get(), clean);
        }
    }

    public static boolean matches(ItemStack filter, ItemStack stack) {
        Holder<Item> holder = stack.getItemHolder();
        for (Rule rule : rules(patterns(filter))) {
            if (rule.matchesItem(holder, stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(ItemStack filter, FluidStack fluid) {
        Holder<Fluid> holder = fluid.getFluidHolder();
        for (Rule rule : rules(patterns(filter))) {
            if (rule.matchesFluid(holder, fluid)) {
                return true;
            }
        }
        return false;
    }

    static Rule[] rules(String text) {
        if (text.isEmpty()) {
            return new Rule[0];
        }
        if (CACHE.size() > 512) {
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(text, TagFilterItem::parse);
    }

    private static Rule[] parse(String text) {
        List<Rule> out = new ArrayList<>();
        for (String raw : text.split("[,\\s]+")) {
            String token = raw.strip().toLowerCase(java.util.Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("#")) {
                String body = token.substring(1);
                if (body.contains("*")) {
                    out.add(new TagGlobRule(glob(withNamespace(body))));
                } else {
                    ResourceLocation id = ResourceLocation.tryParse(withNamespace(body));
                    if (id != null) {
                        out.add(new TagRule(TagKey.create(net.minecraft.core.registries.Registries.ITEM, id),
                                TagKey.create(net.minecraft.core.registries.Registries.FLUID, id)));
                    }
                }
            } else if (token.startsWith("@")) {
                String mod = token.substring(1);
                out.add(new IdRule(id -> id.getNamespace().equals(mod)));
            } else {
                Pattern p = glob(withNamespace(token));
                out.add(new IdRule(id -> p.matcher(id.toString()).matches()));
            }
        }
        return out.toArray(new Rule[0]);
    }

    private static String withNamespace(String s) {
        return s.contains(":") ? s : "minecraft:" + s;
    }

    private static Pattern glob(String s) {
        StringBuilder b = new StringBuilder();
        for (String part : s.split("\\*", -1)) {
            if (b.length() > 0) {
                b.append(".*");
            }
            b.append(Pattern.quote(part));
        }
        return Pattern.compile(b.toString());
    }

    sealed interface Rule permits TagRule, TagGlobRule, IdRule {
        boolean matchesItem(Holder<Item> item, ItemStack stack);

        boolean matchesFluid(Holder<Fluid> fluid, FluidStack stack);
    }

    record TagRule(TagKey<Item> items, TagKey<Fluid> fluids) implements Rule {
        @Override
        public boolean matchesItem(Holder<Item> item, ItemStack stack) {
            return stack.is(items);
        }

        @Override
        public boolean matchesFluid(Holder<Fluid> fluid, FluidStack stack) {
            return stack.is(fluids);
        }
    }

    record TagGlobRule(Pattern pattern) implements Rule {
        @Override
        public boolean matchesItem(Holder<Item> item, ItemStack stack) {
            return item.tags().anyMatch(t -> pattern.matcher(t.location().toString()).matches());
        }

        @Override
        public boolean matchesFluid(Holder<Fluid> fluid, FluidStack stack) {
            return fluid.tags().anyMatch(t -> pattern.matcher(t.location().toString()).matches());
        }
    }

    record IdRule(Predicate<ResourceLocation> test) implements Rule {
        @Override
        public boolean matchesItem(Holder<Item> item, ItemStack stack) {
            return test.test(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }

        @Override
        public boolean matchesFluid(Holder<Fluid> fluid, FluidStack stack) {
            return test.test(BuiltInRegistries.FLUID.getKey(stack.getFluid()));
        }
    }

    // ---- Use ----

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            com.xtri6.grimpipes.client.ClientHooks.openTagFilter(hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !patterns(stack).isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String p = patterns(stack);
        if (p.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.grimpipes.tag_filter.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.grimpipes.tag_filter.matches").withStyle(ChatFormatting.GRAY));
            for (String rule : p.split("[,\\s]+")) {
                if (!rule.isBlank()) {
                    tooltip.add(Component.literal("  " + rule).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
        }
        tooltip.add(Component.translatable("tooltip.grimpipes.tag_filter.use").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
