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
        // If the sequence contains any ruby-bearing char, shift the whole line uniformly so trailing
        // non-ruby characters stay aligned with the shifted ruby composition.
        if (RubySettings.Y_OFFSET != 0f) {
            boolean[] hasRuby = {false};
            text.accept((i, s, c) -> {
                if (IRubyStyle.getRuby(s).isPresent()) { hasRuby[0] = true; return false; }
                return true;
            });
            if (hasRuby[0]) y += RubySettings.Y_OFFSET;
        }

        final float drawY = y;
        var xx = new MutableFloat(x);
        text.accept((index, style, codePoint) -> {
            xx.add(IRubyStyle
                .getRuby(style)
                .map(rubyText -> rubyText.draw(xx.getValue(), drawY, matrix, splitter, fontHeight, textDrawer))
                .orElseGet(() -> {
                    var styledChar = FormattedCharSequence.codepoint(codePoint, style);
                    textDrawer.draw(styledChar, xx.floatValue(), drawY, matrix);
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
