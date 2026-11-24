package dev.simulated_team.simulated.compat.create;

import net.minecraft.world.level.LevelAccessor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

public final class CommonLevelAttached<T> {
    private final Function<LevelAccessor, T> factory;
    // PORT-26.3: values usually reach their level (a handler holding it), so the weak keys never let go on their own;
    // every instance is listed here and a level that goes away is removed from all of them (as Create's LevelAttached).
    private static final List<CommonLevelAttached<?>> ALL = new CopyOnWriteArrayList<>();
    private final Map<LevelAccessor, T> values = Collections.synchronizedMap(new WeakHashMap<>());

    public CommonLevelAttached(final Function<LevelAccessor, T> factory) {
        this.factory = factory;
        ALL.add(this);
    }

    /** Drops what every instance keeps for a level that has gone away (a client level left or a server level closed). */
    public static void invalidateLevel(final LevelAccessor level) {
        for (final CommonLevelAttached<?> attached : ALL) {
            attached.values.remove(level);
        }
    }

    public T get(final LevelAccessor level) {
        synchronized (this.values) {
            final T existing = this.values.get(level);
            if (existing != null || this.values.containsKey(level)) {
                return existing;
            }
            final T created = this.factory.apply(level);
            if (created != null) {
                this.values.put(level, created);
            }
            return created;
        }
    }
}
