package com.presence.attendance.testutil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the shared contract files in {@code contracts/} for parity tests. */
public final class Contracts {

    private static final Pattern FLAT_OBJECT = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern PAIR =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}\\s]+)");

    private Contracts() {}

    /** Contents of a file in the contracts directory, which Gradle passes as a system property. */
    public static String read(String fileName) throws IOException {
        final String dir = System.getProperty("contractsDir");
        if (dir == null) {
            throw new IllegalStateException(
                    "contractsDir is not set; run the tests through Gradle");
        }
        return new String(Files.readAllBytes(Paths.get(dir, fileName)), StandardCharsets.UTF_8);
    }

    /**
     * Every innermost {@code {...}} object in the text as key to raw value, strings unquoted.
     *
     * <p>shortcut: regex reader for flat objects of scalars, which is all the contract files hold.
     * Switch to a JSON library if a contract gains nested values or escaped quotes.
     */
    public static List<Map<String, String>> flatObjects(String json) {
        final List<Map<String, String>> objects = new ArrayList<>();
        final Matcher object = FLAT_OBJECT.matcher(json);
        while (object.find()) {
            final Map<String, String> fields = new LinkedHashMap<>();
            final Matcher pair = PAIR.matcher(object.group());
            while (pair.find()) {
                fields.put(pair.group(1), unquote(pair.group(2)));
            }
            objects.add(fields);
        }
        return objects;
    }

    private static String unquote(String raw) {
        return raw.startsWith("\"") ? raw.substring(1, raw.length() - 1) : raw;
    }
}
