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
    public static final char ESCAPED_RUBY_PREFIX = '\uFFF0';
    public static final Pattern RUBY_PATTERN = Pattern.compile("§\\^\\s*(.+?)\\s*\\(\\s*(.+?)\\s*\\)");

    /**
     * One kanji-run-plus-its-reading chunk of a compound annotation, or a kana-run inside the base
     * that matched its own verbatim spelling inside the reading (in which case no furigana is drawn
     * above that run — it's already hiragana/katakana). For pure-kanji or malformed annotations the
     * segment list is a singleton with {@code kanaMatched=false} and the whole word as its base.
     */
    public record Segment(String base, String reading, boolean kanaMatched) {}

    public static String strip(String returnValue) {
        String normalized = normalizeRubyPrefixes(returnValue);
        StringBuilder sb = new StringBuilder(normalized.length());
        boolean replaceMode = RubyRenderMode.getOption().get() == RubyRenderMode.REPLACE;

        var matcher = RUBY_PATTERN.matcher(normalized);
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

    public static String escapeForComponentParsers(String text) {
        if (text == null || text.isEmpty()) return text;
        if (text.indexOf('&') < 0 && text.indexOf('\u00A7') < 0) return text;

        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < text.length() && text.charAt(i + 1) == '^') {
                if (sb == null) {
                    sb = new StringBuilder(text.length());
                    sb.append(text, 0, i);
                }
                sb.append(ESCAPED_RUBY_PREFIX);
                i++;
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? text : sb.toString();
    }

    public static String normalizeRubyPrefixes(String text) {
        if (text == null || text.isEmpty()) return text;
        if (text.indexOf('&') < 0 && text.indexOf(ESCAPED_RUBY_PREFIX) < 0) return text;

        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ESCAPED_RUBY_PREFIX) {
                if (sb == null) {
                    sb = new StringBuilder(text.length() + 8);
                    sb.append(text, 0, i);
                }
                sb.append('\u00A7').append('^');
            } else if (c == '&' && i + 1 < text.length() && text.charAt(i + 1) == '^') {
                if (sb == null) {
                    sb = new StringBuilder(text.length() + 8);
                    sb.append(text, 0, i);
                }
                sb.append('\u00A7').append('^');
                i++;
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? text : sb.toString();
    }

    public static @NotNull RubyText fromFormatted(String word, String ruby, Style style) {
        FormattedCharSequence formattedWord = sink -> StringDecomposer.iterateFormatted(word, 0, style, style, sink);
        FormattedCharSequence formattedRuby = sink -> StringDecomposer.iterateFormatted(ruby, 0, style, style, sink);
        formattedRuby = Utils.transformStyle(formattedRuby, s -> s.withUnderlined(false).withStrikethrough(false));
        List<Segment> segments = computeSegments(word, ruby);
        return new RubyText(formattedWord, formattedRuby, word, ruby, style, segments);
    }

    /**
     * True if {@code text} contains at least one ruby-bearing codepoint that will actually render
     * as furigana with the current settings. Used by the line-height-bonus mixins and the
     * TextDrawer pre-scan to gate the extra vertical space.
     *
     * <p>Returns {@code false} in two cases where no furigana is visible even if ruby markers are
     * present:
     * <ul>
     *   <li>Global render mode is {@link RubyRenderMode#HIDDEN} — every ruby renders as base-only
     *       via {@link #drawHidden}.</li>
     *   <li>Global render mode is {@link RubyRenderMode#OFF} — upstream mixins short-circuit so
     *       ruby markers are never emitted; defensive check only.</li>
     * </ul>
     * Otherwise, returns {@code true} iff some codepoint carries a {@link RubyText} whose
     * (word, reading) pair is not in {@link KnownReadings}.
     */
    public static boolean hasUnknownRuby(FormattedCharSequence text) {
        RubyRenderMode mode = RubyRenderMode.getOption().get();
        if (mode == RubyRenderMode.HIDDEN || mode == RubyRenderMode.OFF) return false;

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
     * True if {@code text} contains at least one ruby-bearing codepoint (known or unknown) that
     * requires custom rendering. Used by {@code MixinFont} to decide whether to take over
     * Font.drawInBatch on this line — plain lines bail out of our pipeline so ModernUI (or
     * vanilla) can render them normally.
     *
     * <p>Unlike {@link #hasUnknownRuby}, known rubies still count here: HIDDEN mode draws them
     * via {@link #drawHidden} which substitutes the U+FFFC marker with the base text, and that
     * substitution still needs our render path. OFF mode returns {@code false} because
     * {@code MixinStringDecomposer} never emits ruby markers in that mode.
     */
    public static boolean hasAnyRuby(FormattedCharSequence text) {
        if (RubyRenderMode.getOption().get() == RubyRenderMode.OFF) return false;

        boolean[] found = {false};
        text.accept((i, s, c) -> {
            if (IRubyStyle.getRuby(s).isPresent()) {
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

    public Bounds getBounds(StringSplitter splitter) {
        var mode = this.effectiveMode();
        return switch (mode) {
            case ABOVE, BELOW -> this.segmentedBounds(splitter);
            case HIDDEN, OFF -> {
                float width = splitter.stringWidth(this.text());
                yield new Bounds(0f, width, width);
            }
            case REPLACE -> {
                float width = splitter.stringWidth(this.ruby());
                yield new Bounds(0f, width, width);
            }
        };
    }

    private float segmentedWidth(StringSplitter splitter) {
        return this.segmentedBounds(splitter).advance();
    }

    private Bounds segmentedBounds(StringSplitter splitter) {
        float advance = 0f;
        float minLeft = 0f;
        float maxRight = 0f;
        for (Segment seg : this.segments) {
            float baseW = splitter.stringWidth(this.segmentBaseFcs(seg)) * RubySettings.TEXT_SCALE;
            float rubyW = seg.kanaMatched()
                ? 0f
                : splitter.stringWidth(this.segmentRubyFcs(seg)) * RubySettings.RUBY_SCALE;
            maxRight = Math.max(maxRight, advance + baseW + Math.max(0f, (rubyW - baseW) / 2f));
            if (rubyW > 0f) {
                minLeft = Math.min(minLeft, advance + Math.min(0f, (baseW - rubyW) / 2f));
            }
            advance += baseW;
        }
        return new Bounds(minLeft, maxRight, maxRight);
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
            textDrawer.drawScaled(baseFcs, xx, yBase, RubySettings.TEXT_SCALE, matrix);
            if (rubyFcs != null) {
                float rubyW = splitter.stringWidth(rubyFcs) * RubySettings.RUBY_SCALE;
                float rubyX = xx + (baseW - rubyW) / 2f;
                textDrawer.drawScaled(rubyFcs, rubyX, yRuby, RubySettings.RUBY_SCALE, matrix);
            }
            xx += baseW;
        }
    }

    private void drawReplace(float x, float y, Matrix4f matrix, TextDrawer textDrawer) {
        textDrawer.draw(this.ruby(), x, y, matrix);
    }

    private void drawHidden(float x, float y, Matrix4f matrix, TextDrawer textDrawer) {
        textDrawer.draw(this.text(), x, y, matrix);
    }

    public record Bounds(float minX, float maxX, float advance) {
        public float visualWidth() {
            return this.maxX - this.minX;
        }
    }

    public static float visualWidth(FormattedCharSequence text, StringSplitter splitter) {
        float[] advance = {0f};
        float[] minLeft = {0f};
        float[] maxRight = {0f};

        text.accept((index, style, codePoint) -> {
            var ruby = IRubyStyle.getRuby(style);
            if (ruby.isPresent()) {
                Bounds bounds = ruby.get().getBounds(splitter);
                minLeft[0] = Math.min(minLeft[0], advance[0] + bounds.minX());
                maxRight[0] = Math.max(maxRight[0], advance[0] + bounds.maxX());
                advance[0] += bounds.advance();
            } else {
                float width = splitter.stringWidth(FormattedCharSequence.codepoint(codePoint, style));
                maxRight[0] = Math.max(maxRight[0], advance[0] + width);
                advance[0] += width;
            }
            return true;
        });

        return maxRight[0] - minLeft[0];
    }
}
