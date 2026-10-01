package ru.monolith.arsenal.client;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerRawAnimationBuilder;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.util.Identifier;
import ru.monolith.arsenal.MonolithArsenal;
import ru.monolith.arsenal.registry.ItemRegistry;

@Environment(EnvType.CLIENT)
public final class TestWeaponPose {
    public static final Identifier LAYER_ID = Identifier.of(MonolithArsenal.MOD_ID, "test_weapon_pose");
    private static final Identifier ANIMATION_ID = Identifier.of(MonolithArsenal.MOD_ID, "test_weapon_pose");

    private TestWeaponPose() {
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1000, TestWeaponPose::createController);
    }

    // One controller per avatar; the pose is re-armed each time the weapon returns to the main hand.
    private static PlayerAnimationController createController(PlayerLikeEntity avatar) {
        RawAnimation pose = PlayerRawAnimationBuilder.begin().thenLoop(ANIMATION_ID).build();
        return new PlayerAnimationController(avatar, (controller, state, animationSetter) -> {
            if (isTestWeaponInMainHand(avatar)) {
                if (!controller.isActive()) {
                    controller.forceAnimationReset();
                }
                return animationSetter.setAnimation(pose);
            }
            return PlayState.STOP;
        });
    }

    private static boolean isTestWeaponInMainHand(PlayerLikeEntity avatar) {
        return !avatar.isRemoved() && avatar.getMainHandStack().isOf(ItemRegistry.TEST_WEAPON);
    }
}
