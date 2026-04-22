package com.kevinsundqvistnorlen.rubi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class KnownReadings {
    private static final String DIR_NAME = "rubi";
    private static final String FILE_NAME = "known_readings.json";
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private static volatile Map<String, Set<String>> KNOWN = Map.of();

    private KnownReadings() {}

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(DIR_NAME).resolve(FILE_NAME);
    }

    public static synchronized void load() {
        Path path = file();
        if (!Files.exists(path)) {
            KNOWN = Map.of();
            Utils.LOGGER.info("Rubi: no known-readings file at {}, starting fresh.", path);
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            JsonElement root = JsonParser.parseReader(reader);
            Map<String, Set<String>> built = new HashMap<>();
            if (root != null && root.isJsonObject()) {
                JsonObject json = root.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    JsonElement value = entry.getValue();
                    Set<String> readings = new LinkedHashSet<>();
                    if (value.isJsonArray()) {
                        for (JsonElement v : value.getAsJsonArray()) {
                            if (v.isJsonPrimitive()) readings.add(v.getAsString());
                        }
                    } else if (value.isJsonPrimitive()) {
                        readings.add(value.getAsString());
                    }
                    if (!readings.isEmpty()) {
                        built.put(entry.getKey(), Set.copyOf(readings));
                    }
                }
            }
            KNOWN = Map.copyOf(built);
            Utils.LOGGER.info("Rubi: loaded {} known word(s) from {}", KNOWN.size(), path);
        } catch (Exception e) {
            Utils.LOGGER.warn("Rubi: failed to read {}, starting fresh: {}", path, e.toString());
            KNOWN = Map.of();
        }
    }

    private static void writeNow() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            JsonObject json = new JsonObject();
            new TreeMap<>(KNOWN).forEach((k, v) -> {
                JsonArray arr = new JsonArray();
                new TreeSet<>(v).forEach(arr::add);
                json.add(k, arr);
            });
            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            Utils.LOGGER.warn("Rubi: failed to write {}: {}", path, e.toString());
        }
    }

    public static synchronized boolean add(String word, String reading) {
        Set<String> existing = KNOWN.get(word);
        if (existing != null && existing.contains(reading)) return false;
        Map<String, Set<String>> next = new HashMap<>(KNOWN.size() + 1);
        for (Map.Entry<String, Set<String>> e : KNOWN.entrySet()) next.put(e.getKey(), e.getValue());
        Set<String> readings = new LinkedHashSet<>(existing == null ? Set.of() : existing);
        readings.add(reading);
        next.put(word, Set.copyOf(readings));
        KNOWN = Collections.unmodifiableMap(next);
        writeNow();
        return true;
    }

    public static synchronized boolean remove(String word, String reading) {
        Set<String> existing = KNOWN.get(word);
        if (existing == null || !existing.contains(reading)) return false;
        Map<String, Set<String>> next = new HashMap<>(KNOWN);
        if (existing.size() == 1) {
            next.remove(word);
        } else {
            Set<String> readings = new LinkedHashSet<>(existing);
            readings.remove(reading);
            next.put(word, Set.copyOf(readings));
        }
        KNOWN = Collections.unmodifiableMap(next);
        writeNow();
        return true;
    }

    public static synchronized boolean removeAll(String word) {
        if (!KNOWN.containsKey(word)) return false;
        Map<String, Set<String>> next = new HashMap<>(KNOWN);
        next.remove(word);
        KNOWN = Collections.unmodifiableMap(next);
        writeNow();
        return true;
    }

    public static synchronized int clear() {
        int count = 0;
        for (Set<String> v : KNOWN.values()) count += v.size();
        KNOWN = Map.of();
        writeNow();
        return count;
    }

    public static boolean isKnown(String word, String reading) {
        Map<String, Set<String>> snapshot = KNOWN;
        if (snapshot.isEmpty()) return false;
        Set<String> readings = snapshot.get(word);
        return readings != null && readings.contains(reading);
    }

    public static boolean isKnown(RubyText rubyText) {
        return isKnown(rubyText.textPlain(), rubyText.rubyPlain());
    }

    public static int wordCount() { return KNOWN.size(); }

    public static int totalReadings() {
        int total = 0;
        for (Set<String> v : KNOWN.values()) total += v.size();
        return total;
    }

    public static Map<String, Set<String>> snapshot() {
        Map<String, Set<String>> sorted = new LinkedHashMap<>();
        new TreeMap<>(KNOWN).forEach((k, v) -> sorted.put(k, new TreeSet<>(v)));
        return Collections.unmodifiableMap(sorted);
    }

    public static Set<String> readingsFor(String word) {
        Set<String> readings = KNOWN.get(word);
        return readings == null ? Set.of() : readings;
    }

    public static Set<String> knownWords() {
        return KNOWN.keySet();
    }
}
