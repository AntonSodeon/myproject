package ru.monolith.skies.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class ShipRenderState extends EntityRenderState {
    public final List<BlockState> states = new ArrayList<>();
    public final List<BlockPos> positions = new ArrayList<>();
    public int[] lights = new int[0];
    public Vec3d centerOfMass = Vec3d.ZERO;
    public float shipYaw;
}
