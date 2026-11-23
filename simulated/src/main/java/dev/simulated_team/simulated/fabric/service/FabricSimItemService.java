package dev.simulated_team.simulated.fabric.service;

import com.zurrtum.create.AllItemTags;
import dev.simulated_team.simulated.service.SimItemService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

import java.util.Optional;

/**
 * PORT-26.3: fuel is the data-driven COOKING_FUEL component, resolved against a loot context, so this keeps the
 * running server instead of a FuelValues table. Without a server (a client with no integrated server) any fuel
 * item reports coal's 1600 ticks, which is all the client-side callers need (is-fuel checks, ponder scenes).
 */
public final class FabricSimItemService implements SimItemService {
    private static final int CLIENT_FUEL_TICKS = 1600;
    private static volatile MinecraftServer server;

    public static void setServer(final MinecraftServer running) {
        server = running;
    }

    @Override
    public int getBurnTime(final ItemStack stack) {
        if (!stack.has(DataComponents.COOKING_FUEL)) {
            return 0;
        }
        final MinecraftServer running = server;
        if (running == null) {
            return CLIENT_FUEL_TICKS;
        }
        final ServerLevel level = running.overworld();
        final LootContext context = new LootContext.Builder(new LootParams.Builder(level).create(LootContextParamSets.EMPTY))
                .create(Optional.empty());
        return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
    }

    @Override
    public int getSuperheatedBurnTime(final ItemStack stack) {
        return stack.typeHolder().is(AllItemTags.BLAZE_BURNER_FUEL_SPECIAL) ? 3200 : 0;
    }
}
