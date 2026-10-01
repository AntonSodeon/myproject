package ru.monolith.arsenal.physics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

/**
 * Operator diagnostics for the vehicle physics backend ({@code /monolith_arsenal physics ...}, permission level 2).
 * Everything goes through {@link VehiclePhysicsBackend}; Valkyrien Skies does its own client sync, so there are no
 * custom network packets.
 */
public final class PhysicsCommands {
    private static final double MAX_SECONDS = 60.0;

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
                        .then(withId("status", PhysicsCommands::status))
                        .then(withId("info", PhysicsCommands::info))
                        .then(withId("remove", PhysicsCommands::remove))
                        .then(vectorCommand("impulse", false, (body, v, s) -> body.applyImpulse(v)))
                        .then(vectorCommand("angular_impulse", false, (body, v, s) -> body.applyAngularImpulse(v)))
                        .then(vectorCommand("force", true, VehicleBody::applyForce))
                        .then(vectorCommand("torque", true, VehicleBody::applyTorque))
                        .then(CommandManager.literal("continuous")
                                .then(CommandManager.argument("id", LongArgumentType.longArg())
                                        .then(CommandManager.argument("channel", StringArgumentType.word())
                                                .then(CommandManager.argument("force", Vec3ArgumentType.vec3(false))
                                                        .then(CommandManager.argument("torque", Vec3ArgumentType.vec3(false))
                                                                .executes(PhysicsCommands::continuous))))))
                        .then(CommandManager.literal("clear_continuous")
                                .then(CommandManager.argument("id", LongArgumentType.longArg())
                                        .then(CommandManager.argument("channel", StringArgumentType.word())
                                                .executes(PhysicsCommands::clearContinuous))))));
    }

    private interface IdCommand {
        int run(CommandContext<ServerCommandSource> context, long id) throws CommandSyntaxException;
    }

    private interface VectorAction {
        boolean apply(VehicleBody body, Vec3d vector, double seconds);
    }

    private static ArgumentBuilder<ServerCommandSource, ?> withId(String name, IdCommand command) {
        return CommandManager.literal(name).then(CommandManager.argument("id", LongArgumentType.longArg())
                .executes(context -> command.run(context, LongArgumentType.getLong(context, "id"))));
    }

    private static ArgumentBuilder<ServerCommandSource, ?> vectorCommand(String name, boolean timed, VectorAction action) {
        var vector = CommandManager.argument("vector", Vec3ArgumentType.vec3(false));
        if (timed) {
            vector.then(CommandManager.argument("seconds", DoubleArgumentType.doubleArg(0.001, MAX_SECONDS))
                    .executes(context -> applyVector(context, name, action, DoubleArgumentType.getDouble(context, "seconds"))));
        } else {
            vector.executes(context -> applyVector(context, name, action, 0.0));
        }
        return CommandManager.literal(name).then(CommandManager.argument("id", LongArgumentType.longArg()).then(vector));
    }

    private static int applyVector(CommandContext<ServerCommandSource> context, String name, VectorAction action, double seconds) {
        long id = LongArgumentType.getLong(context, "id");
        Optional<VehicleBody> body = ready(context, id);
        if (body.isEmpty()) {
            return 0;
        }
        Vec3d v = Vec3ArgumentType.getVec3(context, "vector");
        if (!action.apply(body.get(), v, seconds)) {
            context.getSource().sendError(Text.literal("Command queue of body " + id + " is full"));
            return 0;
        }
        String line = String.format(Locale.ROOT, "Queued %s (%.1f, %.1f, %.1f)%s on body %d", name, v.x, v.y, v.z,
                seconds > 0 ? String.format(Locale.ROOT, " for %.3f s", seconds) : "", id);
        context.getSource().sendFeedback(() -> Text.literal(line), true);
        return 1;
    }

    private static Optional<VehicleBody> ready(CommandContext<ServerCommandSource> context, long id) {
        Optional<VehicleBody> body = VehiclePhysics.find(context.getSource().getWorld(), id);
        if (body.isEmpty()) {
            String state = VehiclePhysics.backend().creation(id).map(c -> " (creation " + c.state() + ")").orElse("");
            context.getSource().sendError(Text.literal("No ready vehicle body with id " + id + state));
        }
        return body;
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
        ServerCommandSource source = context.getSource();
        var pos = BlockPosArgumentType.getLoadedBlockPos(context, "pos");
        BodyCreation creation = VehiclePhysics.backend().createTestBody(source.getWorld(), pos);
        if (creation.state() == BodyCreation.State.FAILED && creation.id() < 0) {
            source.sendError(Text.literal("Could not create the test body: " + creation.failure()));
            return 0;
        }
        long id = creation.id();
        source.sendFeedback(() -> Text.literal("Test body id=" + id + " PENDING"), true);
        creation.onComplete(done -> {
            if (done.state() == BodyCreation.State.READY) {
                source.sendFeedback(() -> Text.literal("Test body id=" + id + " READY"), true);
            } else {
                source.sendError(Text.literal("Test body id=" + id + " FAILED: " + done.failure()));
            }
        });
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> context, long id) {
        String state = VehiclePhysics.find(context.getSource().getWorld(), id).isPresent() ? "READY"
                : VehiclePhysics.backend().creation(id).map(c -> c.state() + (c.failure().isEmpty() ? "" : ": " + c.failure()))
                .orElse("UNKNOWN");
        context.getSource().sendFeedback(() -> Text.literal("Body " + id + " status " + state), false);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> context, long id) {
        Optional<VehicleBody> body = ready(context, id);
        if (body.isEmpty()) {
            return 0;
        }
        String line = describe(body.get()) + " " + body.get().diagnostics();
        context.getSource().sendFeedback(() -> Text.literal(line), false);
        return 1;
    }

    private static int continuous(CommandContext<ServerCommandSource> context) {
        long id = LongArgumentType.getLong(context, "id");
        Optional<VehicleBody> body = ready(context, id);
        if (body.isEmpty()) {
            return 0;
        }
        String channel = StringArgumentType.getString(context, "channel");
        body.get().setContinuous(channel, Vec3ArgumentType.getVec3(context, "force"), Vec3ArgumentType.getVec3(context, "torque"));
        context.getSource().sendFeedback(() -> Text.literal("Continuous channel '" + channel + "' set on body " + id), true);
        return 1;
    }

    private static int clearContinuous(CommandContext<ServerCommandSource> context) {
        long id = LongArgumentType.getLong(context, "id");
        Optional<VehicleBody> body = ready(context, id);
        if (body.isEmpty()) {
            return 0;
        }
        String channel = StringArgumentType.getString(context, "channel");
        body.get().clearContinuous(channel);
        context.getSource().sendFeedback(() -> Text.literal("Continuous channel '" + channel + "' cleared on body " + id), true);
        return 1;
    }

    private static int remove(CommandContext<ServerCommandSource> context, long id) {
        if (!VehiclePhysics.backend().remove(context.getSource().getWorld(), id)) {
            context.getSource().sendError(Text.literal("No ready vehicle body with id " + id));
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
                "body %d pos=(%.3f, %.3f, %.3f) rot(pitch=%.2f, yaw=%.2f, roll=%.2f) vel=(%.4f, %.4f, %.4f) angVel=(%.4f, %.4f, %.4f) mass=%.0f",
                body.id(), p.x, p.y, p.z, Math.toDegrees(euler.x), Math.toDegrees(euler.y), Math.toDegrees(euler.z),
                v.x, v.y, v.z, w.x, w.y, w.z, body.mass());
    }
}
