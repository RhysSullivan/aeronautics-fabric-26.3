package io.github.fabricators_of_create.porting_lib.util;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * PORT-26.3: {@link Holder} is sealed in 26.3, so this no longer implements it; {@link #getDelegate()} returns the
 * registry's own holder for callers that need one. The Holder-shaped methods are kept.
 */
public class DeferredHolder<R, T extends R> implements Supplier<T> {
    public static <R, T extends R> DeferredHolder<R, T> create(
            final ResourceKey<? extends Registry<R>> registryKey, final Identifier valueName) {
        return create(ResourceKey.create(registryKey, valueName));
    }

    public static <R, T extends R> DeferredHolder<R, T> create(
            final Identifier registryName, final Identifier valueName) {
        return create(ResourceKey.createRegistryKey(registryName), valueName);
    }

    public static <R, T extends R> DeferredHolder<R, T> create(final ResourceKey<R> key) {
        return new DeferredHolder<>(key);
    }

    protected final ResourceKey<R> key;
    @Nullable
    private Holder<R> holder;

    protected DeferredHolder(final ResourceKey<R> key) {
        this.key = Objects.requireNonNull(key);
        bind(false);
    }

    @SuppressWarnings("unchecked")
    public T value() {
        bind(true);
        if (holder == null) {
            throw new NullPointerException("Trying to access unbound value: " + key);
        }
        return (T) holder.value();
    }

    @Override
    public T get() {
        return value();
    }

    public Holder<R> getDelegate() {
        bind(true);
        if (holder == null) {
            throw new NullPointerException("Trying to access unbound holder: " + key);
        }
        return holder;
    }

    public Optional<T> asOptional() {
        return isBound() ? Optional.of(value()) : Optional.empty();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected Registry<R> getRegistry() {
        return (Registry<R>) BuiltInRegistries.REGISTRY.getValue(key.registry());
    }

    protected final void bind(final boolean throwOnMissingRegistry) {
        if (holder != null) {
            return;
        }
        final Registry<R> registry = getRegistry();
        if (registry != null) {
            holder = registry.get(key).orElse(null);
        } else if (throwOnMissingRegistry) {
            throw new IllegalStateException("Registry not present for " + this + ": " + key.registry());
        }
    }

    public Identifier getId() {
        return key.identifier();
    }

    public ResourceKey<R> getKey() {
        return key;
    }

    public boolean isBound() {
        bind(false);
        return holder != null && holder.isBound();
    }

    public boolean is(final Identifier id) {
        return id.equals(key.identifier());
    }

    public boolean is(final ResourceKey<R> otherKey) {
        return key.equals(otherKey);
    }

    public boolean is(final Predicate<ResourceKey<R>> predicate) {
        return predicate.test(key);
    }

    public boolean is(final TagKey<R> tag) {
        bind(false);
        return holder != null && holder.is(tag);
    }

    public boolean is(final Holder<R> other) {
        bind(false);
        return holder != null && holder.is(other);
    }

    public Stream<TagKey<R>> tags() {
        bind(false);
        return holder == null ? Stream.empty() : holder.tags();
    }

    public Either<ResourceKey<R>, R> unwrap() {
        return Either.left(key);
    }

    public Optional<ResourceKey<R>> unwrapKey() {
        return Optional.of(key);
    }

    public Holder.Kind kind() {
        return Holder.Kind.REFERENCE;
    }

    public boolean canSerializeIn(final HolderOwner<R> owner) {
        bind(false);
        return holder != null && holder.canSerializeIn(owner);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        return object instanceof Holder<?> other
                && other.kind() == Holder.Kind.REFERENCE
                && other.unwrapKey().map(key::equals).orElse(false);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "DeferredHolder{%s}", key);
    }
}
