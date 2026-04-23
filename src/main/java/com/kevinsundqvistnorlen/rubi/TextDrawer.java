package com.kevinsundqvistnorlen.rubi;

import net.minecraft.client.StringSplitter;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.joml.Matrix4f;

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
        text.accept((index, style, codePoint) -> {
            xx.add(IRubyStyle
                .getRuby(style)
                .map(rubyText -> rubyText.draw(xx.getValue(), charY, matrix, splitter, fontHeight, textDrawer))
                .orElseGet(() -> {
                    var styledChar = FormattedCharSequence.codepoint(codePoint, style);
                    textDrawer.draw(styledChar, xx.floatValue(), charY, matrix);
                    return splitter.stringWidth(styledChar);
                }));
            return true;
        });
        return xx.getValue();
    }

    void draw(FormattedCharSequence text, float x, float y, Matrix4f matrix);

    default void drawScaled(FormattedCharSequence text, float x, float y, float scale, Matrix4f matrix) {
        this.draw(text, x, y, new Matrix4f(matrix).scaleAround(scale, x, y, 0));
    }

    default void drawSpacedApart(
        FormattedCharSequence text, float x, float y, float scale, float boxWidth, Matrix4f matrix, StringSplitter splitter) {
        float textWidth = splitter.stringWidth(text) * scale;
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
