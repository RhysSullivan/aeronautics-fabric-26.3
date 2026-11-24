package dev.ryanhcode.sable.compatibility;

import dev.ryanhcode.sable.platform.SableLoaderPlatform;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

/**
 * PORT-26.3: Create (Fly) registers every client level with its redstone-link and kinetic network handlers when the
 * level is made, but only server levels are ever removed again, so each world a player leaves stays in memory until the
 * game is closed. When a client level goes away, this calls the same two {@code onUnloadWorld} methods Create's server
 * path calls (each only removes the level's map entry and logs at debug). Resolved once by name: without Create, or
 * with a Create whose methods have moved, it does nothing.
 */
public final class CreateClientLevelCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("sable/create_compat");
    private static final String[] HANDLERS = {"REDSTONE_LINK_NETWORK_HANDLER", "TORQUE_PROPAGATOR"};
    private static List<MethodHandle> unloads;

    private CreateClientLevelCompat() {
    }

    /** Call when a client level is gone (dimension change or disconnect). */
    public static void onClientLevelGone(final Level level) {
        for (final MethodHandle unload : resolve()) {
            try {
                unload.invoke(level);
            } catch (final Throwable t) {
                LOGGER.debug("Create level unload failed", t);
            }
        }
    }

    private static List<MethodHandle> resolve() {
        if (unloads != null) {
            return unloads;
        }
        final List<MethodHandle> found = new ArrayList<>();
        if (SableLoaderPlatform.INSTANCE.isModLoaded("create")) {
            for (final String name : HANDLERS) {
                try {
                    final Class<?> create = Class.forName("com.zurrtum.create.Create");
                    final Object handler = create.getField(name).get(null);
                    final Class<?> accessor = Class.forName("net.minecraft.world.level.LevelAccessor");
                    final MethodHandle mh = MethodHandles.publicLookup()
                            .findVirtual(handler.getClass(), "onUnloadWorld", MethodType.methodType(void.class, accessor))
                            .bindTo(handler);
                    found.add(mh.asType(MethodType.methodType(void.class, Level.class)));
                } catch (final Throwable t) {
                    LOGGER.debug("Create {} unload not found; client levels are left to Create", name, t);
                }
            }
        }
        unloads = found;
        return found;
    }
}
