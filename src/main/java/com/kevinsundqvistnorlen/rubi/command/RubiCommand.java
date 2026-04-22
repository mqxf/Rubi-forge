package com.kevinsundqvistnorlen.rubi.command;

import com.kevinsundqvistnorlen.rubi.KnownReadings;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;
import java.util.Set;

public final class RubiCommand {
    private RubiCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        SuggestionProvider<CommandSourceStack> wordSuggestions = (ctx, builder) ->
            SharedSuggestionProvider.suggest(KnownReadings.knownWords(), builder);

        SuggestionProvider<CommandSourceStack> readingSuggestions = (ctx, builder) -> {
            try {
                String word = WordArgument.get(ctx, "word");
                return SharedSuggestionProvider.suggest(KnownReadings.readingsFor(word), builder);
            } catch (IllegalArgumentException ignored) {
                return builder.buildFuture();
            }
        };

        dispatcher.register(
            Commands.literal("rubi")
                .then(Commands.literal("known")
                    .then(Commands.literal("add")
                        .then(Commands.argument("word", WordArgument.word())
                            .then(Commands.argument("reading", WordArgument.word())
                                .executes(ctx -> {
                                    String word = WordArgument.get(ctx, "word");
                                    String reading = WordArgument.get(ctx, "reading");
                                    boolean changed = KnownReadings.add(word, reading);
                                    feedback(ctx, changed
                                        ? "Added " + word + " (" + reading + ") to known readings."
                                        : word + " (" + reading + ") was already known.", changed);
                                    return changed ? Command.SINGLE_SUCCESS : 0;
                                }))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("word", WordArgument.word())
                            .suggests(wordSuggestions)
                            .executes(ctx -> {
                                String word = WordArgument.get(ctx, "word");
                                boolean changed = KnownReadings.removeAll(word);
                                feedback(ctx, changed
                                    ? "Removed all readings for " + word + "."
                                    : "No known readings for " + word + ".", changed);
                                return changed ? Command.SINGLE_SUCCESS : 0;
                            })
                            .then(Commands.argument("reading", WordArgument.word())
                                .suggests(readingSuggestions)
                                .executes(ctx -> {
                                    String word = WordArgument.get(ctx, "word");
                                    String reading = WordArgument.get(ctx, "reading");
                                    boolean changed = KnownReadings.remove(word, reading);
                                    feedback(ctx, changed
                                        ? "Removed " + word + " (" + reading + ")."
                                        : word + " (" + reading + ") was not in known readings.", changed);
                                    return changed ? Command.SINGLE_SUCCESS : 0;
                                }))))
                    .then(Commands.literal("list")
                        .executes(ctx -> {
                            Map<String, Set<String>> snapshot = KnownReadings.snapshot();
                            CommandSourceStack source = ctx.getSource();
                            if (snapshot.isEmpty()) {
                                source.sendSuccess(() -> Component.literal("No known readings yet. Use /rubi known add <word> <reading>.")
                                    .withStyle(ChatFormatting.GRAY), false);
                                return 0;
                            }
                            int total = KnownReadings.totalReadings();
                            source.sendSuccess(() -> Component.literal("Known readings (" + total
                                + " across " + snapshot.size() + " words):")
                                .withStyle(ChatFormatting.YELLOW), false);
                            snapshot.forEach((word, readings) -> {
                                MutableComponent line = Component.literal("  " + word).withStyle(ChatFormatting.WHITE);
                                line.append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY));
                                line.append(Component.literal(String.join(", ", readings)).withStyle(ChatFormatting.AQUA));
                                source.sendSuccess(() -> line, false);
                            });
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("word", WordArgument.word())
                            .suggests(wordSuggestions)
                            .executes(ctx -> {
                                String word = WordArgument.get(ctx, "word");
                                Set<String> readings = KnownReadings.readingsFor(word);
                                CommandSourceStack source = ctx.getSource();
                                if (readings.isEmpty()) {
                                    source.sendSuccess(() -> Component.literal("No readings stored for " + word + ".")
                                        .withStyle(ChatFormatting.GRAY), false);
                                    return 0;
                                }
                                source.sendSuccess(() -> Component.literal(word + " → " + String.join(", ", readings))
                                    .withStyle(ChatFormatting.WHITE), false);
                                return Command.SINGLE_SUCCESS;
                            })))
                    .then(Commands.literal("reload")
                        .executes(ctx -> {
                            KnownReadings.load();
                            feedback(ctx, "Reloaded " + KnownReadings.totalReadings()
                                + " known readings from disk.", true);
                            return Command.SINGLE_SUCCESS;
                        }))));
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, String message, boolean success) {
        ctx.getSource().sendSuccess(() -> Component.literal(message)
            .withStyle(success ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
    }

    /**
     * Quoted-or-unquoted string argument: like {@link com.mojang.brigadier.arguments.StringArgumentType#string()}
     * but the unquoted form accepts any non-whitespace characters (incl. CJK), not just ASCII identifiers.
     */
    public static final class WordArgument implements ArgumentType<String> {
        private static final SimpleCommandExceptionType EMPTY =
            new SimpleCommandExceptionType(new LiteralMessage("Expected non-empty word"));

        public static WordArgument word() { return new WordArgument(); }

        public static String get(CommandContext<?> ctx, String name) {
            return ctx.getArgument(name, String.class);
        }

        @Override
        public String parse(StringReader reader) throws CommandSyntaxException {
            if (reader.canRead() && (reader.peek() == '"' || reader.peek() == '\'')) {
                return reader.readQuotedString();
            }
            int start = reader.getCursor();
            while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
                reader.skip();
            }
            if (reader.getCursor() == start) {
                throw EMPTY.createWithContext(reader);
            }
            return reader.getString().substring(start, reader.getCursor());
        }
    }
}
