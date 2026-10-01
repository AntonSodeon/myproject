package ru.monolith.skies.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import ru.monolith.skies.ship.ShipBlock;
import ru.monolith.skies.ship.ShipEntity;
import ru.monolith.skies.ship.ShipStructure;

/** Draws every ship block with its block model, rotated around the centre of mass. */
@Environment(EnvType.CLIENT)
public final class ShipEntityRenderer extends EntityRenderer<ShipEntity, ShipRenderState> {
    public ShipEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ShipRenderState createRenderState() {
        return new ShipRenderState();
    }

    @Override
    public void updateRenderState(ShipEntity ship, ShipRenderState state, float tickProgress) {
        super.updateRenderState(ship, state, tickProgress);
        ShipStructure structure = ship.getStructure();
        state.shipYaw = ship.getLerpedShipYaw(tickProgress);
        state.centerOfMass = structure.centerOfMass();
        state.states.clear();
        state.positions.clear();
        if (state.lights.length != structure.size()) {
            state.lights = new int[structure.size()];
        }
        int i = 0;
        for (ShipBlock block : structure.blocks()) {
            state.states.add(block.state());
            state.positions.add(block.pos());
            Vec3d center = ship.shipToWorld(Vec3d.ofCenter(block.pos()));
            state.lights[i++] = WorldRenderer.getLightmapCoordinates(ship.getEntityWorld(), BlockPos.ofFloored(center));
        }
    }

    @Override
    public void render(ShipRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        matrices.push();
        // Ship yaw turns (x, z) into (x cos - z sin, x sin + z cos), which is a negative rotation around +Y.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-state.shipYaw));
        for (int i = 0; i < state.states.size(); i++) {
            BlockState blockState = state.states.get(i);
            if (blockState.getRenderType() != BlockRenderType.MODEL) {
                continue;
            }
            BlockPos pos = state.positions.get(i);
            matrices.push();
            matrices.translate(pos.getX() - state.centerOfMass.x, pos.getY() - state.centerOfMass.y, pos.getZ() - state.centerOfMass.z);
            queue.submitBlock(matrices, blockState, state.lights[i], OverlayTexture.DEFAULT_UV, 0);
            matrices.pop();
        }
        matrices.pop();
        super.render(state, matrices, queue, cameraState);
    }
}
