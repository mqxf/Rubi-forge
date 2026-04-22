package com.kevinsundqvistnorlen.rubi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;

public final class RubySettings {
    // Defaults match the original Fabric mod's hardcoded constants.
    public static final float DEFAULT_TEXT_SCALE = 0.8f;
    public static final float DEFAULT_RUBY_SCALE = 0.5f;
    public static final float DEFAULT_RUBY_OVERLAP = 0.1f;
    public static final float DEFAULT_Y_OFFSET = 0.0f;

    public static volatile float TEXT_SCALE = DEFAULT_TEXT_SCALE;
    public static volatile float RUBY_SCALE = DEFAULT_RUBY_SCALE;
    public static volatile float RUBY_OVERLAP = DEFAULT_RUBY_OVERLAP;
    public static volatile float Y_OFFSET = DEFAULT_Y_OFFSET;

    private static final ResourceLocation SETTINGS_FILE =
        new ResourceLocation(Rubi.MODID, "ruby_settings.json");

    public static void reload(ResourceManager manager) {
        float textScale = DEFAULT_TEXT_SCALE;
        float rubyScale = DEFAULT_RUBY_SCALE;
        float rubyOverlap = DEFAULT_RUBY_OVERLAP;
        float yOffset = DEFAULT_Y_OFFSET;

        var resource = manager.getResource(SETTINGS_FILE);
        if (resource.isPresent()) {
            try (BufferedReader reader = resource.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("text_scale")) textScale = json.get("text_scale").getAsFloat();
                if (json.has("ruby_scale")) rubyScale = json.get("ruby_scale").getAsFloat();
                if (json.has("ruby_overlap")) rubyOverlap = json.get("ruby_overlap").getAsFloat();
                if (json.has("y_offset")) yOffset = json.get("y_offset").getAsFloat();
            } catch (Exception e) {
                Utils.LOGGER.warn("Failed to parse {}, reverting to defaults: {}", SETTINGS_FILE, e.toString());
            }
        }

        TEXT_SCALE = textScale;
        RUBY_SCALE = rubyScale;
        RUBY_OVERLAP = rubyOverlap;
        Y_OFFSET = yOffset;

        Utils.LOGGER.info(
            "Ruby layout: text_scale={}, ruby_scale={}, ruby_overlap={}, y_offset={}",
            TEXT_SCALE, RUBY_SCALE, RUBY_OVERLAP, Y_OFFSET
        );
    }
}
