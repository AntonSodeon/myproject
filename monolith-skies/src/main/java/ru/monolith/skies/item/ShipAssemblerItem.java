package ru.monolith.skies.item;

import java.util.Optional;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ru.monolith.skies.ship.ShipAssembler;
import ru.monolith.skies.ship.ShipHolder;

/**
 * Right-click a block to turn the structure it belongs to into a ship.
 * Right-click a ship to place it back into the world (snapped to the nearest quarter turn).
 */
public class ShipAssemblerItem extends Item {
    public ShipAssemblerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }
        if (!(context.getWorld() instanceof ServerWorld world)) {
            return ActionResult.SUCCESS;
        }
        double blockDistance = player.getEyePos().distanceTo(context.getHitPos());
        Optional<ShipAssembler.ShipHit> shipHit = findShip(world, player);
        if (shipHit.isPresent() && shipHit.get().distance() < blockDistance) {
            report(player, ShipAssembler.disassemble(world, shipHit.get().ship()));
            return ActionResult.SUCCESS;
        }
        report(player, ShipAssembler.assemble(world, context.getBlockPos(), ShipAssembler.DEFAULT_MAX_BLOCKS));
        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return ActionResult.SUCCESS;
        }
        Optional<ShipAssembler.ShipHit> shipHit = findShip(serverWorld, player);
        if (shipHit.isEmpty()) {
            return ActionResult.PASS;
        }
        report(player, ShipAssembler.disassemble(serverWorld, shipHit.get().ship()));
        return ActionResult.SUCCESS;
    }

    private static Optional<ShipAssembler.ShipHit> findShip(ServerWorld world, PlayerEntity player) {
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(player.getRotationVec(1.0F).multiply(player.getBlockInteractionRange()));
        return ShipAssembler.raycast(((ShipHolder) world).monolithSkies$getShips(), start, end);
    }

    private static void report(PlayerEntity player, ShipAssembler.Result result) {
        player.sendMessage(Text.translatable(result.messageKey(), result.blocks()), true);
    }
}
