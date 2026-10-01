package ru.monolith.arsenal.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import ru.monolith.arsenal.MonolithArsenal;
import ru.monolith.arsenal.weapon.AnimatedTestEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

@Environment(EnvType.CLIENT)
public final class AnimatedTestModel extends GeoModel<AnimatedTestEntity> {
    private static final Identifier MODEL = Identifier.of(MonolithArsenal.MOD_ID, "animated_test");
    private static final Identifier TEXTURE = Identifier.of(MonolithArsenal.MOD_ID, "textures/entity/animated_test.png");
    private static final Identifier ANIMATIONS = Identifier.of(MonolithArsenal.MOD_ID, "animated_test");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(AnimatedTestEntity animatable) {
        return ANIMATIONS;
    }
}
