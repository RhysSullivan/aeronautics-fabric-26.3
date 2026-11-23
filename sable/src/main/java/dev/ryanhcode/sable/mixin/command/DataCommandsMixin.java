package dev.ryanhcode.sable.mixin.command;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.command.data_accessor.SubLevelDataAccessor;
import net.minecraft.server.commands.ArgProvider;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(DataCommands.class)
public class DataCommandsMixin {

    // 26.3: ALL_PROVIDERS = List.of(entity, block, storage) of ArgProvider.Factory<DataAccessor>; add the sub-level one
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE", target = "Ljava/util/List;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"))
    private static <E> List<E> sable$allProviders(final E e1, final E e2, final E e3, final Operation<List<E>> original) {
        final List<E> providers = original.call(e1, e2, e3);
        if (!(e1 instanceof ArgProvider.Factory<?>)) {
            return providers;
        }
        final List<E> mutableList = new ArrayList<>(providers);
        @SuppressWarnings("unchecked")
        final E sable = (E) (ArgProvider.Factory<DataAccessor>) SubLevelDataAccessor.PROVIDER;
        mutableList.add(sable);
        return List.copyOf(mutableList);
    }
}
