package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.RubyText;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EditBox.class)
public abstract class MixinEditBox {
    @Unique
    private static String rubi$stripAnnotations(String text) {
        return text == null ? null : RubyText.strip(text);
    }

    @ModifyVariable(method = "setValue", at = @At("HEAD"), argsOnly = true)
    private String rubi$stripValue(String text) {
        return rubi$stripAnnotations(text);
    }

    @ModifyVariable(method = "insertText", at = @At("HEAD"), argsOnly = true)
    private String rubi$stripInsertedText(String text) {
        return rubi$stripAnnotations(text);
    }

    @ModifyVariable(method = "setSuggestion", at = @At("HEAD"), argsOnly = true)
    private String rubi$stripSuggestion(String text) {
        return rubi$stripAnnotations(text);
    }
}
