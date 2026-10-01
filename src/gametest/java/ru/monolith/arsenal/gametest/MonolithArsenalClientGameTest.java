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
import ru.monolith.arsenal.physics.ScheduledForces;
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
            boolean present = reopened.getServer().computeOnServer(server -> VehiclePhysics.find(server.getOverworld(), id).isPresent());
            check(present, "the physics body must be saved with the world");
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
        long id = server.computeOnServer(s -> VehiclePhysics.backend()
                .createTestBody(s.getOverworld(), new BlockPos(20, -50, 5)).orElseThrow());
        LOGGER.info("[gametest] created Valkyrien Skies test body {}", id);
        server.runCommand("tp @a 14 -59 5 -90 15");
        context.waitTicks(100);

        Vec3d landed = server.computeOnServer(s -> body(s, id).position());
        check(landed.y < -57.0 && landed.y > -60.0, "body must fall and rest on the floor, y=" + landed.y);
        Vec3d clientPos = context.computeOnClient(client -> clientShipPosition(client, id));
        check(clientPos != null && clientPos.distanceTo(landed) < 0.5,
                "client must see the body where the server has it: client " + clientPos + ", server " + landed);
        context.takeScreenshot("08_vs_body_landed");

        double mass = server.computeOnServer(s -> body(s, id).mass());
        server.runOnServer(s -> ScheduledForces.add(s.getOverworld(), id, new Vec3d(mass * 8.0, 0.0, 0.0), Vec3d.ZERO, 20));
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

        server.runOnServer(s -> ScheduledForces.add(s.getOverworld(), id, new Vec3d(0.0, mass * 14.0, 0.0), Vec3d.ZERO, 25));
        context.waitTicks(10);
        server.runOnServer(s -> ScheduledForces.add(s.getOverworld(), id, Vec3d.ZERO, new Vec3d(mass * 5.0, 0.0, mass * 5.0), 8));
        context.waitTicks(12);
        Vec3d spin = server.computeOnServer(s -> body(s, id).angularVelocity());
        check(Math.abs(spin.x) > 0.2 && Math.abs(spin.z) > 0.2, "torque must spin the body around X and Z, angVel=" + spin);
        context.takeScreenshot("10_vs_body_rotating");
        context.waitTicks(120);

        // Stand on the resting hull and drag it: the player must stay on the deck.
        Vec3d rest = server.computeOnServer(s -> body(s, id).position());
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f -90 30", rest.x, rest.y + 2.5, rest.z));
        context.waitTicks(30);
        double startX = context.computeOnClient(client -> client.player.getX());
        server.runOnServer(s -> ScheduledForces.add(s.getOverworld(), id, new Vec3d(mass * 4.0, 0.0, 0.0), Vec3d.ZERO, 20));
        context.waitTicks(50);
        double carried = context.computeOnClient(client -> client.player.getX()) - startX;
        double bodyDx = server.computeOnServer(s -> body(s, id).position().x) - rest.x;
        LOGGER.info("[gametest] body moved {} blocks, player on deck moved {} blocks", bodyDx, carried);
        context.takeScreenshot("11_player_on_moving_body");

        server.runCommand("tp @a 30 -55 -8 30 20");
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
