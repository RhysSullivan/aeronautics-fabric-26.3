package dev.simulated_team.simulated.mixin.port_assets;

import dev.simulated_team.simulated.port.assets.OfficialAssetsPack;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * PORT-26.3: the client's resource pack repository also lists {@link OfficialAssetsPack}, the mods' assets read from
 * the official release jar (required, at the bottom of the stack). Fabric API adds its mod packs the same way.
 */
@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @Shadow @Final @Mutable private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void simulated$officialAssets(final RepositorySource[] given, final CallbackInfo ci) {
        for (final RepositorySource s : given) {
            if (s instanceof ClientPackSource) {
                final Set<RepositorySource> all = new LinkedHashSet<>(this.sources);
                all.add(OfficialAssetsPack.source());
                this.sources = all;
                return;
            }
        }
    }
}
