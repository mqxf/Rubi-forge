package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.IRubyStyle;
import com.kevinsundqvistnorlen.rubi.RubyText;
import com.kevinsundqvistnorlen.rubi.option.RubyRenderMode;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StringDecomposer.class)
public abstract class MixinStringDecomposer {
    @ModifyVariable(
        method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;" +
            "Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private static String rubi$normalizeRubyPrefixes(String text) {
        return RubyText.normalizeRubyPrefixes(text);
    }

    @Inject(
        method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;" +
            "Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/ChatFormatting;getByCode(C)Lnet/minecraft/ChatFormatting;"
        ),
        cancellable = true
    )
    private static void onFormattingCode(
        String text, int startIndex, Style startingStyle, Style resetStyle, FormattedCharSink sink,
        CallbackInfoReturnable<Boolean> cir,
        @Local(ordinal = 2) Style style,
        @Local(ordinal = 2) LocalIntRef index,
        @Local(ordinal = 1) char formattingCode
    ) {
        if (formattingCode == '^') {
            var matcher = RubyText.RUBY_PATTERN.matcher(text).region(index.get(), text.length());
            if (matcher.lookingAt()) {
                // OFF mode: emit the base word chars directly with the current style (no ruby marker,
                // no custom style), skip over the (reading) region, and let vanilla Font/Splitter
                // handle the rest via the usual pipeline.
                if (RubyRenderMode.getOption().get() == RubyRenderMode.OFF) {
                    String word = matcher.group(1);
                    int emitStart = matcher.start(1);
                    for (int i = 0; i < word.length(); ) {
                        int cp = word.codePointAt(i);
                        if (!sink.accept(emitStart + i, style, cp)) {
                            cir.setReturnValue(false);
                            return;
                        }
                        i += Character.charCount(cp);
                    }
                    index.set(matcher.end() - 2);
                    return;
                }
                var rubyText = RubyText.fromFormatted(matcher.group(1), matcher.group(2), style);
                var rubyStyle = ((IRubyStyle) style).rubi$withRuby(rubyText);
                if (!sink.accept(index.get(), rubyStyle, '￼')) {
                    cir.setReturnValue(false);
                    return;
                }

                index.set(matcher.end() - 2);
            }
        }
    }
}
