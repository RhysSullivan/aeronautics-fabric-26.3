package com.tterrag.registrate.providers;

import com.tterrag.registrate.AbstractRegistrate;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import io.github.fabricators_of_create.porting_lib.data.ExistingFileHelper;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class RegistrateItemTagsProvider extends RegistrateTagsProvider.IntrinsicImpl<Item> {

    private final CompletableFuture<TagsProvider.TagLookup<Block>> blockTags;
    private final Map<TagKey<Block>, TagKey<Item>> tagsToCopy = new HashMap<>();

    public RegistrateItemTagsProvider(AbstractRegistrate<?> owner, ProviderType<RegistrateItemTagsProvider> type, String name, FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesLookup, CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, ExistingFileHelper existingFileHelper) {
        super(owner, type, name, output, Registries.ITEM, registriesLookup, item -> item.builtInRegistryHolder().key());
        this.blockTags = blockTags;
    }

    public void copy(TagKey<Block> p_206422_, TagKey<Item> p_206423_) {
        this.tagsToCopy.put(p_206422_, p_206423_);
    }

    // PORT-26.3: the block-to-item tag copy ran inside TagsProvider.createContentsProvider, which 26.3 removed; data
    // generation is not ported (generated tags are committed), so copy() only records the pairs.
}
