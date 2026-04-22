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
        float furiganaOffset = RubySettings.Y_OFFSET_FURIGANA;
        float plainOffset = RubySettings.Y_OFFSET_PLAIN;
        float offset;
        if (furiganaOffset == plainOffset) {
            offset = furiganaOffset;
        } else {
            boolean[] hasUnknownRuby = {false};
            text.accept((i, s, c) -> {
                var ruby = IRubyStyle.getRuby(s);
                if (ruby.isPresent() && !ruby.get().isKnown()) {
                    hasUnknownRuby[0] = true;
                    return false;
                }
                return true;
            });
            offset = hasUnknownRuby[0] ? furiganaOffset : plainOffset;
        }

        // Per-codepoint Y decision: ASCII-pass-through chars render at the line's original baseline
        // (so English words embedded in Japanese text don't bob up/down with the Japanese). Everything
        // else (kana, kanji, the U+FFFC ruby marker, full-width punctuation) gets the shifted Y so the
        // ruby composition stays aligned with its base text.
        final float origY = y;
        final float shiftedY = y + offset;
        var xx = new MutableFloat(x);
        text.accept((index, style, codePoint) -> {
            final float charY = isAsciiPassThrough(codePoint) ? origY : shiftedY;
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

    /**
     * Code points that should ignore the line-level Y shift. All ASCII (U+0000..U+007F) except
     * `.` and `,` — those two commonly stand in for the Japanese 「。」/「、」 in partially translated
     * strings and should shift with the Japanese text, not stay anchored like the Latin letters.
     */
    private static boolean isAsciiPassThrough(int codePoint) {
        return codePoint < 0x80 && codePoint != '.' && codePoint != ',';
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
