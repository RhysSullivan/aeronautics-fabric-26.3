package com.tterrag.registrate.providers.loot;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Function4;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateProvider;
import com.tterrag.registrate.util.nullness.NonNullConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * PORT-26.3: keeps the loot actions builders register; data generation itself is not ported (the generated loot tables
 * are committed), so {@link #run} fails with a clear message instead of extending 26.3's LootTableProvider.
 */
public class RegistrateLootTableProvider implements RegistrateProvider {

    public interface LootType<T extends RegistrateLootTables> {

        static LootType<RegistrateBlockLootTables> BLOCK = register("block", LootContextParamSets.BLOCK, RegistrateBlockLootTables::new);
        static LootType<RegistrateEntityLootTables> ENTITY = register("entity", LootContextParamSets.ENTITY, RegistrateEntityLootTables::new);

        T getLootCreator(HolderLookup.Provider provider, AbstractRegistrate<?> parent, Consumer<T> callback, FabricPackOutput output);
        ContextKeySet getLootSet();

        static <T extends RegistrateLootTables> LootType<T> register(String name, ContextKeySet set, Function4<HolderLookup.Provider, AbstractRegistrate<?>, Consumer<T>, FabricPackOutput, T> factory) {
            LootType<T> type = new LootType<T>() {
                @Override
                public T getLootCreator(HolderLookup.Provider provider, AbstractRegistrate<?> parent, Consumer<T> callback, FabricPackOutput output) {
                    return factory.apply(provider, parent, callback, output);
                }

                @Override
                public ContextKeySet getLootSet() {
                    return set;
                }
            };
            LOOT_TYPES.put(name, type);
            return type;
        }
    }

    private static final Map<String, LootType<?>> LOOT_TYPES = new HashMap<>();

    private final AbstractRegistrate<?> parent;

    private final Multimap<LootType<?>, Consumer<? super RegistrateLootTables>> specialLootActions = HashMultimap.create();
    private final Multimap<ContextKeySet, Consumer<BiConsumer<ResourceKey<LootTable>, LootTable.Builder>>> lootActions = HashMultimap.create();
    private final FabricPackOutput output;

    private CompletableFuture<HolderLookup.Provider> provider;

    public RegistrateLootTableProvider(AbstractRegistrate<?> parent, FabricPackOutput output,
                                       CompletableFuture<HolderLookup.Provider> provider) {
        this.parent = parent;
        this.output = output;
        this.provider = provider;
    }

    public HolderLookup.Provider getProvider(){
        return provider.getNow(null);
    }

    public <T> Holder<T> resolve(ResourceKey<T> key) {
        return getProvider().lookupOrThrow(key.registryKey()).getOrThrow(key);
    }

    @Override
    public EnvType getSide() {
        return EnvType.SERVER;
    }

    @SuppressWarnings("unchecked")
    public <T extends RegistrateLootTables> void addLootAction(LootType<T> type, NonNullConsumer<? extends RegistrateLootTables> action) {
        this.specialLootActions.put(type, (Consumer<RegistrateLootTables>) action);
    }

    public void addLootAction(ContextKeySet set, Consumer<BiConsumer<ResourceKey<LootTable>, LootTable.Builder>> action) {
        this.lootActions.put(set, action);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Registrate loot data generation is not ported to Minecraft 26.3 (the generated loot tables are committed)"));
    }

    @Override
    public String getName() {
        return "Registrate loot tables for " + this.parent.getModid();
    }
}
