package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.RubySettings;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * {@link MultiLineTextWidget} is used for the accessibility onboarding screen, credits-style
 * labels, and other places that render a stack of wrapped text lines. It hardcodes {@code 9} both
 * as its per-line Y advance ({@code renderWidget} — passed into
 * {@link net.minecraft.client.gui.components.MultiLineLabel#renderCentered}/{@code
 * renderLeftAligned}) and as the per-line height in {@link MultiLineTextWidget#getHeight()} used
 * for layout sizing. Both need to track our line-height bonus so the widget reserves enough
 * vertical space and spaces its lines wide enough for furigana.
 */
@Mixin(MultiLineTextWidget.class)
public abstract class MixinMultiLineTextWidget {
    @ModifyConstant(method = "renderWidget", constant = @Constant(intValue = 9))
    private int rubi$bumpRenderedLineHeight(int original) {
        return original + RubySettings.LINE_HEIGHT_BONUS;
    }

    @ModifyConstant(method = "getHeight", constant = @Constant(intValue = 9))
    private int rubi$bumpMeasuredLineHeight(int original) {
        return original + RubySettings.LINE_HEIGHT_BONUS;
    }
}
