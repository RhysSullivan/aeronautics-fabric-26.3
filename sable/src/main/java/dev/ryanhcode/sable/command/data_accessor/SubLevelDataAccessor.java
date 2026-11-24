package dev.ryanhcode.sable.command.data_accessor;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.sublevel.BodyPose;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.ArgProvider;

import java.util.Locale;
import java.util.function.Function;

public class SubLevelDataAccessor implements DataAccessor {
    // 26.3: data command targets are ArgProvider factories (was DataCommands.DataProvider)
    public static final ArgProvider.Factory<DataAccessor> PROVIDER = string -> new ArgProvider<>() {
        @Override
        public DataAccessor access(final CommandContext<CommandSourceStack> commandContext) throws CommandSyntaxException {
            return new SubLevelDataAccessor((ServerSubLevel) SubLevelArgumentType.getSingleSubLevel(commandContext, string));
        }

        @Override
        public ArgumentBuilder<CommandSourceStack, ?> wrap(
                final ArgumentBuilder<CommandSourceStack, ?> argumentBuilder, final Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> function
        ) {
            return argumentBuilder.then(
                    Commands.literal("sub_level").then(function.apply(Commands.argument(string, SubLevelArgumentType.singleSubLevel())))
            );
        }
    };
    private final ServerSubLevel subLevel;

    public SubLevelDataAccessor(final ServerSubLevel subLevel) {
        this.subLevel = subLevel;
    }

    @Override
    public void setData(final CompoundTag compoundTag) {
        // PORT-26.3 readable pose: the computed compound is never stored (data merge, modify and remove all come here)
        final CompoundTag current = this.subLevel.getUserDataTag();
        if (BodyPose.readable(compoundTag) || (current != null && BodyPose.readable(current))) {
            compoundTag.remove(BodyPose.KEY);
        }
        this.subLevel.setUserDataTag(compoundTag);
    }

    @Override
    public CompoundTag getData() {
        final CompoundTag userTag = this.subLevel.getUserDataTag();
        // PORT-26.3 readable pose: only for a body that asks for it, a copy with the pose computed now
        if (userTag != null && BodyPose.readable(userTag)) {
            final CompoundTag withPose = userTag.copy();
            withPose.put(BodyPose.KEY, BodyPose.compute(this.subLevel));
            return withPose;
        }
        return userTag != null ? userTag : new CompoundTag();
    }

    @Override
    public Component getModifiedSuccess() {
        return Component.translatable("commands.data.sub_level.modified", this.subLevel.toString());
    }

    @Override
    public Component getPrintSuccess(final Tag tag) {
        return Component.translatable("commands.data.sub_level.query", this.subLevel.toString(), NbtUtils.toPrettyComponent(tag));
    }

    @Override
    public Component getPrintSuccess(final NbtPathArgument.NbtPath nbtPath, final double d, final int i) {
        return Component.translatable(
                "commands.data.sub_level.get", nbtPath.asString(), this.subLevel.toString(), String.format(Locale.ROOT, "%.2f", d), i
        );
    }}
