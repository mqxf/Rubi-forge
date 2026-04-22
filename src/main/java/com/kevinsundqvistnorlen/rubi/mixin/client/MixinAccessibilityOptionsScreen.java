package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.option.RubyRenderMode;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.AccessibilityOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(AccessibilityOptionsScreen.class)
public class MixinAccessibilityOptionsScreen {
    @Inject(method = "options", at = @At("RETURN"), cancellable = true)
    private static void rubi$onOptions(CallbackInfoReturnable<OptionInstance<?>[]> cir) {
        OptionInstance<?>[] options = cir.getReturnValue();
        OptionInstance<?>[] newOptions = Arrays.copyOf(options, options.length + 1);
        newOptions[options.length] = RubyRenderMode.getOption();
        cir.setReturnValue(newOptions);
    }
}
