package dev.ryanhcode.sable.sublevel.render.vanilla;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LightLayer;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PORT-26.3, test-only: {@code -Dsable.debug.lightDump=FILE}. When {@code FILE.req} exists (it holds a world box
 * "x0 y0 z0 x1 y1 z1"), the client writes FILE once and deletes the request: for every sub-level, its plot bounds
 * (grown by one) and for every cell "plot x y z sky block id shade_up shade_north"; then the same for every cell of
 * the world box ("world ..."). The deck-brightness check compares the ship's cells with the world copy's.
 */
public final class LightProbeDump {
    private LightProbeDump() {
    }

    public static void maybeDump(final ClientLevel level, final Iterable<ClientSubLevel> subLevels) {
        final String file = System.getProperty("sable.debug.lightDump");
        if (file == null || file.isEmpty()) {
            return;
        }
        final Path req = Path.of(file + ".req");
        if (!Files.exists(req)) {
            return;
        }
        try {
            final String[] box = Files.readString(req).trim().split("\\s+");
            Files.delete(req);
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(file), StandardCharsets.UTF_8))) {
                for (final ClientSubLevel subLevel : subLevels) {
                    final var b = subLevel.getPlot().getBoundingBox();
                    out.println("sublevel " + b.minX() + " " + b.minY() + " " + b.minZ() + " " + b.maxX() + " " + b.maxY() + " " + b.maxZ()
                            + " scale " + subLevel.getLatestSkyLightScale());
                    cells(out, "plot", level, b.minX() - 1, b.minY() - 1, b.minZ() - 1, b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
                }
                if (box.length == 6) {
                    cells(out, "world", level, Integer.parseInt(box[0]), Integer.parseInt(box[1]), Integer.parseInt(box[2]),
                            Integer.parseInt(box[3]), Integer.parseInt(box[4]), Integer.parseInt(box[5]));
                }
                out.println("end");
            }
            Sable.LOGGER.info("PORT-26.3: light dump written to {}", file);
        } catch (final IOException | RuntimeException e) {
            Sable.LOGGER.error("PORT-26.3: light dump failed", e);
        }
    }

    private static void cells(final PrintWriter out, final String tag, final ClientLevel level, final int x0, final int y0, final int z0,
                              final int x1, final int y1, final int z1) {
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    p.set(x, y, z);
                    out.println(tag + " " + x + " " + y + " " + z + " " + level.getBrightness(LightLayer.SKY, p) + " " + level.getBrightness(LightLayer.BLOCK, p)
                            + " " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock())
                            + " " + level.cardinalLighting().byFace(Direction.UP) + " " + level.cardinalLighting().byFace(Direction.NORTH));
                }
            }
        }
    }
}
