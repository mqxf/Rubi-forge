package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.RubySettings;
import com.kevinsundqvistnorlen.rubi.RubyText;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item tooltips (lore, armour trim descriptions, enchantments, ...) render via
 * {@code Screen.renderTooltipInternal}, which advances the per-line Y by
 * {@code ClientTooltipComponent#getHeight()}. For text lines that's {@link ClientTextTooltip}
 * returning a hardcoded {@code 10}. Bumping {@code Font.lineHeight} or {@code
 * GuiGraphics.drawWordWrap}'s advance has no effect on tooltips because this path bypasses both.
 *
 * <p>The same {@code getHeight()} result is used twice: to size the tooltip bounding box
 * ({@code j += getHeight()}) and to advance {@code k1} between the actual text draws. Bumping it
 * keeps the box tall enough for furigana *and* pushes successive lines far enough apart to avoid
 * overlap.
 *
 * <p>We only apply the bonus to lines that actually render furigana (i.e. contain at least one
 * unknown ruby annotation). An item type label like {@code §^戦闘(せんとう)} that the player has
 * marked as known stays at the default {@code 10} so tooltip layout doesn't stretch needlessly.
 */
@Mixin(ClientTextTooltip.class)
public abstract class MixinClientTextTooltip {
    @Final @Shadow private FormattedCharSequence text;

    @Inject(method = "getHeight", at = @At("RETURN"), cancellable = true)
    private void rubi$bumpTooltipLineHeight(CallbackInfoReturnable<Integer> cir) {
        int bonus = RubySettings.LINE_HEIGHT_BONUS;
        if (bonus != 0 && RubyText.hasUnknownRuby(this.text)) {
            cir.setReturnValue(cir.getReturnValueI() + bonus);
        }
    }
}
