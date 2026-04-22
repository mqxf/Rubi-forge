package com.kevinsundqvistnorlen.rubi.mixin.client;

import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.regex.Pattern;

@Mixin(ChatFormatting.class)
public abstract class MixinChatFormatting {
    // ChatFormatting's static initializer compiles its STRIP_FORMATTING_PATTERN via Pattern.compile().
    // We redirect that call and return a superset pattern that also matches '^' so ruby markup is
    // treated as a formatting code by vanilla strip/visit logic.
    @Redirect(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/regex/Pattern;compile(Ljava/lang/String;)Ljava/util/regex/Pattern;"
        )
    )
    private static Pattern rubi$replaceStripPattern(String original) {
        return Pattern.compile("(?i)§[0-9A-FK-OR^]");
    }
}
