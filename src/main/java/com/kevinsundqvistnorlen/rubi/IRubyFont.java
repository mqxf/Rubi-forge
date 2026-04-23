package com.kevinsundqvistnorlen.rubi;

/**
 * Mixed into {@link net.minecraft.client.gui.Font} to let {@link RubySettings#reload}
 * re-apply the line-height bonus to already-constructed font instances when the ruby
 * settings file is hot-reloaded.
 */
public interface IRubyFont {
    void rubi$reapplyLineHeightBonus();

    /**
     * Returns the {@link net.minecraft.client.gui.Font#lineHeight} the target had before we applied
     * the ruby line-height bonus. Used by {@code MixinFont} when calling into {@code TextDrawer} so
     * that ruby vertical layout stays anchored to the font's true glyph height, not the bumped
     * inter-line advance.
     */
    int rubi$baseLineHeight();
}
