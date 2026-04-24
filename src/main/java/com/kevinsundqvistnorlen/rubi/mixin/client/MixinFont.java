package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.IRubyFont;
import com.kevinsundqvistnorlen.rubi.RubySettings;
import com.kevinsundqvistnorlen.rubi.RubyText;
import com.kevinsundqvistnorlen.rubi.TextDrawer;
import com.kevinsundqvistnorlen.rubi.option.RubyRenderMode;
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
public abstract class MixinFont implements IRubyFont {
    @Unique private final ThreadLocal<Boolean> recursionGuard = ThreadLocal.withInitial(() -> false);
    // Snapshot of the font's original lineHeight (the per-glyph vertical metric). Ruby layout in
    // RubyText uses this as "font height" so the furigana stays anchored to the actual glyph
    // extent even when surrounding widgets reserve extra local spacing for wrapped text.
    @Unique private int rubi$baseLineHeight = -1;

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
        // OFF mode: skip all custom routing and let vanilla batch-render this call. Safe because
        // MixinStringDecomposer has also short-circuited in OFF and never emits ruby markers.
        if (RubyRenderMode.getOption().get() == RubyRenderMode.OFF) return;
        // Only take over for lines that actually carry ruby annotations. Plain text bails out here
        // so the active renderer (vanilla, ModernUI, ...) continues to handle it unchanged.
        if (!RubyText.hasAnyRuby(text)) return;
        this.recursionGuard.set(true);

        try {
            x = TextDrawer.draw(
                text, x, y, matrix, this.splitter, this.rubi$baseLineHeight(),
                (t, xx, yy, m) -> this.drawInBatch(
                    t, xx, yy, color, dropShadow, m, buffer, displayMode, backgroundColor, packedLightCoords
                )
            );
            cir.setReturnValue((int) Math.ceil(x) + (dropShadow ? 1 : 0));
        } finally {
            this.recursionGuard.set(false);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void rubi$applyInitialLineHeight(CallbackInfo ci) {
        // Capture the font's declared lineHeight once. Local line-spacing hooks operate elsewhere,
        // so this stays the source of truth for ruby geometry.
        if (this.rubi$baseLineHeight < 0) this.rubi$baseLineHeight = this.lineHeight;
    }

    @Override
    public void rubi$reapplyLineHeightBonus() {
        // Intentionally a no-op. line_height_bonus is consumed by local layout hooks rather than
        // by mutating Font.lineHeight, which breaks third-party widget renderers such as EditBox.
    }

    @Override
    public int rubi$baseLineHeight() {
        return this.rubi$baseLineHeight >= 0 ? this.rubi$baseLineHeight : this.lineHeight;
    }

    @Inject(method = "drawInBatch8xOutline", at = @At("HEAD"), cancellable = true)
    private void onDrawInBatch8xOutline(
        FormattedCharSequence text, float x, float y, int color, int outlineColor, Matrix4f matrix,
        MultiBufferSource buffer, int packedLightCoords, CallbackInfo ci
    ) {
        if (this.recursionGuard.get()) return;
        if (RubyRenderMode.getOption().get() == RubyRenderMode.OFF) return;
        if (!RubyText.hasAnyRuby(text)) return;
        this.recursionGuard.set(true);

        try {
            TextDrawer.draw(
                text, x, y, matrix, this.splitter, this.rubi$baseLineHeight(),
                (t, xx, yy, m) -> this.drawInBatch8xOutline(
                    t, xx, yy, color, outlineColor, m, buffer, packedLightCoords
                )
            );
            ci.cancel();
        } finally {
            this.recursionGuard.set(false);
        }
    }
}
