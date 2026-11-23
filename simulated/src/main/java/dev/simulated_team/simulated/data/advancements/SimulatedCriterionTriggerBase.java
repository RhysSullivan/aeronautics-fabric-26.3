package dev.simulated_team.simulated.data.advancements;

import org.jspecify.annotations.NullMarked;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.ValidationContextSource;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Supplier;

/**
 * PORT-26.3: 26.3 keeps trigger listeners inside {@link PlayerAdvancements} ({@code getTriggerMapForType}), so the
 * listener bookkeeping this class used to do is gone; {@link #trigger} awards the matching criteria directly.
 */
@ParametersAreNonnullByDefault
@NullMarked
public abstract class SimulatedCriterionTriggerBase<T extends SimulatedCriterionTriggerBase.Instance> implements CriterionTrigger<T> {

    private final Identifier id;

    public SimulatedCriterionTriggerBase(final Identifier id) {
        this.id = id;
    }

    public Identifier getId() {
        return this.id;
    }

    protected void trigger(final ServerPlayer player, @Nullable final List<Supplier<Object>> suppliers) {
        final PlayerAdvancements playerAdvancements = player.getAdvancements();
        final Map<PlayerAdvancements.TriggerInstanceKey, T> listeners = playerAdvancements.getTriggerMapForType(this);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        final List<PlayerAdvancements.TriggerInstanceKey> matched = new ArrayList<>();
        for (final Map.Entry<PlayerAdvancements.TriggerInstanceKey, T> entry : listeners.entrySet()) {
            if (entry.getValue().test(suppliers)) {
                matched.add(entry.getKey());
            }
        }
        for (final PlayerAdvancements.TriggerInstanceKey key : matched) {
            playerAdvancements.award(key.advancement(), key.criterion());
        }
    }

    public abstract static class Instance implements CriterionTriggerInstance {
        private final Identifier id;
        public Instance(final Identifier id) {
            this.id = id;
        }
        public Identifier getId() {
            return this.id;
        }
        protected abstract boolean test (@Nullable List<Supplier<Object>> suppliers);

        @Override
        public void validate(final ValidationContextSource validator) {
        }
    }
}
