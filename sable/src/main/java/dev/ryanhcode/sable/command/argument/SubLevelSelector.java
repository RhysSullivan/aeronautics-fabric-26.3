package dev.ryanhcode.sable.command.argument;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.command.SableCommandHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.*;

public class SubLevelSelector {

    private final SubLevelSelectorType type;
    private final List<Pair<SubLevelSelectorModifierType, SubLevelSelectorModifierType.Modifier>> modifiers;

    public SubLevelSelector(final SubLevelSelectorType type, final List<Pair<SubLevelSelectorModifierType, SubLevelSelectorModifierType.Modifier>> modifiers) {
        this.type = type;
        this.modifiers = modifiers;
    }

    public SubLevelSelectorType getSelectorType() {
        return this.type;
    }

    public Collection<ServerSubLevel> getSubLevels(final CommandSourceStack source) throws CommandSyntaxException {
        if (this.type == null) {
            return List.of();
        }

        final ServerLevel level = source.getLevel();
        final ServerSubLevelContainer container = SableCommandHelper.requireSubLevelContainer(source);

        final Iterable<ServerSubLevel> containerBodies = container.getAllSubLevels();
        final Collection<ServerSubLevel> bodies = new ObjectArrayList<>();

        for (final ServerSubLevel subLevel : containerBodies) {
            bodies.add(subLevel);
        }

        if (bodies.isEmpty()) {
            return Collections.emptySet();
        }

        final ActiveSableCompanion helper = Sable.HELPER;
        final Collection<ServerSubLevel> collectedSubLevels = switch (this.type) {
            case ALL -> new HashSet<>(bodies);
            // PORT-26.3: as vanilla's @n and @r, the modifiers (name=, distance=, ...) filter all bodies first; the nearest
            // or a random one is taken from what is left (below). Upstream took one body first and filtered it after,
            // so @n[name=X] matched nothing whenever another body was nearer.
            case NEAREST, RANDOM -> new ObjectArrayList<>(bodies);
            case INSIDE -> {
                final ServerSubLevel subLevel = (ServerSubLevel) helper.getContaining(level, source.getPosition());
                if (subLevel != null) {
                    yield Collections.singleton(subLevel);
                } else {
                    yield Collections.emptySet();
                }
            }
            case TRACKING -> {
                if (source.getEntity() == null) {
                    yield Collections.emptySet();
                }

                final ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getTrackingSubLevel(source.getEntity());

                if (subLevel != null) {
                    yield Collections.singleton(subLevel);
                } else {
                    yield Collections.emptySet();
                }
            }
            case VIEWED -> {
                if (source.getEntity() != null) {
                    final HitResult res = source.getEntity().pick(100.0, 1.0f, true);

                    if (res instanceof final BlockHitResult blockHitResult) {
                        final ServerSubLevel containing = (ServerSubLevel) helper.getContaining(level, blockHitResult.getBlockPos());
                        if (containing != null) {
                            yield Collections.singleton(containing);
                        } else {
                            yield Collections.emptySet();
                        }
                    } else {
                        yield Collections.emptySet();
                    }
                } else {
                    yield Collections.emptySet();
                }
            }
            case LATEST -> {
                final List<ServerSubLevel> subLevels = container.getAllSubLevels();
                if (subLevels.isEmpty()) {
                    yield Collections.emptySet();
                }
                yield Collections.singleton(subLevels.getLast());
            }
        };

        List<ServerSubLevel> modifiedSubLevels = new ObjectArrayList<>(collectedSubLevels);

        final Vector3d position = new Vector3d(source.getPosition().x, source.getPosition().y, source.getPosition().z);
        // every ordering (the default nearest and an explicit sort=) measures from the source projected out of any body
        // it is in, so a command run inside a body's plot orders bodies by where it is in the world; filters keep the
        // source position as given. Projected on first use: plain @e and @r never need it.
        final java.util.function.Supplier<Vector3d> orderFrom = new java.util.function.Supplier<>() {
            private Vector3d value;

            @Override
            public Vector3d get() {
                if (this.value == null) {
                    final Vec3 projected = Sable.HELPER.projectOutOfSubLevel(source.getLevel(), source.getPosition());
                    this.value = new Vector3d(projected.x, projected.y, projected.z);
                }
                return this.value;
            }
        };
        this.modifiers.sort(
                Comparator.comparingInt(a -> a.first().getFilterPriority().ordinal())
        );

        // PORT-26.3: @n and @r work as vanilla's: the position and filter modifiers (name=, distance=, ...) run over all
        // bodies first; then the bodies are ordered (sort= when given, otherwise nearest for @n and random for @r); then
        // limit= (1 when not given) takes from the front. Upstream chose one body before filtering.
        final boolean chooses = this.type == SubLevelSelectorType.NEAREST || this.type == SubLevelSelectorType.RANDOM;
        boolean userSort = false;
        boolean userLimit = false;
        for (final Pair<SubLevelSelectorModifierType, SubLevelSelectorModifierType.Modifier> modifier : this.modifiers) {
            final SubLevelSelectorModifierType.FilterPriority priority = modifier.first().getFilterPriority();
            userSort |= priority == SubLevelSelectorModifierType.FilterPriority.SORTING;
            userLimit |= priority == SubLevelSelectorModifierType.FilterPriority.SORTING_SELECTION;
        }

        boolean ordered = false;
        for (final Pair<SubLevelSelectorModifierType, SubLevelSelectorModifierType.Modifier> modifier : this.modifiers) {
            if (chooses && !ordered && !userSort
                    && modifier.first().getFilterPriority().ordinal() >= SubLevelSelectorModifierType.FilterPriority.SORTING.ordinal()) {
                this.defaultOrder(modifiedSubLevels, orderFrom, level);
                ordered = true;
            }
            final boolean ordering = modifier.first().getFilterPriority() == SubLevelSelectorModifierType.FilterPriority.SORTING;
            modifiedSubLevels = modifier.right().apply(modifiedSubLevels, ordering ? orderFrom.get() : position);
        }
        if (chooses && !ordered && !userSort) {
            this.defaultOrder(modifiedSubLevels, orderFrom, level);
        }
        if (chooses && !userLimit && modifiedSubLevels.size() > 1) {
            modifiedSubLevels = new ObjectArrayList<>(modifiedSubLevels.subList(0, 1));
        }
        return modifiedSubLevels;
    }

    /** @n: nearest first, from the source projected out of any body it is in; @r: shuffled. */
    private void defaultOrder(final List<ServerSubLevel> bodies, final java.util.function.Supplier<Vector3d> fromSupplier, final ServerLevel level) {
        if (this.type == SubLevelSelectorType.NEAREST) {
            final Vector3d from = fromSupplier.get();
            bodies.sort(Comparator.comparingDouble(body -> body.logicalPose().position().distanceSquared(from)));
        } else {
            // PORT-NOTE(mc26.1): Level.random is protected now; use getRandom().
            Util.shuffle(bodies, level.getRandom());
        }
    }

}