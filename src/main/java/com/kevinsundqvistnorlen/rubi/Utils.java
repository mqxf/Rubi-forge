package com.kevinsundqvistnorlen.rubi;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.UnaryOperator;

public final class Utils {
    public static final Logger LOGGER = LoggerFactory.getLogger("Rubi");

    public static FormattedCharSequence transformStyle(FormattedCharSequence text, UnaryOperator<Style> transformer) {
        return sink -> text.accept(
            (index, style, codePoint) -> sink.accept(index, transformer.apply(style), codePoint)
        );
    }
}
