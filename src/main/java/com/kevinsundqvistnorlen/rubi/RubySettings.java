package com.kevinsundqvistnorlen.rubi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;

public final class RubySettings {
    // Defaults match the original Fabric mod's hardcoded constants.
    public static final float DEFAULT_TEXT_SCALE = 0.8f;
    public static final float DEFAULT_RUBY_SCALE = 0.5f;
    public static final float DEFAULT_RUBY_OVERLAP = 0.1f;
    public static final float DEFAULT_Y_OFFSET = 0.0f;
    public static final int DEFAULT_LINE_HEIGHT_BONUS = 0;

    public static volatile float TEXT_SCALE = DEFAULT_TEXT_SCALE;
    public static volatile float RUBY_SCALE = DEFAULT_RUBY_SCALE;
    public static volatile float RUBY_OVERLAP = DEFAULT_RUBY_OVERLAP;
    // Vertical shift applied to lines with visible furigana when rendered inside a fixed-height
    // container (UI button, chat line, creative tab title, etc.). These can't grow to accommodate
    // the ruby's extra vertical extent, so the default is usually small.
    public static volatile float Y_OFFSET_FURIGANA = DEFAULT_Y_OFFSET;
    // Vertical shift applied to lines with visible furigana when rendered inside a dynamic-height
    // container (item tooltips, hover popups, etc.). These grow around their contents, so the
    // furigana can sit further down without clipping. Falls back to Y_OFFSET_FURIGANA when the
    // JSON doesn't specify it explicitly.
    public static volatile float Y_OFFSET_FURIGANA_DYNAMIC = DEFAULT_Y_OFFSET;
    // Vertical shift applied to lines that render no furigana (no annotations, or all known) in a
    // fixed-height container. Paired with Y_OFFSET_PLAIN_DYNAMIC for tooltip-style hosts.
    public static volatile float Y_OFFSET_PLAIN = DEFAULT_Y_OFFSET;
    // Dynamic-container counterpart to Y_OFFSET_PLAIN. Falls back to Y_OFFSET_PLAIN when the JSON
    // doesn't specify it explicitly.
    public static volatile float Y_OFFSET_PLAIN_DYNAMIC = DEFAULT_Y_OFFSET;
    // Extra vertical space reserved by the local GUI-layout hooks that render wrapped or stacked
    // text. This deliberately does not mutate Font.lineHeight globally because custom widgets and
    // third-party renderers can assume vanilla font metrics.
    public static volatile int LINE_HEIGHT_BONUS = DEFAULT_LINE_HEIGHT_BONUS;

    private static final ResourceLocation SETTINGS_FILE =
        new ResourceLocation(Rubi.MODID, "ruby_settings.json");

    public static void reload(ResourceManager manager) {
        float textScale = DEFAULT_TEXT_SCALE;
        float rubyScale = DEFAULT_RUBY_SCALE;
        float rubyOverlap = DEFAULT_RUBY_OVERLAP;
        float yOffsetFurigana = DEFAULT_Y_OFFSET;
        Float yOffsetFuriganaDynamic = null; // null → fall back to yOffsetFurigana after load
        float yOffsetPlain = DEFAULT_Y_OFFSET;
        Float yOffsetPlainDynamic = null; // null → fall back to yOffsetPlain
        int lineHeightBonus = DEFAULT_LINE_HEIGHT_BONUS;

        var resource = manager.getResource(SETTINGS_FILE);
        if (resource.isPresent()) {
            try (BufferedReader reader = resource.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("text_scale")) textScale = json.get("text_scale").getAsFloat();
                if (json.has("ruby_scale")) rubyScale = json.get("ruby_scale").getAsFloat();
                if (json.has("ruby_overlap")) rubyOverlap = json.get("ruby_overlap").getAsFloat();
                // Back-compat: legacy "y_offset" seeds the furigana offset.
                if (json.has("y_offset")) yOffsetFurigana = json.get("y_offset").getAsFloat();
                if (json.has("y_offset_furigana")) yOffsetFurigana = json.get("y_offset_furigana").getAsFloat();
                if (json.has("y_offset_furigana_dynamic")) {
                    yOffsetFuriganaDynamic = json.get("y_offset_furigana_dynamic").getAsFloat();
                }
                if (json.has("y_offset_plain")) yOffsetPlain = json.get("y_offset_plain").getAsFloat();
                if (json.has("y_offset_plain_dynamic")) {
                    yOffsetPlainDynamic = json.get("y_offset_plain_dynamic").getAsFloat();
                }
                if (json.has("line_height_bonus")) lineHeightBonus = json.get("line_height_bonus").getAsInt();
            } catch (Exception e) {
                Utils.LOGGER.warn("Failed to parse {}, reverting to defaults: {}", SETTINGS_FILE, e.toString());
            }
        }

        TEXT_SCALE = textScale;
        RUBY_SCALE = rubyScale;
        RUBY_OVERLAP = rubyOverlap;
        Y_OFFSET_FURIGANA = yOffsetFurigana;
        Y_OFFSET_FURIGANA_DYNAMIC = yOffsetFuriganaDynamic != null ? yOffsetFuriganaDynamic : yOffsetFurigana;
        Y_OFFSET_PLAIN = yOffsetPlain;
        Y_OFFSET_PLAIN_DYNAMIC = yOffsetPlainDynamic != null ? yOffsetPlainDynamic : yOffsetPlain;
        LINE_HEIGHT_BONUS = lineHeightBonus;

        Utils.LOGGER.info(
            "Ruby layout: text_scale={}, ruby_scale={}, ruby_overlap={}, y_offset_furigana={} (dynamic={}), y_offset_plain={} (dynamic={}), line_height_bonus={}",
            TEXT_SCALE, RUBY_SCALE, RUBY_OVERLAP, Y_OFFSET_FURIGANA, Y_OFFSET_FURIGANA_DYNAMIC, Y_OFFSET_PLAIN, Y_OFFSET_PLAIN_DYNAMIC, LINE_HEIGHT_BONUS
        );
    }
}
