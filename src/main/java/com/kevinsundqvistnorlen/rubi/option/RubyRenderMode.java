package com.kevinsundqvistnorlen.rubi.option;

import com.kevinsundqvistnorlen.rubi.Utils;
import com.mojang.serialization.Codec;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.util.OptionEnum;

import java.util.Arrays;

public enum RubyRenderMode implements OptionEnum {
    // Append new values only; ordinals are persisted in options.txt via the Codec.INT xmap below.
    HIDDEN("hidden"),
    ABOVE("above"),
    BELOW("below"),
    REPLACE("replace"),
    // OFF is a true-fallback mode: all custom rendering is bypassed and §^word(reading) is stripped
    // to just `word` via the vanilla decomposer path. HIDDEN, by contrast, still goes through the
    // custom font pipeline and just renders the base text — use OFF if you suspect rubi is
    // interfering with a render path and want to A/B test.
    OFF("off");

    private final String key;

    RubyRenderMode(String name) {
        this.key = Option.TRANSLATION_KEY + "." + name;
    }

    public static void accept(Options.FieldAccess visitor) {
        visitor.process("rubi.renderMode", Option.INSTANCE);
    }

    public static OptionInstance<RubyRenderMode> getOption() {
        return Option.INSTANCE;
    }

    public static RubyRenderMode byId(int id) {
        return RubyRenderMode.values()[id];
    }

    @Override
    public int getId() {
        return this.ordinal();
    }

    @Override
    public String getKey() {
        return this.key;
    }

    private static final class Option {
        static final String TRANSLATION_KEY = "options.rubi.renderMode";
        static final OptionInstance<RubyRenderMode> INSTANCE = new OptionInstance<>(
            TRANSLATION_KEY,
            OptionInstance.noTooltip(),
            (caption, value) -> value.getCaption(),
            new OptionInstance.Enum<>(
                Arrays.asList(RubyRenderMode.values()),
                Codec.INT.xmap(RubyRenderMode::byId, RubyRenderMode::getId)
            ),
            RubyRenderMode.ABOVE,
            (value) -> Utils.LOGGER.debug("Ruby display mode changed to {} ({})", value.toString(), value.ordinal())
        );
    }
}
