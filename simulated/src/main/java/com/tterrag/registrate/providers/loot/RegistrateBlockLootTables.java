package com.tterrag.registrate.providers.loot;

import java.util.Set;
import java.util.function.Consumer;

import com.tterrag.registrate.AbstractRegistrate;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.world.flag.FeatureFlags;

/**
 * PORT-26.3: 26.3's {@link BlockLootSubProvider} takes a bootstrap context and its helpers are public, so the
 * generated pass-through overrides are gone. Loot data generation is not ported (the generated tables are
 * committed), so this is never constructed at runtime; the builders' loot callbacks still compile against it.
 */
public class RegistrateBlockLootTables extends BlockLootSubProvider implements RegistrateLootTables {
    private final AbstractRegistrate<?> parent;
    private final Consumer<RegistrateBlockLootTables> callback;

    public RegistrateBlockLootTables(HolderLookup.Provider provider, AbstractRegistrate<?> parent, Consumer<RegistrateBlockLootTables> callback, FabricPackOutput output) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), notPorted());
        this.parent = parent;
        this.callback = callback;
    }

    private static LootTableSubProvider.Context notPorted() {
        throw new UnsupportedOperationException("Registrate loot data generation is not ported to Minecraft 26.3 (the generated loot tables are committed)");
    }

    @Override
    public void generate() {
        callback.accept(this);
    }
}
