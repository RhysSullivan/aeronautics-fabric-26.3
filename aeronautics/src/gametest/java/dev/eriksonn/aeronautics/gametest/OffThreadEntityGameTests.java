package dev.eriksonn.aeronautics.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * PORT-26.3: entities are saved off the server thread while terrain generates (a structure or spawn puts a zombie into
 * a proto chunk on a worker thread, and 26.3's zombie save asks {@code isEyeInFluid(WATER)}). Nothing our mixins add to
 * that path may read the level: a level read off the server thread hands the chunk request to the server thread and
 * waits, and on a live server the server thread was itself waiting for that generation, so both stopped for good.
 *
 * <p>The test builds the same shape: a zombie at a position whose chunk is not loaded is saved on another thread while
 * the server thread waits for it. A level read on that thread would need the server thread, so the save times out.
 */
public final class OffThreadEntityGameTests {
    private static final long WAIT_SECONDS = 10;

    @GameTest(maxTicks = 20)
    public void zombieSavedOffTheServerThreadNeverWaitsForTheServerThread(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos far = new BlockPos(1_900_000, 64, 1_900_000);
        helper.assertTrue(!level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4),
                "the test position's chunk is already loaded");

        final Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.STRUCTURE);
        helper.assertTrue(zombie != null, "no zombie created");
        zombie.setPos(Vec3.atCenterOf(far));

        final CompletableFuture<Boolean> save = CompletableFuture.supplyAsync(() -> {
            final TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            zombie.saveWithoutId(output); // a zombie's save asks isEyeInFluid(WATER) (its drowning conversion tracker)
            return zombie.isEyeInFluid(FluidTags.WATER);
        }, task -> {
            final Thread thread = new Thread(task, "aeronautics-gametest-worldgen-worker");
            thread.setDaemon(true);
            thread.start();
        });

        boolean finished;
        boolean eyeInWater = false;
        try {
            eyeInWater = save.get(WAIT_SECONDS, TimeUnit.SECONDS);
            finished = true;
        } catch (final TimeoutException e) {
            finished = false;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        helper.assertTrue(finished, "saving a zombie off the server thread waited for the server thread for "
                + WAIT_SECONDS + " s (a level read from a mixin; on a live server this froze the server)");
        helper.assertTrue(!eyeInWater, "a zombie that never ticked reported its eyes in water");
        zombie.discard();
        helper.succeed();
    }
}
