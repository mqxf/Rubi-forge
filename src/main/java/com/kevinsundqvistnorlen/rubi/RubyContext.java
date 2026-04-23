package com.kevinsundqvistnorlen.rubi;

/**
 * Thread-local flag for whether the current text render is happening inside a dynamic-height
 * container (item tooltip, hover popup, …) versus a fixed-height container (UI button, chat line,
 * creative tab title, …). {@link TextDrawer} uses it to pick between
 * {@link RubySettings#Y_OFFSET_FURIGANA} (the fixed-context default) and
 * {@link RubySettings#Y_OFFSET_FURIGANA_DYNAMIC}, so the furigana can sit at a different baseline
 * depending on whether the host container is free to grow vertically.
 *
 * <p>Tracked as a depth counter (not a boolean) so nested tooltip-like contexts still pop
 * correctly. {@link #popDynamic()} clamps at zero so an accidental extra pop can't flip the
 * flag permanently negative.
 */
public final class RubyContext {
    private static final ThreadLocal<Integer> DYNAMIC_DEPTH = ThreadLocal.withInitial(() -> 0);

    private RubyContext() {}

    public static void pushDynamic() {
        DYNAMIC_DEPTH.set(DYNAMIC_DEPTH.get() + 1);
    }

    public static void popDynamic() {
        int d = DYNAMIC_DEPTH.get();
        DYNAMIC_DEPTH.set(d > 0 ? d - 1 : 0);
    }

    public static boolean isDynamic() {
        return DYNAMIC_DEPTH.get() > 0;
    }
}
