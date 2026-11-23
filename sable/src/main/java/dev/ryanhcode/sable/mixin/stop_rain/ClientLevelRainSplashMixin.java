package dev.ryanhcode.sable.mixin.stop_rain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.mixinhelpers.stop_rain.SubLevelRainHeight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT-26.3: rain splash particles moved from the weather renderer to {@code ClientLevel.tickWeatherEffects}. As in
 * 1.21, the column's height is raised to a sub-level deck above the terrain, so splashes land on decks and not on
 * the ground under them.
 */
@Mixin(ClientLevel.class)
public class ClientLevelRainSplashMixin {
    @WrapOperation(method = "tickWeatherEffects", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos sable$splashOnDecks(final ClientLevel level, final Heightmap.Types type, final BlockPos pos, final Operation<BlockPos> original) {
        final BlockPos terrain = original.call(level, type, pos);
        final int deck = SubLevelRainHeight.get(level, pos.getX(), 2, pos.getZ());
        return deck > terrain.getY() ? new BlockPos(terrain.getX(), deck, terrain.getZ()) : terrain;
    }
}
