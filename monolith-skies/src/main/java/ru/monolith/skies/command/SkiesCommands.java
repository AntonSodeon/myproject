package ru.monolith.skies.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import ru.monolith.skies.MonolithSkies;
import ru.monolith.skies.ship.ShipAssembler;
import ru.monolith.skies.ship.ShipEntity;

/**
 * /monolith_skies assemble|disassemble|list|push|spin — operator tools for building and testing ships.
 */
public final class SkiesCommands {
    private SkiesCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal(MonolithSkies.MOD_ID)
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.literal("assemble")
                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(SkiesCommands::assemble)))
                .then(CommandManager.literal("disassemble")
                        .then(CommandManager.argument("ships", EntityArgumentType.entities())
                                .executes(SkiesCommands::disassemble)))
                .then(CommandManager.literal("list")
                        .executes(SkiesCommands::list))
                .then(CommandManager.literal("push")
                        .then(CommandManager.argument("ships", EntityArgumentType.entities())
                                .then(CommandManager.argument("velocity", Vec3ArgumentType.vec3(false))
                                        .executes(SkiesCommands::push))))
                .then(CommandManager.literal("spin")
                        .then(CommandManager.argument("ships", EntityArgumentType.entities())
                                .then(CommandManager.argument("degreesPerTick", DoubleArgumentType.doubleArg(-10.0, 10.0))
                                        .executes(SkiesCommands::spin)))));
    }

    private static int assemble(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ShipAssembler.Result result = ShipAssembler.assemble(source.getWorld(),
                BlockPosArgumentType.getLoadedBlockPos(context, "pos"), ShipAssembler.DEFAULT_MAX_BLOCKS);
        return report(source, result);
    }

    private static int disassemble(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        int done = 0;
        for (ShipEntity ship : ships(context)) {
            done += report(source, ShipAssembler.disassemble((ServerWorld) ship.getEntityWorld(), ship));
        }
        return done;
    }

    private static int list(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int count = 0;
        for (ServerWorld world : source.getServer().getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof ShipEntity ship) {
                    count++;
                    Vec3d p = ship.getEntityPos();
                    Vec3d v = ship.getVelocity();
                    String line = String.format(Locale.ROOT,
                            "%s %s blocks=%d mass=%.1f pos=(%.2f, %.2f, %.2f) vel=(%.3f, %.3f, %.3f) yaw=%.1f submerged=%.2f onGround=%s",
                            ship.getUuidAsString(), world.getRegistryKey().getValue(), ship.getBlockCount(), ship.getMass(),
                            p.x, p.y, p.z, v.x, v.y, v.z, ship.getShipYaw(), ship.getSubmergedFraction(), ship.isOnGround());
                    source.sendFeedback(() -> Text.literal(line), false);
                }
            }
        }
        int total = count;
        source.sendFeedback(() -> Text.translatable("message.monolith_skies.list", total), false);
        return count;
    }

    private static int push(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Vec3d velocity = Vec3ArgumentType.getVec3(context, "velocity");
        List<ShipEntity> ships = ships(context);
        ships.forEach(ship -> ship.addVelocity(velocity));
        return ships.size();
    }

    private static int spin(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        double degrees = DoubleArgumentType.getDouble(context, "degreesPerTick");
        List<ShipEntity> ships = ships(context);
        ships.forEach(ship -> ship.addYawVelocity(degrees));
        return ships.size();
    }

    private static List<ShipEntity> ships(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgumentType.getEntities(context, "ships");
        return entities.stream().filter(ShipEntity.class::isInstance).map(ShipEntity.class::cast).toList();
    }

    private static int report(ServerCommandSource source, ShipAssembler.Result result) {
        Text message = Text.translatable(result.messageKey(), result.blocks());
        if (result.success()) {
            source.sendFeedback(() -> message, true);
            return 1;
        }
        source.sendError(message);
        return 0;
    }
}
