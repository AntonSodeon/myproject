package ru.monolith.arsenal.gametest;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import ru.monolith.arsenal.client.TestWeaponPose;
import ru.monolith.arsenal.physics.BodyCreation;
import ru.monolith.arsenal.physics.VehicleBody;
import ru.monolith.arsenal.physics.VehiclePhysics;
import ru.monolith.arsenal.registry.EntityRegistry;
import ru.monolith.arsenal.registry.ItemRegistry;


/**
 * Drives a real client through the manual checklist: weapon pose (first/third person, re-enabled after slot
 * switches), the GeckoLib test entity, a Valkyrien Skies physics body next to the player, and a world reload.
 */
public final class MonolithArsenalClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("monolith_arsenal_gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.takeScreenshot("01_title_screen");
        TestWorldSave save;
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            save = singleplayer.getWorldSave();
            singleplayer.getClientWorld().waitForChunksRender();
            singleplayer.getServer().runCommand("gamemode creative @a");
            singleplayer.getServer().runCommand("tp @a 0.5 -60 0.5 0 20");
            context.waitTicks(20);

            testWeaponPose(context, singleplayer);
            testAnimatedEntity(context, singleplayer);
            testPhysicsBody(context, singleplayer);
        }

        try (TestSingleplayerContext reopened = save.open()) {
            reopened.getClientWorld().waitForChunksRender();
            context.waitTicks(40);
            long animated = reopened.getServer().computeOnServer(server -> count(server, EntityRegistry.ANIMATED_TEST.getTranslationKey()));
            check(animated == 0, "killed animated_test must not come back after reload, found " + animated);
            long id = bodyId;
            int wait = 0;
            while (!reopened.getServer().computeOnServer(server -> VehiclePhysics.find(server.getOverworld(), id).isPresent()) && wait < 200) {
                context.waitTicks(5);
                wait += 5;
            }
            boolean present = reopened.getServer().computeOnServer(server -> VehiclePhysics.find(server.getOverworld(), id).isPresent());
            check(present, "the physics body must be saved with the world");
            double y0 = reopened.getServer().computeOnServer(server -> body(server, id).position().y);
            context.waitTicks(100);
            double y1 = reopened.getServer().computeOnServer(server -> body(server, id).position().y);
            check(Math.abs(y1 - y0) < 0.5 && y1 > -61.0, "restored body must stay on the ground with a player nearby: y " + y0 + " -> " + y1);
            double m = reopened.getServer().computeOnServer(server -> body(server, id).mass());
            double v0 = reopened.getServer().computeOnServer(server -> body(server, id).linearVelocity().y);
            reopened.getServer().runOnServer(server -> body(server, id).applyImpulse(new Vec3d(0.0, m * 6.0, 0.0)));
            context.waitTicks(2);
            double dv = reopened.getServer().computeOnServer(server -> body(server, id).linearVelocity().y) - v0;
            check(dv > 3.0 && dv < 6.3, "impulse after world reload is applied once: dvy=" + dv);
            context.waitTicks(80);
            context.takeScreenshot("12_after_reload");
            boolean removed = reopened.getServer().computeOnServer(server -> VehiclePhysics.backend().remove(server.getOverworld(), id));
            context.waitTicks(10);
            boolean stillThere = reopened.getServer().computeOnServer(server -> VehiclePhysics.find(server.getOverworld(), id).isPresent());
            check(removed && !stillThere, "the physics body must be removed safely");
            LOGGER.info("[gametest] reload: animated_test={}, body {} restored and removed", animated, id);
        }
        LOGGER.info("[gametest] ALL CHECKS PASSED");
    }

    private static void testWeaponPose(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("item replace entity @a hotbar.0 with monolith_arsenal:test_weapon");
        selectSlot(context, 0);
        context.waitTicks(10);
        check(poseActive(context), "pose must be active with test_weapon in hand");
        context.takeScreenshot("02_weapon_first_person");

        context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
        context.waitTicks(10);
        context.takeScreenshot("03_weapon_third_person");

        for (int cycle = 1; cycle <= 5; cycle++) {
            selectSlot(context, 1);
            context.waitTicks(10);
            check(!poseActive(context), "pose must stop with an empty slot (cycle " + cycle + ")");
            if (cycle == 1) {
                context.takeScreenshot("04_empty_slot_third_person");
            }
            selectSlot(context, 0);
            context.waitTicks(10);
            check(poseActive(context), "pose must re-enable with test_weapon (cycle " + cycle + ")");
            LOGGER.info("[gametest] weapon pose cycle {}: empty -> normal, weapon -> pose OK", cycle);
        }
        context.takeScreenshot("05_weapon_after_5_cycles");
        context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
        selectSlot(context, 1);
    }

    private static void testAnimatedEntity(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("tp @a 0.5 -60 0.5 0 10");
        singleplayer.getServer().runCommand("summon monolith_arsenal:animated_test 0.5 -60 4.5");
        context.waitTicks(10);
        context.takeScreenshot("06_animated_test_a");
        context.waitTicks(20);
        context.takeScreenshot("07_animated_test_b");

        float health = singleplayer.getServer().computeOnServer(server -> {
            Entity entity = first(server, EntityRegistry.ANIMATED_TEST.getTranslationKey());
            return entity instanceof net.minecraft.entity.LivingEntity living ? living.getHealth() : -1.0F;
        });
        singleplayer.getServer().runCommand("damage @e[type=monolith_arsenal:animated_test,limit=1] 2 minecraft:player_attack by @p");
        context.waitTicks(5);
        float damaged = singleplayer.getServer().computeOnServer(server -> {
            Entity entity = first(server, EntityRegistry.ANIMATED_TEST.getTranslationKey());
            return entity instanceof net.minecraft.entity.LivingEntity living ? living.getHealth() : -1.0F;
        });
        check(damaged < health, "animated_test must take damage: " + health + " -> " + damaged);
        LOGGER.info("[gametest] animated_test health {} -> {}", health, damaged);

        singleplayer.getServer().runCommand("kill @e[type=monolith_arsenal:animated_test]");
        context.waitTicks(30);
        long left = singleplayer.getServer().computeOnServer(server -> count(server, EntityRegistry.ANIMATED_TEST.getTranslationKey()));
        check(left == 0, "animated_test must be removed by /kill");
    }

    private static void testPhysicsBody(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        var server = singleplayer.getServer();
        check(server.computeOnServer(s -> VehiclePhysics.backend().isAvailable()), "Valkyrien Skies backend must be available");
        server.runCommand("fill 10 -61 -12 40 -61 22 minecraft:stone");
        server.runCommand("fill 34 -60 -6 34 -52 16 minecraft:obsidian");
        server.runCommand("tp @a 14 -59 5 -90 15");
        context.waitTicks(20);

        BodyCreation creation = server.computeOnServer(s -> VehiclePhysics.backend().createTestBody(s.getOverworld(), new BlockPos(20, -50, 5)));
        long id = creation.id();
        int waited = 0;
        while (server.computeOnServer(s -> creation.state()) == BodyCreation.State.PENDING && waited < 120) {
            context.waitTicks(1);
            waited++;
        }
        check(creation.state() == BodyCreation.State.READY, "creation must become READY, got " + creation.state() + " after " + waited + " ticks");
        // No keep-active here: the player is next to the body, which is how VS normally simulates ships.
        context.waitTicks(100);

        Vec3d landed = server.computeOnServer(s -> body(s, id).position());
        check(landed.y < -57.0 && landed.y > -60.0, "body must fall and rest on the floor, y=" + landed.y);
        Vec3d clientPos = context.computeOnClient(client -> clientShipPosition(client, id));
        check(clientPos != null && clientPos.distanceTo(landed) < 0.5,
                "client must see the body where the server has it: client " + clientPos + ", server " + landed);
        context.takeScreenshot("08_vs_body_landed");

        double mass = server.computeOnServer(s -> body(s, id).mass());
        server.runOnServer(s -> body(s, id).applyForce(new Vec3d(mass * 8.0, 0.0, 0.0), 1.0));
        context.waitTicks(10);
        double vx = server.computeOnServer(s -> body(s, id).linearVelocity().x);
        check(vx > 1.0, "force must accelerate the body next to the player, vx=" + vx);
        context.takeScreenshot("09_vs_body_moving");
        context.waitTicks(40);
        Vec3d moved = server.computeOnServer(s -> body(s, id).position());
        Vec3d clientMoved = context.computeOnClient(client -> clientShipPosition(client, id));
        check(moved.x - landed.x > 1.0, "body must have moved along +X, dx=" + (moved.x - landed.x));
        check(clientMoved != null && clientMoved.distanceTo(moved) < 0.5,
                "client copy must follow the moving body: client " + clientMoved + ", server " + moved);
        context.waitTicks(60);

        server.runOnServer(s -> body(s, id).applyImpulse(new Vec3d(0.0, mass * 9.0, 0.0)));
        context.waitTicks(3);
        server.runOnServer(s -> body(s, id).applyAngularImpulse(new Vec3d(mass * 3.0, 0.0, mass * 3.0)));
        context.waitTicks(8);
        Vec3d spin = server.computeOnServer(s -> body(s, id).angularVelocity());
        check(Math.abs(spin.x) > 0.2 && Math.abs(spin.z) > 0.2, "angular impulse must spin the body around X and Z, angVel=" + spin);
        context.takeScreenshot("10_vs_body_rotating");
        context.waitTicks(160);

        // Stand on the resting hull and push it: the player must stay with the deck.
        Vec3d rest = server.computeOnServer(s -> body(s, id).position());
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f -90 30", rest.x, rest.y + 2.0, rest.z));
        context.waitTicks(40);
        double startX = context.computeOnClient(client -> client.player.getX());
        server.runOnServer(s -> body(s, id).applyForce(new Vec3d(mass * 10.0, 0.0, 0.0), 1.0));
        context.waitTicks(50);
        double carried = context.computeOnClient(client -> client.player.getX()) - startX;
        double bodyDx = server.computeOnServer(s -> body(s, id).position().x) - rest.x;
        LOGGER.info("[gametest] player on deck: body moved {} blocks, player moved {} blocks", bodyDx, carried);
        context.takeScreenshot("11_player_on_moving_body");

        // Leave the area. This VS port keeps the ship in memory but stops simulating it without nearby players
        // (ShipActivationManager); commands sent meanwhile must wait in the queue, not be lost or repeated.
        Vec3d before = server.computeOnServer(s -> body(s, id).position());
        server.runCommand("tp @a 12000 -50 12000");
        for (int i = 0; i < 5; i++) {
            context.waitTicks(20);
            LOGGER.info("[gametest] away t={}s: {}", i + 1, server.computeOnServer(s -> body(s, id).diagnostics()));
        }
        Vec3d away1 = server.computeOnServer(s -> body(s, id).position());
        server.runOnServer(s -> body(s, id).applyImpulse(new Vec3d(0.0, mass * 6.0, 0.0)));
        for (int i = 0; i < 5; i++) {
            context.waitTicks(20);
            LOGGER.info("[gametest] away+impulse t={}s: {}", i + 1, server.computeOnServer(s -> body(s, id).diagnostics()));
        }
        Vec3d away2 = server.computeOnServer(s -> body(s, id).position());
        LOGGER.info("[gametest] player away: body {} -> {} -> {} (impulse queued while away); {}", before, away1, away2,
                server.computeOnServer(s -> body(s, id).diagnostics()));
        check(away2.distanceTo(away1) < 0.01, "without players nearby the body must not be simulated, moved " + away2.distanceTo(away1));

        server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f -90 15", rest.x - 8, rest.y + 1, rest.z));
        int resumeWait = 0;
        double peak = away2.y;
        while (!server.computeOnServer(s -> body(s, id).isSimulated()) && resumeWait < 600) {
            context.waitTicks(1);
            resumeWait++;
            peak = Math.max(peak, server.computeOnServer(s -> body(s, id).position().y));
        }
        LOGGER.info("[gametest] player back: simulation resumed after {} ticks; {}", resumeWait,
                server.computeOnServer(s -> body(s, id).diagnostics()));
        for (int i = 0; i < 4; i++) {
            context.waitTicks(20);
            LOGGER.info("[gametest] back t={}s: y={} {}", i + 1, server.computeOnServer(s -> body(s, id).position().y),
                    server.computeOnServer(s -> body(s, id).diagnostics()));
        }
        check(resumeWait < 600, "simulation must resume after the player returns");
        for (int i = 0; i < 40; i++) {
            context.waitTicks(4);
            peak = Math.max(peak, server.computeOnServer(s -> body(s, id).position().y));
        }
        double rise = peak - away2.y;
        // Characterise: does the body react to a fresh impulse 10 s after the player returned?
        context.waitTicks(200);
        double yA = server.computeOnServer(s -> body(s, id).position().y);
        server.runOnServer(s -> body(s, id).applyImpulse(new Vec3d(0.0, mass * 6.0, 0.0)));
        double peakA = yA;
        for (int i = 0; i < 20; i++) {
            context.waitTicks(2);
            peakA = Math.max(peakA, server.computeOnServer(s -> body(s, id).position().y));
        }
        LOGGER.info("[gametest] probe: fresh impulse 10 s after return lifted the body by {}", peakA - yA);
        server.runCommand("vs set-keep-active @v[id=" + id + "] true");
        context.waitTicks(40);
        double yB = server.computeOnServer(s -> body(s, id).position().y);
        server.runOnServer(s -> body(s, id).applyImpulse(new Vec3d(0.0, mass * 6.0, 0.0)));
        double peakB = yB;
        for (int i = 0; i < 20; i++) {
            context.waitTicks(2);
            peakB = Math.max(peakB, server.computeOnServer(s -> body(s, id).position().y));
        }
        LOGGER.info("[gametest] probe: impulse after keep-active toggle lifted the body by {}", peakB - yB);
        server.runCommand("vs set-keep-active @v[id=" + id + "] false");
        double yBack = server.computeOnServer(s -> body(s, id).position().y);
        LOGGER.info("[gametest] player back: deferred impulse lifted the body by {} blocks, now y={}", rise, yBack);
        check(rise > 1.0 && rise < 3.0, "the impulse queued while away must apply exactly once after return (expected rise ~1.8, got " + rise + ")");
        check(yBack > -61.0, "body must not fall through the ground after the player returns, y=" + yBack);
        double vy0 = server.computeOnServer(s -> body(s, id).linearVelocity().y);
        server.runOnServer(s -> body(s, id).applyImpulse(new Vec3d(0.0, mass * 6.0, 0.0)));
        context.waitTicks(2);
        double dvy = server.computeOnServer(s -> body(s, id).linearVelocity().y) - vy0;
        check(dvy > 3.0 && dvy < 6.3, "exactly one controller after the player returned: impulse 6 m/s gives dvy=" + dvy);
        context.waitTicks(80);

        server.runCommand("tp @a 30 -55 -8 30 20");
        context.runOnClient(client -> client.player.getAbilities().flying = true);
        context.waitTicks(20);
        context.takeScreenshot("11b_vs_body_from_side");
        bodyId = id;
    }

    private static long bodyId;

    private static VehicleBody body(MinecraftServer server, long id) {
        return VehiclePhysics.find(server.getOverworld(), id).orElseThrow(() -> new AssertionError("body " + id + " is not loaded"));
    }

    private static Vec3d clientShipPosition(MinecraftClient client, long id) {
        var ship = ValkyrienSkies.api().getClientShipWorld(client).getAllShips().getById(id);
        if (ship == null) {
            return null;
        }
        var p = ship.getTransform().getPositionInWorld();
        return new Vec3d(p.x(), p.y(), p.z());
    }

    private static void selectSlot(ClientGameTestContext context, int slot) {
        context.runOnClient(client -> client.player.getInventory().setSelectedSlot(slot));
    }

    private static boolean poseActive(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(client.player, TestWeaponPose.LAYER_ID);
            return layer instanceof PlayerAnimationController controller && controller.isActive()
                    && client.player.getMainHandStack().isOf(ItemRegistry.TEST_WEAPON);
        });
    }

    private static long count(MinecraftServer server, String typeKey) {
        long n = 0;
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity.getType().getTranslationKey().equals(typeKey)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static Entity first(MinecraftServer server, String typeKey) {
        for (Entity entity : server.getOverworld().iterateEntities()) {
            if (entity.getType().getTranslationKey().equals(typeKey)) {
                return entity;
            }
        }
        return null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        LOGGER.info("[gametest] OK: {}", message);
    }
}
