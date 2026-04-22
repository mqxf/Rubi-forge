package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.IRubyStyle;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StringSplitter.class)
public abstract class MixinStringSplitter {
    @Unique private final ThreadLocal<Boolean> recursionGuard = ThreadLocal.withInitial(() -> false);

    // Forge's Mixin rejects @At("HEAD"/"CTOR_HEAD") on constructors, so we wrap the widthProvider
    // field at RETURN instead. @Mutable is required to reassign the final field.
    @Mutable @Final @Shadow StringSplitter.WidthProvider widthProvider;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onStringSplitterInit(StringSplitter.WidthProvider widthProvider, CallbackInfo ci) {
        this.widthProvider = (codePoint, style) -> IRubyStyle
            .getRuby(style)
            .map(rubyText -> rubyText.getWidth((StringSplitter) (Object) this))
            .orElseGet(() -> widthProvider.getWidth(codePoint, style));
    }

    @Shadow
    public abstract float stringWidth(FormattedCharSequence text);

    @Inject(method = "stringWidth(Ljava/lang/String;)F", at = @At("HEAD"), cancellable = true)
    private void onStringWidthFromString(String text, CallbackInfoReturnable<Float> cir) {
        this.onStringWidthFromSequence(
            (FormattedCharSequence) (sink -> StringDecomposer.iterateFormatted(text, Style.EMPTY, sink)), cir
        );
    }

    @Inject(
        method = "stringWidth(Lnet/minecraft/network/chat/FormattedText;)F",
        at = @At("HEAD"), cancellable = true
    )
    private void onStringWidthFromFormattedText(FormattedText text, CallbackInfoReturnable<Float> cir) {
        this.onStringWidthFromSequence(
            (FormattedCharSequence) (sink -> StringDecomposer.iterateFormatted(text, Style.EMPTY, sink)), cir
        );
    }

    @Inject(
        method = "stringWidth(Lnet/minecraft/util/FormattedCharSequence;)F",
        at = @At("HEAD"), cancellable = true
    )
    private void onStringWidthFromSequence(FormattedCharSequence text, CallbackInfoReturnable<Float> cir) {
        if (this.recursionGuard.get()) return;
        this.recursionGuard.set(true);

        try {
            var width = new MutableFloat();
            text.accept((index, style, codePoint) -> {
                width.add(
                    IRubyStyle
                        .getRuby(style)
                        .map(ruby -> ruby.getWidth((StringSplitter) (Object) this))
                        .orElseGet(() -> this.stringWidth(FormattedCharSequence.codepoint(codePoint, style)))
                );
                return true;
            });
            cir.setReturnValue(width.floatValue());
        } finally {
            this.recursionGuard.set(false);
        }
    }
}
