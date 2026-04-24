package com.kevinsundqvistnorlen.rubi.mixin.client;

import com.kevinsundqvistnorlen.rubi.RubyContext;
import com.kevinsundqvistnorlen.rubi.RubySettings;
import com.kevinsundqvistnorlen.rubi.RubyText;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import net.minecraft.util.Mth;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.ui.TextField", remap = false)
public abstract class MixinFTBTextField {
    @Mutable @Shadow(remap = false) private FormattedText[] formattedText;
    @Shadow(remap = false) private Component rawText;
    @Shadow(remap = false) private int minWidth;
    @Shadow(remap = false) private int maxWidth;

    @Inject(method = "resize", at = @At("HEAD"), remap = false)
    private void rubi$rebuildWrappedLines(CallbackInfoReturnable<Object> cir) {
        if (this.rawText == null) return;
        if (com.kevinsundqvistnorlen.rubi.option.RubyRenderMode.getOption().get()
            == com.kevinsundqvistnorlen.rubi.option.RubyRenderMode.OFF) {
            return;
        }

        FormattedText rubyAware = this.rubi$toRubyAwareText(this.rawText);
        if (rubyAware == this.rawText) return;

        int wrapWidth = this.maxWidth > 0 ? this.maxWidth : this.minWidth;
        if (wrapWidth <= 0) return;

        StringSplitter splitter = Minecraft.getInstance().font.getSplitter();
        List<FormattedText> lines = splitter.splitLines(rubyAware, wrapWidth, Style.EMPTY);
        this.formattedText = lines.toArray(FormattedText[]::new);
    }

    @Unique
    private boolean rubi$hasVisibleRuby() {
        if (this.formattedText == null || this.formattedText.length == 0) return false;

        for (FormattedText line : this.formattedText) {
            if (line != null && RubyText.hasUnknownRuby(rubi$toSequence(line))) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private int rubi$lineHeightBonus() {
        return this.rubi$hasVisibleRuby() ? RubySettings.LINE_HEIGHT_BONUS : 0;
    }

    @Unique
    private int rubi$maxVisualWidth() {
        if (this.formattedText == null || this.formattedText.length == 0) return 0;

        var splitter = Minecraft.getInstance().font.getSplitter();
        int max = 0;
        for (FormattedText line : this.formattedText) {
            if (line == null) continue;
            FormattedCharSequence sequence = rubi$toSequence(line);
            if (!RubyText.hasUnknownRuby(sequence)) continue;
            max = Math.max(max, Mth.ceil(RubyText.visualWidth(sequence, splitter)));
        }
        return max;
    }

    @Unique
    private static FormattedCharSequence rubi$toSequence(FormattedText text) {
        return sink -> StringDecomposer.iterateFormatted(text, Style.EMPTY, sink);
    }

    @Unique
    private FormattedText rubi$toRubyAwareText(Component text) {
        List<Component> parts = new ArrayList<>();
        text.visit((style, chunk) -> {
            rubi$appendRubyAwareParts(parts, style, chunk);
            return Optional.empty();
        }, Style.EMPTY);

        if (parts.isEmpty()) return text;

        MutableComponent rebuilt = Component.empty();
        boolean changed = false;
        for (Component part : parts) {
            rebuilt.append(part);
            changed |= "\uFFFC".contentEquals(part.getString());
        }

        return changed ? rebuilt : text;
    }

    @Unique
    private static void rubi$appendRubyAwareParts(List<Component> parts, Style style, String chunk) {
        String normalized = RubyText.normalizeRubyPrefixes(chunk);
        var matcher = RubyText.RUBY_PATTERN.matcher(normalized);
        int cursor = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            if (matcher.start() > cursor) {
                parts.add(Component.literal(normalized.substring(cursor, matcher.start())).setStyle(style));
            }

            RubyText rubyText = RubyText.fromFormatted(matcher.group(1), matcher.group(2), style);
            Style rubyStyle = ((com.kevinsundqvistnorlen.rubi.IRubyStyle) style).rubi$withRuby(rubyText);
            parts.add(Component.literal("\uFFFC").setStyle(rubyStyle));
            cursor = matcher.end();
        }

        if (!found) {
            parts.add(Component.literal(normalized).setStyle(style));
            return;
        }

        if (cursor < normalized.length()) {
            parts.add(Component.literal(normalized.substring(cursor)).setStyle(style));
        }
    }

    @ModifyExpressionValue(
        method = "resize",
        at = @At(
            value = "FIELD",
            target = "Ldev/ftb/mods/ftblibrary/ui/TextField;textSpacing:I",
            opcode = Opcodes.GETFIELD,
            remap = false
        ),
        remap = false
    )
    private int rubi$expandResizeSpacing(int original) {
        return original + this.rubi$lineHeightBonus();
    }

    @ModifyExpressionValue(
        method = "draw",
        at = @At(
            value = "FIELD",
            target = "Ldev/ftb/mods/ftblibrary/ui/TextField;textSpacing:I",
            opcode = Opcodes.GETFIELD,
            remap = false
        ),
        remap = false
    )
    private int rubi$expandDrawSpacing(int original) {
        return original + this.rubi$lineHeightBonus();
    }

    @ModifyExpressionValue(
        method = "resize",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ftb/mods/ftblibrary/ui/Theme;getFontHeight()I",
            remap = false
        ),
        remap = false
    )
    private int rubi$expandResizeFontHeight(int original) {
        return original + this.rubi$lineHeightBonus();
    }

    @ModifyExpressionValue(
        method = "getComponentStyleAt",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ftb/mods/ftblibrary/ui/Theme;getFontHeight()I",
            remap = false
        ),
        remap = false
    )
    private int rubi$expandHitboxLineHeight(int original) {
        return original + this.rubi$lineHeightBonus();
    }

    @ModifyExpressionValue(
        method = "resize",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ftb/mods/ftblibrary/ui/Theme;getStringWidth(Lnet/minecraft/network/chat/FormattedText;)I",
            remap = false
        ),
        remap = false
    )
    private int rubi$expandResizeWidth(int original) {
        return Math.max(original, this.rubi$maxVisualWidth());
    }

    @Inject(method = "draw", at = @At("HEAD"), remap = false)
    private void rubi$enterDynamicContext(CallbackInfo ci) {
        if (this.rubi$hasVisibleRuby()) {
            RubyContext.pushDynamic();
        }
    }

    @Inject(method = "draw", at = @At("RETURN"), remap = false)
    private void rubi$exitDynamicContext(CallbackInfo ci) {
        if (this.rubi$hasVisibleRuby()) {
            RubyContext.popDynamic();
        }
    }
}
