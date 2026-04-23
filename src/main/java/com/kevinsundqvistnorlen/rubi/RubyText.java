package com.kevinsundqvistnorlen.rubi;

import com.kevinsundqvistnorlen.rubi.option.RubyRenderMode;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record RubyText(
    FormattedCharSequence text,
    FormattedCharSequence ruby,
    String textPlain,
    String rubyPlain,
    Style style,
    List<Segment> segments
) {
    public static final Pattern RUBY_PATTERN = Pattern.compile("§\\^\\s*(.+?)\\s*\\(\\s*(.+?)\\s*\\)");

    /**
     * One kanji-run-plus-its-reading chunk of a compound annotation, or a kana-run inside the base
     * that matched its own verbatim spelling inside the reading (in which case no furigana is drawn
     * above that run — it's already hiragana/katakana). For pure-kanji or malformed annotations the
     * segment list is a singleton with {@code kanaMatched=false} and the whole word as its base.
     */
    public record Segment(String base, String reading, boolean kanaMatched) {}

    public static String strip(String returnValue) {
        StringBuilder sb = new StringBuilder(returnValue.length());
        boolean replaceMode = RubyRenderMode.getOption().get() == RubyRenderMode.REPLACE;

        var matcher = RUBY_PATTERN.matcher(returnValue);
        while (matcher.find()) {
            String word = matcher.group(1);
            String reading = matcher.group(2);
            // Known words always strip to their base, even in REPLACE mode — the reader doesn't need the
            // hint, so let it show as the original kanji form.
            boolean useReading = replaceMode && !KnownReadings.isKnown(word, reading);
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(useReading ? reading : word));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    public static @NotNull RubyText fromFormatted(String word, String ruby, Style style) {
        FormattedCharSequence formattedWord = sink -> StringDecomposer.iterateFormatted(word, 0, style, style, sink);
        FormattedCharSequence formattedRuby = sink -> StringDecomposer.iterateFormatted(ruby, 0, style, style, sink);
        formattedRuby = Utils.transformStyle(formattedRuby, s -> s.withUnderlined(false).withStrikethrough(false));
        List<Segment> segments = computeSegments(word, ruby);
        return new RubyText(formattedWord, formattedRuby, word, ruby, style, segments);
    }

    /**
     * True if {@code text} contains at least one ruby-bearing codepoint whose (word, reading) pair
     * isn't in {@link KnownReadings}. Used by the line-height-bonus mixins to gate the bonus:
     * if a line renders no furigana (either because it carries no ruby at all, or every ruby on
     * it is already known) we keep vanilla spacing for that line.
     */
    public static boolean hasUnknownRuby(FormattedCharSequence text) {
        boolean[] found = {false};
        text.accept((i, s, c) -> {
            var ruby = IRubyStyle.getRuby(s);
            if (ruby.isPresent() && !ruby.get().isKnown()) {
                found[0] = true;
                return false;
            }
            return true;
        });
        return found[0];
    }

    /**
     * Split a compound annotation at its kana boundaries. For {@code §^飲み物(のみもの)} this returns
     * {@code [(飲, の), (み, match), (物, もの)]} — the kana run in the base is kept as-is with no
     * furigana above it, and each kanji run gets the slice of the reading that lives between
     * adjacent kana matches. When the reading can't be aligned (no matching kana substring, trailing
     * reading with no trailing kanji, etc.) we fall back to a single whole-word segment so rendering
     * stays correct even for malformed annotations.
     */
    public static List<Segment> computeSegments(String base, String reading) {
        // Tokenise base into alternating kana/non-kana runs.
        List<int[]> runs = new ArrayList<>();
        int runStart = 0;
        boolean runIsKana = false;
        for (int i = 0; i < base.length(); ) {
            int cp = base.codePointAt(i);
            boolean cpIsKana = isKana(cp);
            if (i == 0) {
                runIsKana = cpIsKana;
            } else if (cpIsKana != runIsKana) {
                runs.add(new int[]{runStart, i, runIsKana ? 1 : 0});
                runStart = i;
                runIsKana = cpIsKana;
            }
            i += Character.charCount(cp);
        }
        if (base.length() > 0) {
            runs.add(new int[]{runStart, base.length(), runIsKana ? 1 : 0});
        }

        // If the base is entirely kana or entirely non-kana there's nothing to split.
        boolean hasKana = false, hasNonKana = false;
        for (int[] r : runs) {
            if (r[2] == 1) hasKana = true;
            else hasNonKana = true;
        }
        if (!hasKana || !hasNonKana) {
            return List.of(new Segment(base, reading, false));
        }

        List<Segment> result = new ArrayList<>();
        int rpos = 0;
        int pendingKanjiIdx = -1;
        for (int[] run : runs) {
            String runText = base.substring(run[0], run[1]);
            boolean isKanaRun = run[2] == 1;
            if (isKanaRun) {
                int idx = reading.indexOf(runText, rpos);
                if (idx < 0) return List.of(new Segment(base, reading, false));
                if (pendingKanjiIdx >= 0) {
                    String chunk = reading.substring(rpos, idx);
                    if (chunk.isEmpty()) return List.of(new Segment(base, reading, false));
                    Segment prev = result.get(pendingKanjiIdx);
                    result.set(pendingKanjiIdx, new Segment(prev.base(), chunk, false));
                    pendingKanjiIdx = -1;
                } else if (idx != rpos) {
                    return List.of(new Segment(base, reading, false));
                }
                result.add(new Segment(runText, runText, true));
                rpos = idx + runText.length();
            } else {
                result.add(new Segment(runText, "", false));
                pendingKanjiIdx = result.size() - 1;
            }
        }
        if (pendingKanjiIdx >= 0) {
            String chunk = reading.substring(rpos);
            if (chunk.isEmpty()) return List.of(new Segment(base, reading, false));
            Segment prev = result.get(pendingKanjiIdx);
            result.set(pendingKanjiIdx, new Segment(prev.base(), chunk, false));
        } else if (rpos < reading.length()) {
            return List.of(new Segment(base, reading, false));
        }

        return result;
    }

    private static boolean isKana(int cp) {
        return (cp >= 0x3041 && cp <= 0x309F)    // Hiragana
            || (cp >= 0x30A0 && cp <= 0x30FF)    // Katakana (incl. ー prolonged sound mark)
            || (cp >= 0x31F0 && cp <= 0x31FF);   // Katakana Phonetic Extensions
    }

    public boolean isKnown() {
        return KnownReadings.isKnown(this);
    }

    public RubyRenderMode effectiveMode() {
        if (this.isKnown()) return RubyRenderMode.HIDDEN;
        RubyRenderMode mode = RubyRenderMode.getOption().get();
        // OFF short-circuits upstream (MixinStringDecomposer) and should never reach here, but if a
        // cached ruby marker slips through, render as HIDDEN (base text only) rather than crashing.
        return mode == RubyRenderMode.OFF ? RubyRenderMode.HIDDEN : mode;
    }

    float draw(
        float x, float y, Matrix4f matrix, StringSplitter splitter, int fontHeight,
        TextDrawer textDrawer
    ) {
        float width = this.getWidth(splitter);

        switch (this.effectiveMode()) {
            case ABOVE -> this.drawAbove(x, y, matrix, splitter, fontHeight, textDrawer);
            case BELOW -> this.drawBelow(x, y, matrix, splitter, fontHeight, textDrawer);
            case REPLACE -> this.drawReplace(x, y, matrix, textDrawer);
            case HIDDEN, OFF -> this.drawHidden(x, y, matrix, textDrawer);
        }

        return width;
    }

    public float getWidth(StringSplitter splitter) {
        var mode = this.effectiveMode();
        return switch (mode) {
            case ABOVE, BELOW -> this.segmentedWidth(splitter);
            case HIDDEN, OFF -> splitter.stringWidth(this.text());
            case REPLACE -> splitter.stringWidth(this.ruby());
        };
    }

    private float segmentedWidth(StringSplitter splitter) {
        float total = 0f;
        for (Segment seg : this.segments) {
            float baseW = splitter.stringWidth(this.segmentBaseFcs(seg)) * RubySettings.TEXT_SCALE;
            float rubyW = seg.kanaMatched()
                ? 0f
                : splitter.stringWidth(this.segmentRubyFcs(seg)) * RubySettings.RUBY_SCALE;
            total += Math.max(baseW, rubyW);
        }
        return total;
    }

    private FormattedCharSequence segmentBaseFcs(Segment seg) {
        final Style s = this.style;
        return sink -> StringDecomposer.iterateFormatted(seg.base(), 0, s, s, sink);
    }

    private FormattedCharSequence segmentRubyFcs(Segment seg) {
        final Style s = this.style;
        FormattedCharSequence raw = sink -> StringDecomposer.iterateFormatted(seg.reading(), 0, s, s, sink);
        return Utils.transformStyle(raw, st -> st.withUnderlined(false).withStrikethrough(false));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || this.getClass() != o.getClass()) return false;
        if (this == o) return true;
        RubyText other = (RubyText) o;
        return Objects.equals(this.text(), other.text()) && Objects.equals(this.ruby(), other.ruby());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.text(), this.ruby());
    }

    private void drawAbove(
        float x, float y, Matrix4f matrix, StringSplitter splitter, int fontHeight, TextDrawer textDrawer
    ) {
        float textHeight = fontHeight * RubySettings.TEXT_SCALE;
        float rubyHeight = fontHeight * RubySettings.RUBY_SCALE;
        float yBody = y + (fontHeight - textHeight);
        float yAbove = yBody - rubyHeight + fontHeight * RubySettings.RUBY_OVERLAP;

        this.drawSegments(x, yBody, yAbove, matrix, splitter, textDrawer);
    }

    private void drawBelow(
        float x, float y, Matrix4f matrix, StringSplitter splitter, int fontHeight, TextDrawer textDrawer
    ) {
        float textHeight = fontHeight * RubySettings.TEXT_SCALE;
        float yBelow = y + textHeight - fontHeight * RubySettings.RUBY_OVERLAP;

        this.drawSegments(x, y, yBelow, matrix, splitter, textDrawer);
    }

    private void drawSegments(
        float x, float yBase, float yRuby, Matrix4f matrix, StringSplitter splitter, TextDrawer textDrawer
    ) {
        float xx = x;
        for (Segment seg : this.segments) {
            FormattedCharSequence baseFcs = this.segmentBaseFcs(seg);
            FormattedCharSequence rubyFcs = seg.kanaMatched() ? null : this.segmentRubyFcs(seg);

            float baseW = splitter.stringWidth(baseFcs) * RubySettings.TEXT_SCALE;
            float rubyW = rubyFcs == null ? 0f : splitter.stringWidth(rubyFcs) * RubySettings.RUBY_SCALE;
            float segWidth = Math.max(baseW, rubyW);

            textDrawer.drawSpacedApart(baseFcs, xx, yBase, RubySettings.TEXT_SCALE, segWidth, matrix, splitter);
            if (rubyFcs != null) {
                textDrawer.drawSpacedApart(rubyFcs, xx, yRuby, RubySettings.RUBY_SCALE, segWidth, matrix, splitter);
            }
            xx += segWidth;
        }
    }

    private void drawReplace(float x, float y, Matrix4f matrix, TextDrawer textDrawer) {
        textDrawer.draw(this.ruby(), x, y, matrix);
    }

    private void drawHidden(float x, float y, Matrix4f matrix, TextDrawer textDrawer) {
        textDrawer.draw(this.text(), x, y, matrix);
    }
}
