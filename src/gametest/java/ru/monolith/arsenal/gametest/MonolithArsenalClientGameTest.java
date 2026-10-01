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
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.monolith.arsenal.client.TestWeaponPose;
import ru.monolith.arsenal.registry.EntityRegistry;
import ru.monolith.arsenal.registry.ItemRegistry;


/**
 * Drives a real client through the manual checklist: weapon pose (first/third person, re-enabled after slot
 * switches), the GeckoLib test entity, a Monolith Skies ship with the player on deck, and a world reload.
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
            testShip(context, singleplayer);
        }

        try (TestSingleplayerContext reopened = save.open()) {
            reopened.getClientWorld().waitForChunksRender();
            context.waitTicks(40);
            long animated = reopened.getServer().computeOnServer(server -> count(server, EntityRegistry.ANIMATED_TEST.getTranslationKey()));
            check(animated == 0, "killed animated_test must not come back after reload, found " + animated);
            long ships = reopened.getServer().computeOnServer(server -> countShips(server));
            check(ships == 1, "the ship must be saved with the world, found " + ships);
            context.takeScreenshot("12_after_reload");
            LOGGER.info("[gametest] reload: animated_test={}, ships={}", animated, ships);
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

    private static void testShip(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        var server = singleplayer.getServer();
        // A pool of water with a wooden raft above it.
        server.runCommand("fill 10 -64 -6 30 -58 14 minecraft:stone");
        server.runCommand("fill 11 -62 -5 29 -58 13 minecraft:water");
        server.runCommand("fill 14 -54 0 18 -54 4 minecraft:oak_planks");
        server.runCommand("fill 14 -53 0 18 -53 0 minecraft:oak_fence");
        server.runCommand("monolith_skies assemble 14 -54 0");
        context.waitTicks(120);

        ShipEntity ship = server.computeOnServer(MonolithArsenalClientGameTest::firstShip);
        check(ship != null, "ship must exist after assembly");
        double submerged = server.computeOnServer(s -> firstShip(s).getSubmergedFraction());
        check(submerged > 0.3 && submerged < 0.9, "wooden ship must float partly submerged, got " + submerged);
        Vec3d shipPos = server.computeOnServer(s -> firstShip(s).getEntityPos());
        LOGGER.info("[gametest] ship floating at {} submerged {}", shipPos, submerged);

        server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f -90 25", shipPos.x, shipPos.y + 2.0, shipPos.z + 1.0));
        context.waitTicks(40);
        double playerY = context.computeOnClient(client -> client.player.getY());
        boolean onGround = context.computeOnClient(client -> client.player.isOnGround());
        check(onGround && playerY > shipPos.y, "player must stand on the deck (y=" + playerY + ", ship y=" + shipPos.y + ")");
        context.takeScreenshot("08_player_on_ship");

        double startX = context.computeOnClient(client -> client.player.getX());
        server.runCommand("monolith_skies push @e[type=monolith_skies:ship] 0.25 0 0");
        context.waitTicks(40);
        double endX = context.computeOnClient(client -> client.player.getX());
        double shipEndX = server.computeOnServer(s -> firstShip(s).getEntityPos().x);
        check(endX - startX > 1.0, "player must be carried by the moving ship (moved " + (endX - startX) + ")");
        LOGGER.info("[gametest] ship moved to x={}, player carried {} blocks", shipEndX, endX - startX);
        context.takeScreenshot("09_ship_moved_with_player");

        server.runCommand("monolith_skies spin @e[type=monolith_skies:ship] 3");
        context.waitTicks(30);
        double yaw = server.computeOnServer(s -> firstShip(s).getShipYaw());
        check(Math.abs(yaw) > 5.0, "ship must rotate, yaw=" + yaw);
        context.takeScreenshot("10_ship_rotated");

        // Hover outside the pool (creative flight) and look back at the rotated ship.
        server.runCommand("tp @a 20 -53 -12 0 30");
        context.runOnClient(client -> {
            client.player.getAbilities().flying = true;
            client.player.setVelocity(0.0, 0.0, 0.0);
        });
        context.waitTicks(20);
        context.takeScreenshot("11_ship_from_side");
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

    private static long countShips(MinecraftServer server) {
        long n = 0;
        for (Entity entity : server.getOverworld().iterateEntities()) {
            if (entity instanceof ShipEntity) {
                n++;
            }
        }
        return n;
    }

    private static ShipEntity firstShip(MinecraftServer server) {
        for (Entity entity : server.getOverworld().iterateEntities()) {
            if (entity instanceof ShipEntity ship) {
                return ship;
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
