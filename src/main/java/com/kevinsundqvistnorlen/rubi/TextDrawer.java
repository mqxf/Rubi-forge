package com.kevinsundqvistnorlen.rubi;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.joml.Matrix4f;

import java.util.Objects;

@FunctionalInterface
public interface TextDrawer {
    static float draw(
        FormattedCharSequence text, float x, float y, Matrix4f matrix, StringSplitter splitter, int fontHeight,
        TextDrawer textDrawer
    ) {
        // Pick the line-level offset amount:
        //   Y_OFFSET_FURIGANA when the line has at least one *unknown* ruby word to render.
        //   Y_OFFSET_PLAIN    otherwise (no ruby at all, or every ruby word is known).
        // If both offsets are equal we skip the pre-scan entirely.
        // Tooltips and other dynamic-height containers get their own furigana *and* plain offsets,
        // so lines can sit lower inside the grown box without clipping; fixed-height containers
        // (buttons, chat lines, creative tab title, …) use the regular offsets so content doesn't
        // push off the container's top edge.
        boolean dynamic = RubyContext.isDynamic();
        float furiganaOffset = dynamic
            ? RubySettings.Y_OFFSET_FURIGANA_DYNAMIC
            : RubySettings.Y_OFFSET_FURIGANA;
        float plainOffset = dynamic
            ? RubySettings.Y_OFFSET_PLAIN_DYNAMIC
            : RubySettings.Y_OFFSET_PLAIN;
        float offset = furiganaOffset == plainOffset
            ? furiganaOffset
            : (RubyText.hasUnknownRuby(text) ? furiganaOffset : plainOffset);

        // Uniform per-line shift: every glyph on this line draws at the same Y, so ASCII text
        // (e.g. a trailing "+2" stat) lines up with the Japanese on the same line. Lines with
        // different offsets (furigana vs plain) still differ from each other, but within any one
        // line everything sits on the same baseline.
        final float charY = y + offset;
        var xx = new MutableFloat(x);
        StringBuilder plainRun = new StringBuilder();
        Style[] plainStyle = {null};

        Runnable flushPlainRun = () -> {
            if (plainRun.isEmpty() || plainStyle[0] == null) return;

            String runText = plainRun.toString();
            Style runStyle = plainStyle[0];
            FormattedCharSequence run = sink -> StringDecomposer.iterateFormatted(runText, 0, runStyle, runStyle, sink);
            textDrawer.draw(run, xx.floatValue(), charY, matrix);
            xx.add(splitter.stringWidth(run));
            plainRun.setLength(0);
            plainStyle[0] = null;
        };

        text.accept((index, style, codePoint) -> {
            var ruby = IRubyStyle.getRuby(style);
            if (ruby.isPresent()) {
                flushPlainRun.run();
                xx.add(ruby.get().draw(xx.getValue(), charY, matrix, splitter, fontHeight, textDrawer));
                return true;
            }

            if (!Objects.equals(plainStyle[0], style)) {
                flushPlainRun.run();
                plainStyle[0] = style;
            }
            plainRun.appendCodePoint(codePoint);
            return true;
        });
        flushPlainRun.run();
        return xx.getValue();
    }

    void draw(FormattedCharSequence text, float x, float y, Matrix4f matrix);

    default void drawScaled(FormattedCharSequence text, float x, float y, float scale, Matrix4f matrix) {
        this.draw(text, x, y, new Matrix4f(matrix).scaleAround(scale, x, y, 0));
    }

    default void drawSpacedApart(
        FormattedCharSequence text, float x, float y, float scale, float boxWidth, Matrix4f matrix, StringSplitter splitter) {
        float textWidth = splitter.stringWidth(text) * scale;
        if (textWidth <= 0f) return;
        if (Math.abs(boxWidth - textWidth) < 0.01f) {
            this.drawScaled(text, x, y, scale, matrix);
            return;
        }
        float emptySpace = boxWidth - textWidth;

        var xx = new MutableFloat(x);
        text.accept((index, style, codePoint) -> {
            var styledChar = FormattedCharSequence.codepoint(codePoint, style);
            float charWidth = splitter.stringWidth(styledChar) * scale;
            float spaceAround = emptySpace * (charWidth / textWidth);
            xx.add(spaceAround / 2);
            this.drawScaled(styledChar, xx.floatValue(), y, scale, matrix);
            xx.add(charWidth + spaceAround / 2);
            return true;
        });
    }
}
