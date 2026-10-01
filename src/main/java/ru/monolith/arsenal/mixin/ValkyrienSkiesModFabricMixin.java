package ru.monolith.arsenal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.monolith.arsenal.compat.valkyrienskies.ValkyrienSkiesConfigBridge;

/** Runs the VS config bridge right after VS finished its own (failing) config registration. */
@Pseudo
@Mixin(targets = "org.valkyrienskies.mod.fabric.common.ValkyrienSkiesModFabric", remap = false)
public abstract class ValkyrienSkiesModFabricMixin {
    @Inject(method = "onInitialize", at = @At("TAIL"), remap = false)
    private void monolithArsenal$registerVsConfigs(CallbackInfo ci) {
        ValkyrienSkiesConfigBridge.apply();
    }
}
