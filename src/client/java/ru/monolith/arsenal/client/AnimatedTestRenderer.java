package ru.monolith.arsenal.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import ru.monolith.arsenal.weapon.AnimatedTestEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Environment(EnvType.CLIENT)
public final class AnimatedTestRenderer extends GeoEntityRenderer<AnimatedTestEntity, LivingEntityRenderState> {
    public AnimatedTestRenderer(EntityRendererFactory.Context context) {
        super(context, new AnimatedTestModel());
        this.shadowRadius = 0.35F;
    }
}
