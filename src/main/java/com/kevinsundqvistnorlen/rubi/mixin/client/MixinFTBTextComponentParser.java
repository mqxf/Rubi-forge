package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.RubyText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.util.TextComponentParser", remap = false)
public abstract class MixinFTBTextComponentParser {
    @ModifyVariable(
        method = "parse0(Ljava/lang/String;Ljava/util/function/Function;)Lnet/minecraft/network/chat/Component;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private static String rubi$escapeRubyPrefixes(String text) {
        return RubyText.escapeForComponentParsers(text);
    }
}
