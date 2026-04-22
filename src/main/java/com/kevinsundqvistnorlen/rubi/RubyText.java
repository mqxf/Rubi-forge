package com.kevinsundqvistnorlen.rubi;

import com.kevinsundqvistnorlen.rubi.option.RubyRenderMode;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.Objects;
import java.util.regex.Pattern;

public record RubyText(FormattedCharSequence text, FormattedCharSequence ruby) {
    public static final Pattern RUBY_PATTERN = Pattern.compile("§\\^\\s*(.+?)\\s*\\(\\s*(.+?)\\s*\\)");

    public static String strip(String returnValue) {
        StringBuilder sb = new StringBuilder(returnValue.length());

        var matcher = RUBY_PATTERN.matcher(returnValue);
        while (matcher.find()) {
            if (RubyRenderMode.getOption().get() == RubyRenderMode.REPLACE) {
                matcher.appendReplacement(sb, matcher.group(2));
            } else {
                matcher.appendReplacement(sb, matcher.group(1));
            }
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    public static @NotNull RubyText fromFormatted(String word, String ruby, Style style) {
        FormattedCharSequence formattedWord = sink -> StringDecomposer.iterateFormatted(word, 0, style, style, sink);
        FormattedCharSequence formattedRuby = sink -> StringDecomposer.iterateFormatted(ruby, 0, style, style, sink);
        formattedRuby = Utils.transformStyle(formattedRuby, s -> s.withUnderlined(false).withStrikethrough(false));
        return new RubyText(formattedWord, formattedRuby);
    }

    float draw(
        float x, float y, Matrix4f matrix, StringSplitter splitter, int fontHeight,
        TextDrawer textDrawer
    ) {
        float width = this.getWidth(splitter);

        switch (RubyRenderMode.getOption().get()) {
            case ABOVE -> this.drawAbove(x, y, width, matrix, splitter, fontHeight, textDrawer);
            case BELOW -> this.drawBelow(x, y, width, matrix, splitter, fontHeight, textDrawer);
            case REPLACE -> this.drawReplace(x, y, matrix, textDrawer);
            case HIDDEN -> this.drawHidden(x, y, matrix, textDrawer);
        }

        return width;
    }

    public float getWidth(StringSplitter splitter) {
        var mode = RubyRenderMode.getOption().get();
        float baseWidth = 0f, rubyWidth = 0f;

        if (mode != RubyRenderMode.REPLACE) {
            baseWidth += splitter.stringWidth(this.text());
        }

        if (mode != RubyRenderMode.HIDDEN) {
            rubyWidth += splitter.stringWidth(this.ruby());
        }

        return switch (mode) {
            case ABOVE, BELOW -> Math.max(baseWidth * RubySettings.TEXT_SCALE, rubyWidth * RubySettings.RUBY_SCALE);
            case HIDDEN -> baseWidth;
            case REPLACE -> rubyWidth;
        };
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

    private void drawRubyPair(
        float x, float yText, float yRuby, float width, TextDrawer textDrawer,
        StringSplitter splitter, Matrix4f matrix
    ) {
        textDrawer.drawSpacedApart(this.text(), x, yText, RubySettings.TEXT_SCALE, width, matrix, splitter);
        textDrawer.drawSpacedApart(this.ruby(), x, yRuby, RubySettings.RUBY_SCALE, width, matrix, splitter);
    }

    private void drawAbove(
        float x, float y, float width, Matrix4f matrix, StringSplitter splitter, int fontHeight,
        TextDrawer textDrawer
    ) {
        float textHeight = fontHeight * RubySettings.TEXT_SCALE;
        float rubyHeight = fontHeight * RubySettings.RUBY_SCALE;

        float yBody = y + (fontHeight - textHeight);
        float yAbove = yBody - rubyHeight + fontHeight * RubySettings.RUBY_OVERLAP;

        this.drawRubyPair(x, yBody, yAbove, width, textDrawer, splitter, matrix);
    }

    private void drawBelow(
        float x, float y, float width, Matrix4f matrix, StringSplitter splitter, int fontHeight,
        TextDrawer textDrawer
    ) {
        float textHeight = fontHeight * RubySettings.TEXT_SCALE;
        float yBelow = y + textHeight - fontHeight * RubySettings.RUBY_OVERLAP;

        this.drawRubyPair(x, y, yBelow, width, textDrawer, splitter, matrix);
    }

    private void drawReplace(float x, float y, Matrix4f matrix, TextDrawer textDrawer) {
        textDrawer.draw(this.ruby(), x, y, matrix);
    }

    private void drawHidden(float x, float y, Matrix4f matrix, TextDrawer textDrawer) {
        textDrawer.draw(this.text(), x, y, matrix);
    }
}
