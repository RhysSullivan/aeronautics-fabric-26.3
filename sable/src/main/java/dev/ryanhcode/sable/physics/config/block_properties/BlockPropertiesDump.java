package dev.ryanhcode.sable.physics.config.block_properties;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PORT-26.3, test-only: with {@code -Dsable.debug.dumpBlockProperties=FILE}, writes what the game resolved after the
 * physics block properties were applied, as tab-separated lines: {@code def} (each definition in the order applied),
 * {@code tag} (every block tag and its blocks) and {@code state} (every block state and its stored properties). The
 * brightness and mass tests compare it with the published data read under NeoForge's tag rules.
 */
public final class BlockPropertiesDump {
    private BlockPropertiesDump() {
    }

    /** Once the server has started: every recipe, advancement and loot table it loaded, to FILE.data. */
    public static void dumpDataIfRequested(final net.minecraft.server.MinecraftServer server) {
        final String file = System.getProperty("sable.debug.dumpBlockProperties");
        if (file == null || file.isEmpty()) {
            return;
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(file + ".data"), StandardCharsets.UTF_8))) {
            for (final var holder : server.getRecipeManager().getRecipes()) {
                out.println("recipe\t" + holder.id());
            }
            for (final var holder : server.getAdvancements().getAllAdvancements()) {
                out.println("advancement\t" + holder.id());
            }
            server.reloadableRegistries().lookup().lookupOrThrow(net.minecraft.core.registries.Registries.LOOT_TABLE)
                    .listElementIds().forEach(id -> out.println("loot\t" + id));
            Sable.LOGGER.info("PORT-26.3: loaded recipes, advancements and loot tables dumped to {}.data", file);
        } catch (final IOException e) {
            Sable.LOGGER.error("PORT-26.3: could not dump loaded data", e);
        }
    }

    public static void dumpIfRequested(final net.minecraft.server.MinecraftServer server) {
        final String file = System.getProperty("sable.debug.dumpBlockProperties");
        if (file == null || file.isEmpty()) {
            return;
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(file), StandardCharsets.UTF_8))) {
            for (final PhysicsBlockPropertiesDefinition def : PhysicsBlockPropertiesDefinitionLoader.INSTANCE.getDefinitions()) {
                out.println("def\t" + def.selector() + "\t" + def.priority() + "\t" + def.properties() + "\t" + def.overrides().map(Object::toString).orElse("{}"));
            }
            BuiltInRegistries.BLOCK.getTags().forEach(named -> {
                final StringBuilder sb = new StringBuilder("tag\t#").append(named.key().location());
                for (final Holder<Block> holder : named) {
                    sb.append('\t').append(BuiltInRegistries.BLOCK.getKey(holder.value()));
                }
                out.println(sb);
            });
            BuiltInRegistries.ITEM.getTags().forEach(named -> {
                final StringBuilder sb = new StringBuilder("itag\t#").append(named.key().location());
                for (final Holder<net.minecraft.world.item.Item> holder : named) {
                    sb.append('\t').append(BuiltInRegistries.ITEM.getKey(holder.value()));
                }
                out.println(sb);
            });
            BuiltInRegistries.FLUID.getTags().forEach(named -> {
                final StringBuilder sb = new StringBuilder("ftag\t#").append(named.key().location());
                for (final Holder<net.minecraft.world.level.material.Fluid> holder : named) {
                    sb.append('\t').append(BuiltInRegistries.FLUID.getKey(holder.value()));
                }
                out.println(sb);
            });
            for (final Block block : BuiltInRegistries.BLOCK) {
                final String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                for (final BlockState state : block.getStateDefinition().getPossibleStates()) {
                    final BlockStateExtension ext = (BlockStateExtension) state;
                    final String s = state.toString();
                    final int bracket = s.indexOf('[');
                    out.println("state\t" + id + "\t" + (bracket < 0 ? "" : s.substring(bracket + 1, s.length() - 1))
                            + "\tmass=" + ext.sable$getProperty(PhysicsBlockPropertyTypes.MASS.get())
                            + "\tfriction=" + ext.sable$getProperty(PhysicsBlockPropertyTypes.FRICTION.get())
                            + "\trestitution=" + ext.sable$getProperty(PhysicsBlockPropertyTypes.RESTITUTION.get())
                            + "\tfragile=" + ext.sable$getProperty(PhysicsBlockPropertyTypes.FRAGILE.get())
                            + "\tvolume=" + ext.sable$getProperty(PhysicsBlockPropertyTypes.VOLUME.get())
                            + "\tfloating_material=" + ext.sable$getProperty(PhysicsBlockPropertyTypes.FLOATING_MATERIAL.get())
                            + "\tfloating_scale=" + ext.sable$getProperty(PhysicsBlockPropertyTypes.FLOATING_SCALE.get())
                            + "\tinertia=" + ext.sable$getProperty(PhysicsBlockPropertyTypes.INERTIA.get()));
                }
            }
            Sable.LOGGER.info("PORT-26.3: block physics properties dumped to {}", file);
        } catch (final IOException e) {
            Sable.LOGGER.error("PORT-26.3: could not dump block physics properties to {}", file, e);
        }
    }
}
