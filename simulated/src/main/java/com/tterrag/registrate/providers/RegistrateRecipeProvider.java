package com.tterrag.registrate.providers;

import com.tterrag.registrate.AbstractRegistrate;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * PORT-26.3: Registrate's recipe provider, reduced to what registration needs.
 * <p>
 * Minecraft 26.3 moved recipe data generation onto registry bootstrap contexts, and this port does not run data
 * generation (the generated recipes are committed under {@code src/generated}). Builders still take recipe callbacks
 * of this type, so the class keeps the helpers those callbacks use ({@link #shaped}, {@link #shapeless}, {@link #has}),
 * the {@link RecipeOutput} they save to, and the static criteria helpers; anything that needs the data-generation run
 * fails with a clear message. The 1.21 implementation is in the upstream history.
 */
public class RegistrateRecipeProvider implements RegistrateProvider, RecipeOutput {
    private static final String NOT_PORTED = "Registrate recipe data generation is not ported to Minecraft 26.3 (the generated recipes are committed)";

    private final AbstractRegistrate<?> owner;

    public RegistrateRecipeProvider(final AbstractRegistrate<?> owner, final FabricPackOutput output, final CompletableFuture<HolderLookup.Provider> registries) {
        this.owner = owner;
    }

    // --- criteria helpers (pure, usable at registration) ---

    public static Criterion<InventoryChangeTrigger.TriggerInstance> has(final ItemLike item) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(item);
    }

    public static Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(final ItemPredicate.Builder... items) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(items);
    }

    public static Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(final ItemPredicate... predicates) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(predicates);
    }

    public static String getHasName(final ItemLike item) {
        return "has_" + BuiltInRegistries.ITEM.getKey(item.asItem()).getPath();
    }

    // --- helpers that need the data-generation run's registries ---

    public Criterion<InventoryChangeTrigger.TriggerInstance> has(final TagKey<Item> tag) {
        throw new UnsupportedOperationException(NOT_PORTED);
    }

    public Ingredient tag(final TagKey<Item> tag) {
        throw new UnsupportedOperationException(NOT_PORTED);
    }

    public HolderGetter<Item> itemLookup() {
        throw new UnsupportedOperationException(NOT_PORTED);
    }

    public ShapedRecipeBuilder shaped(final RecipeCategory category, final ItemLike result) {
        return ShapedRecipeBuilder.shaped(this.itemLookup(), category, result);
    }

    public ShapedRecipeBuilder shaped(final RecipeCategory category, final ItemLike result, final int count) {
        return ShapedRecipeBuilder.shaped(this.itemLookup(), category, result, count);
    }

    public ShapelessRecipeBuilder shapeless(final RecipeCategory category, final ItemLike result) {
        return ShapelessRecipeBuilder.shapeless(this.itemLookup(), category, result);
    }

    public ShapelessRecipeBuilder shapeless(final RecipeCategory category, final ItemLike result, final int count) {
        return ShapelessRecipeBuilder.shapeless(this.itemLookup(), category, result, count);
    }

    // --- RecipeOutput (only reachable during data generation) ---

    @Override
    public void accept(final ResourceKey<Recipe<?>> id, final Recipe<?> recipe, final @Nullable AdvancementHolder advancement) {
        throw new UnsupportedOperationException(NOT_PORTED);
    }

    @Override
    public Advancement.Builder advancement() {
        throw new UnsupportedOperationException(NOT_PORTED);
    }

    @Override
    public <S> HolderGetter<S> lookup(final ResourceKey<? extends Registry<? extends S>> key) {
        throw new UnsupportedOperationException(NOT_PORTED);
    }

    @Override
    public <S> Stream<Holder.Reference<S>> listContextElements(final ResourceKey<? extends Registry<? extends S>> key) {
        throw new UnsupportedOperationException(NOT_PORTED);
    }

    // --- DataProvider ---

    @Override
    public CompletableFuture<?> run(final CachedOutput cache) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(NOT_PORTED));
    }

    @Override
    public String getName() {
        return "Registrate recipes for " + this.owner.getModid();
    }

    @Override
    public EnvType getSide() {
        return EnvType.SERVER;
    }
}
