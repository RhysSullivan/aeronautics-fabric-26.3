package com.tterrag.registrate.providers.loot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.tterrag.registrate.AbstractRegistrate;

import lombok.Getter;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.NotNull;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTable.Builder;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * PORT-26.3: no longer a Fabric loot sub-provider (loot data generation is not ported; the generated tables are
 * committed). It still collects the tables entity builders add.
 */
public class RegistrateEntityLootTables implements RegistrateLootTables {

    private final AbstractRegistrate<?> parent;
    @Getter
    private final HolderLookup.Provider provider;
    private final Consumer<RegistrateEntityLootTables> callback;

    private final Map<ResourceKey<LootTable>, Builder> entries = new HashMap<>();

    public RegistrateEntityLootTables(HolderLookup.Provider provider, AbstractRegistrate<?> parent, Consumer<RegistrateEntityLootTables> callback, FabricPackOutput output) {
        this.parent = parent;
        this.provider = provider;
        this.callback = callback;
    }

    @Override
    public void run() {
        callback.accept(this);
    }

    public void generate(@NotNull BiConsumer<ResourceKey<LootTable>, Builder> consumer) {
        callback.accept(this);
        entries.forEach(consumer);
    }

    public void add(EntityType<?> type, LootTable.Builder table) {
        type.getDefaultLootTable().ifPresent(id -> entries.put(id, table));
    }

    public void add(ResourceKey<LootTable> id, LootTable.Builder table) {
        entries.put(id, table);
    }

}
