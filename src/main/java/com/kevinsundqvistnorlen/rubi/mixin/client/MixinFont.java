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
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public abstract class MixinFont implements IRubyFont {
    @Unique private final ThreadLocal<Boolean> recursionGuard = ThreadLocal.withInitial(() -> false);
    // Tracks how much of the current lineHeight is our doing, so we can adjust by the delta when
    // the ruby settings hot-reload instead of stacking the bonus on each reload.
    @Unique private int rubi$appliedLineHeightBonus = 0;
    // Snapshot of the font's original lineHeight (the per-glyph vertical metric) captured before
    // we bump the field. Ruby layout in RubyText uses this as "font height" so the furigana stays
    // anchored to the actual glyph extent even when lineHeight has been inflated for paragraph
    // spacing. Initialised lazily in the constructor injector.
    @Unique private int rubi$baseLineHeight = -1;

    @Mutable @Final @Shadow public int lineHeight;
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

    // Private vanilla method — ModernUI-textmc @Overwrite's only the public drawInBatch variants,
    // leaving drawInternal untouched. Routing per-glyph recursion through this instead of back
    // through drawInBatch bypasses ModernUI entirely, so our ruby Styles don't get stripped by
    // ModernTextRenderer and widths don't drift. Applies only to ruby-bearing lines (see gate
    // below); plain text keeps going through vanilla/ModernUI as normal.
    @Shadow
    private int drawInternal(
        FormattedCharSequence text, float x, float y, int color, boolean dropShadow,
        Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode displayMode,
        int backgroundColor, int packedLightCoords
    ) {
        throw new AssertionError();
    }

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
        // Only take over for lines that actually carry ruby annotations. Plain text bails out
        // here so ModernUI (or vanilla) can render it normally — otherwise our per-glyph recursion
        // would route every label in the game through our custom pipeline, losing ModernUI's
        // SDF rendering and inheriting its width/baseline quirks.
        if (!RubyText.hasAnyRuby(text)) return;
        this.recursionGuard.set(true);

        try {
            x = TextDrawer.draw(
                text, x, y, matrix, this.splitter, this.rubi$baseLineHeight(),
                (t, xx, yy, m) -> this.drawInternal(
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
        // Capture the font's declared lineHeight before we apply the bonus. Ruby layout uses this
        // for its geometric math (scale, overlap, baseline) regardless of how the current lineHeight
        // has been inflated to make room for paragraph spacing.
        if (this.rubi$baseLineHeight < 0) this.rubi$baseLineHeight = this.lineHeight;
        this.rubi$reapplyLineHeightBonus();
    }

    @Override
    public void rubi$reapplyLineHeightBonus() {
        int newBonus = RubySettings.LINE_HEIGHT_BONUS;
        int delta = newBonus - this.rubi$appliedLineHeightBonus;
        if (delta != 0) {
            this.lineHeight += delta;
            this.rubi$appliedLineHeightBonus = newBonus;
        }
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
            // Known limitation: ruby-bearing text in 8x-outline contexts (entity name tags,
            // boss bars, etc.) renders without the outline. ModernUI @Overwrites the public
            // drawInBatch8xOutline and there's no private helper to tunnel through, so we
            // fall back to regular (non-outlined) rendering via drawInternal — still
            // bypassing ModernUI so ruby Styles survive.
            TextDrawer.draw(
                text, x, y, matrix, this.splitter, this.rubi$baseLineHeight(),
                (t, xx, yy, m) -> this.drawInternal(
                    t, xx, yy, color, false, m, buffer, Font.DisplayMode.NORMAL, 0, packedLightCoords
                )
            );
            ci.cancel();
        } finally {
            this.recursionGuard.set(false);
        }
    }
}
