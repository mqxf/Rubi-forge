package com.kevinsundqvistnorlen.rubi;

/**
 * Mixed into {@link net.minecraft.client.gui.Font} to expose the font's original glyph
 * height to the ruby renderer.
 */
public interface IRubyFont {
    void rubi$reapplyLineHeightBonus();

    /**
     * Returns the target's baseline glyph height. Used by {@code MixinFont} when calling into
     * {@code TextDrawer} so ruby vertical layout stays anchored to the font's true metrics rather
     * than any container-specific extra spacing.
     */
    int rubi$baseLineHeight();
}
