package ru.monolith.arsenal.physics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

/**
 * Operator diagnostics for the vehicle physics backend:
 * {@code /monolith_arsenal physics create_test|info|list|force|torque|remove}.
 * Everything goes through {@link VehiclePhysicsBackend}; there are no custom network packets.
 */
public final class PhysicsCommands {
    private PhysicsCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("monolith_arsenal")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.literal("physics")
                        .then(CommandManager.literal("backend").executes(PhysicsCommands::backend))
                        .then(CommandManager.literal("list").executes(PhysicsCommands::list))
                        .then(CommandManager.literal("create_test")
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(PhysicsCommands::createTest)))
                        .then(CommandManager.literal("info")
                                .then(CommandManager.argument("id", LongArgumentType.longArg())
                                        .executes(PhysicsCommands::info)))
                        .then(CommandManager.literal("force")
                                .then(CommandManager.argument("id", LongArgumentType.longArg())
                                        .then(CommandManager.argument("newtons", Vec3ArgumentType.vec3(false))
                                                .then(CommandManager.argument("ticks", IntegerArgumentType.integer(1, 1200))
                                                        .executes(context -> schedule(context, true))))))
                        .then(CommandManager.literal("torque")
                                .then(CommandManager.argument("id", LongArgumentType.longArg())
                                        .then(CommandManager.argument("newtonMetres", Vec3ArgumentType.vec3(false))
                                                .then(CommandManager.argument("ticks", IntegerArgumentType.integer(1, 1200))
                                                        .executes(context -> schedule(context, false))))))
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("id", LongArgumentType.longArg())
                                        .executes(PhysicsCommands::remove)))));
    }

    private static int backend(CommandContext<ServerCommandSource> context) {
        String line = "Vehicle physics backend: " + VehiclePhysics.backend().describe();
        context.getSource().sendFeedback(() -> Text.literal(line), false);
        return VehiclePhysics.backend().isAvailable() ? 1 : 0;
    }

    private static int list(CommandContext<ServerCommandSource> context) {
        var bodies = VehiclePhysics.backend().bodies(context.getSource().getWorld());
        for (VehicleBody body : bodies) {
            String line = describe(body);
            context.getSource().sendFeedback(() -> Text.literal(line), false);
        }
        int count = bodies.size();
        context.getSource().sendFeedback(() -> Text.literal(count + " vehicle body(ies) loaded"), false);
        return count;
    }

    private static int createTest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!VehiclePhysics.backend().isAvailable()) {
            context.getSource().sendError(Text.literal("No physics backend: " + VehiclePhysics.backend().describe()));
            return 0;
        }
        var pos = BlockPosArgumentType.getLoadedBlockPos(context, "pos");
        OptionalLong created = VehiclePhysics.backend().createTestBody(context.getSource().getWorld(), pos);
        if (created.isEmpty()) {
            context.getSource().sendError(Text.literal("Could not create the test body at " + pos.toShortString() + " (space occupied?)"));
            return 0;
        }
        long id = created.getAsLong();
        context.getSource().sendFeedback(() -> Text.literal("Created test body id=" + id), true);
        return (int) id;
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        long id = LongArgumentType.getLong(context, "id");
        Optional<VehicleBody> body = VehiclePhysics.find(context.getSource().getWorld(), id);
        if (body.isEmpty()) {
            context.getSource().sendError(Text.literal("No loaded vehicle body with id " + id));
            return 0;
        }
        String line = describe(body.get());
        context.getSource().sendFeedback(() -> Text.literal(line), false);
        return 1;
    }

    private static int schedule(CommandContext<ServerCommandSource> context, boolean force) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(context, "id");
        Vec3d vector = Vec3ArgumentType.getVec3(context, force ? "newtons" : "newtonMetres");
        int ticks = IntegerArgumentType.getInteger(context, "ticks");
        var world = context.getSource().getWorld();
        if (VehiclePhysics.find(world, id).isEmpty()) {
            context.getSource().sendError(Text.literal("No loaded vehicle body with id " + id));
            return 0;
        }
        ScheduledForces.add(world, id, force ? vector : Vec3d.ZERO, force ? Vec3d.ZERO : vector, ticks);
        String line = String.format(Locale.ROOT, "Applying %s (%.1f, %.1f, %.1f) to body %d for %d ticks",
                force ? "force" : "torque", vector.x, vector.y, vector.z, id, ticks);
        context.getSource().sendFeedback(() -> Text.literal(line), true);
        return 1;
    }

    private static int remove(CommandContext<ServerCommandSource> context) {
        long id = LongArgumentType.getLong(context, "id");
        if (!VehiclePhysics.backend().remove(context.getSource().getWorld(), id)) {
            context.getSource().sendError(Text.literal("No loaded vehicle body with id " + id));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Removed vehicle body " + id), true);
        return 1;
    }

    static String describe(VehicleBody body) {
        Vec3d p = body.position();
        Vec3d v = body.linearVelocity();
        Vec3d w = body.angularVelocity();
        Vector3d euler = body.rotation().getEulerAnglesYXZ(new Vector3d());
        return String.format(Locale.ROOT,
                "body %d pos=(%.2f, %.2f, %.2f) rot(pitch=%.1f, yaw=%.1f, roll=%.1f) vel=(%.2f, %.2f, %.2f) angVel=(%.2f, %.2f, %.2f) mass=%.0f",
                body.id(), p.x, p.y, p.z, Math.toDegrees(euler.x), Math.toDegrees(euler.y), Math.toDegrees(euler.z),
                v.x, v.y, v.z, w.x, w.y, w.z, body.mass());
    }
}
