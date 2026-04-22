package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.option.RubyRenderMode;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class MixinOptions {
    @Inject(method = "processOptions", at = @At("HEAD"))
    private void rubi$onProcessOptions(Options.FieldAccess visitor, CallbackInfo ci) {
        RubyRenderMode.accept(visitor);
    }
}
