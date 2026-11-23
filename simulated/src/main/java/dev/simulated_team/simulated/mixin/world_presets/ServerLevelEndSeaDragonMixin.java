package dev.simulated_team.simulated.mixin.world_presets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.simulated_team.simulated.index.SimWorldPresets;
import dev.simulated_team.simulated.mixin_interface.PrimaryLevelDataExtension;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Optional;

/**
 * PORT-26.3: End Sea worlds start with the dragon fight already won (no dragon, no respawn), as the 1.21 version
 * did by writing the fight into the level data at creation. 26.3 keeps the fight as per-level saved data, so a
 * fresh fight (no saved file yet) is created in the won state instead; saved fights load unchanged.
 */
@Mixin(ServerLevel.class)
public class ServerLevelEndSeaDragonMixin {

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/SavedDataStorage;computeIfAbsent(Lnet/minecraft/world/level/saveddata/SavedDataType;)Lnet/minecraft/world/level/saveddata/SavedData;"))
    private SavedData simulated$endSeaDragonFight(final SavedDataStorage storage, final SavedDataType<?> type,
                                                  final Operation<SavedData> original,
                                                  @Local(argsOnly = true) final MinecraftServer server) {
        if (type == EnderDragonFight.TYPE
                && server.getWorldData() instanceof final PrimaryLevelDataExtension data
                && SimWorldPresets.END_SEA.id().equals(data.getPreset())) {
            final SavedDataType<EnderDragonFight> won = new SavedDataType<>(type.id(), () -> new EnderDragonFight(
                    false, true, true, Optional.empty(), 0, Optional.empty(), Optional.empty(), new ObjectArrayList<>(), List.of()),
                    EnderDragonFight.CODEC, EnderDragonFight.TYPE.dataFixType());
            return original.call(storage, won);
        }
        return original.call(storage, type);
    }
}
