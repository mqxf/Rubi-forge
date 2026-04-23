package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.RubyContext;
import com.kevinsundqvistnorlen.rubi.RubySettings;
import com.kevinsundqvistnorlen.rubi.RubyText;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * {@link GuiGraphics#drawWordWrap} advances the per-line Y with {@code pY += 9}, which javac
 * compiles to an {@code IINC} instruction — the {@code 9} is embedded in the instruction's
 * operand, so {@code @ModifyConstant} (which only scans {@code ICONST}/{@code BIPUSH}/{@code
 * SIPUSH}/{@code LDC}) can't see it. Instead, inject right after each {@code drawString} call
 * and add {@code LINE_HEIGHT_BONUS} to the {@code pY} local — the existing {@code IINC 9}
 * still runs, so net per-line advance becomes {@code 9 + bonus}.
 *
 * <p>Only applies the bonus when the current line has at least one unknown ruby annotation
 * queued for rendering. All-known or no-ruby lines keep the vanilla {@code 9}-pixel advance so
 * wrapped paragraphs don't grow extra whitespace when there's no furigana to make room for.
 */
@Mixin(GuiGraphics.class)
public abstract class MixinGuiGraphics {
    @Inject(
        method = "drawWordWrap",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(" +
                "Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I",
            shift = At.Shift.AFTER
        )
    )
    private void rubi$bumpWrapAdvance(
        Font font, FormattedText text, int x, int y, int width, int color, CallbackInfo ci,
        @Local(ordinal = 1, argsOnly = true) LocalIntRef pY,
        @Local(ordinal = 0) FormattedCharSequence line
    ) {
        int bonus = RubySettings.LINE_HEIGHT_BONUS;
        if (bonus != 0 && RubyText.hasUnknownRuby(line)) {
            pY.set(pY.get() + bonus);
        }
    }

    /**
     * Every tooltip render passes through {@code renderTooltipInternal}. Bracket it with a
     * push/pop of the dynamic-context flag so TextDrawer can pick the dynamic furigana offset
     * while the ruby is being laid out inside a tooltip component. The flag is a depth counter,
     * so nested calls (shouldn't happen in vanilla 1.20.1, but cheap insurance) still pop
     * correctly.
     */
    @Inject(method = "renderTooltipInternal", at = @At("HEAD"))
    private void rubi$enterDynamicContext(
        Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner,
        CallbackInfo ci
    ) {
        RubyContext.pushDynamic();
    }

    @Inject(method = "renderTooltipInternal", at = @At("RETURN"))
    private void rubi$exitDynamicContext(
        Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner,
        CallbackInfo ci
    ) {
        RubyContext.popDynamic();
    }
}
