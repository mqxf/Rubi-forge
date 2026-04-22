package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.TextDrawer;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.joml.Math;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public abstract class MixinFont {
    @Unique private final ThreadLocal<Boolean> recursionGuard = ThreadLocal.withInitial(() -> false);

    @Final @Shadow public int lineHeight;
    @Final @Shadow private StringSplitter splitter;

    @Shadow
    public abstract int drawInBatch(
        FormattedCharSequence text, float x, float y, int color, boolean dropShadow, Matrix4f matrix,
        MultiBufferSource buffer, Font.DisplayMode displayMode, int backgroundColor, int packedLightCoords
    );

    @Shadow
    public abstract void drawInBatch8xOutline(
        FormattedCharSequence text, float x, float y, int color, int outlineColor, Matrix4f matrix,
        MultiBufferSource buffer, int packedLightCoords
    );

    @Inject(
        method =
            "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;" +
                "Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I",
        at = @At("HEAD"), cancellable = true
    )
    private void onDrawString(
        String text, float x, float y, int color, boolean dropShadow, Matrix4f matrix,
        MultiBufferSource buffer, Font.DisplayMode displayMode, int backgroundColor, int packedLightCoords,
        boolean bidirectional, CallbackInfoReturnable<Integer> cir
    ) {
        this.onDrawSequence(
            (FormattedCharSequence) (sink -> StringDecomposer.iterateFormatted(text, Style.EMPTY, sink)),
            x, y, color, dropShadow, matrix, buffer, displayMode, backgroundColor, packedLightCoords, cir
        );
    }

    @Inject(
        method = "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;" +
            "Lnet/minecraft/client/renderer/MultiBufferSource;" +
            "Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
        at = @At("HEAD"), cancellable = true
    )
    private void onDrawSequence(
        FormattedCharSequence text, float x, float y, int color, boolean dropShadow, Matrix4f matrix,
        MultiBufferSource buffer, Font.DisplayMode displayMode, int backgroundColor, int packedLightCoords,
        CallbackInfoReturnable<Integer> cir
    ) {
        if (this.recursionGuard.get()) return;
        this.recursionGuard.set(true);

        try {
            x = TextDrawer.draw(
                text, x, y, matrix, this.splitter, this.lineHeight,
                (t, xx, yy, m) -> this.drawInBatch(
                    t, xx, yy, color, dropShadow, m, buffer, displayMode, backgroundColor, packedLightCoords
                )
            );
            cir.setReturnValue((int) Math.ceil(x) + (dropShadow ? 1 : 0));
        } finally {
            this.recursionGuard.set(false);
        }
    }

    @Inject(method = "drawInBatch8xOutline", at = @At("HEAD"), cancellable = true)
    private void onDrawInBatch8xOutline(
        FormattedCharSequence text, float x, float y, int color, int outlineColor, Matrix4f matrix,
        MultiBufferSource buffer, int packedLightCoords, CallbackInfo ci
    ) {
        if (this.recursionGuard.get()) return;
        this.recursionGuard.set(true);

        try {
            TextDrawer.draw(
                text, x, y, matrix, this.splitter, this.lineHeight,
                (t, xx, yy, m) -> this.drawInBatch8xOutline(t, xx, yy, color, outlineColor, m, buffer, packedLightCoords)
            );
            ci.cancel();
        } finally {
            this.recursionGuard.set(false);
        }
    }
}
