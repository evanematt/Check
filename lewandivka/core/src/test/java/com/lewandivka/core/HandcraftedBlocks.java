package com.lewandivka.core;

import com.lewandivka.core.structure.Keys;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The blocks of the furniture mod Handcrafted 3.0.6 with their properties, as the server pack run of the build listed them
 * ({@code tools/data/handcrafted-3.0.6-blocks.txt}). The furniture of the district names these blocks, so a key can be
 * checked here without the game.
 */
public final class HandcraftedBlocks {

    private HandcraftedBlocks() {
    }

    private static final Pattern LINE = Pattern.compile("^props \\{(.*)\\}: (.*)$");
    private static Map<String, Map<String, Set<String>>> blocks;

    /** Block name (without the namespace) to its properties and their values. */
    public static synchronized Map<String, Map<String, Set<String>>> all() {
        if (blocks == null) {
            Map<String, Map<String, Set<String>>> out = new HashMap<>();
            try {
                for (String line : Files.readAllLines(Path.of("..", "tools", "data", "handcrafted-3.0.6-blocks.txt"), StandardCharsets.UTF_8)) {
                    Matcher m = LINE.matcher(line);
                    if (!m.matches()) {
                        continue;
                    }
                    Map<String, Set<String>> props = new HashMap<>();
                    for (String p : m.group(1).split(";")) {
                        if (p.isBlank()) {
                            continue;
                        }
                        int eq = p.indexOf('=');
                        props.put(p.substring(0, eq), new LinkedHashSet<>(List.of(p.substring(eq + 1).split("\\|"))));
                    }
                    for (String name : m.group(2).trim().split("\\s+")) {
                        out.put(name, props);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("the list of the blocks of Handcrafted is missing", e);
            }
            blocks = out;
        }
        return blocks;
    }

    /** The values of a property of a block (name without the namespace), e.g. {@code valuesOf("oak_table", "shape")}. */
    public static Set<String> valuesOf(String block, String property) {
        Map<String, Set<String>> props = all().get(block);
        if (props == null || !props.containsKey(property)) {
            throw new IllegalArgumentException("Handcrafted has no " + block + "[" + property + "]");
        }
        return props.get(property);
    }

    /** What is wrong with a key such as {@code handcrafted:oak_chair[color=red,facing=north]}: nothing when the list is empty. */
    public static List<String> check(String key) {
        List<String> problems = new ArrayList<>();
        String id = Keys.blockId(key);
        if (!id.startsWith("handcrafted:")) {
            problems.add("not a block of Handcrafted: " + key);
            return problems;
        }
        Map<String, Set<String>> props = all().get(id.substring("handcrafted:".length()));
        if (props == null) {
            problems.add("Handcrafted has no block " + id + " (" + key + ")");
            return problems;
        }
        int br = key.indexOf('[');
        if (br >= 0) {
            for (String kv : key.substring(br + 1, key.length() - 1).split(",")) {
                String[] parts = kv.split("=");
                Set<String> values = props.get(parts[0]);
                if (values == null) {
                    problems.add(id + " has no property " + parts[0] + " (" + key + ")");
                } else if (!values.contains(parts[1])) {
                    problems.add(id + " has no value " + parts[1] + " for " + parts[0] + " (" + key + ")");
                }
            }
        }
        return problems;
    }
}
