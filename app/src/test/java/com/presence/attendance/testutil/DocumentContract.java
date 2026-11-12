package com.presence.attendance.testutil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Field lists from {@code contracts/documents.json}: the documents the app writes, as the security
 * rules whitelist them. The rules suite builds its app-flow documents from the same file.
 */
public final class DocumentContract {

    private DocumentContract() {}

    /** The required fields of a collection's documents, plus the optional ones if asked for. */
    public static Set<String> fields(String collection, boolean withOptional) throws IOException {
        final Map<String, String> contract =
                Contracts.flatObjects(Contracts.read("documents.json")).get(0);
        final String required = contract.get(collection);
        if (required == null) {
            throw new IllegalArgumentException("No contract for " + collection);
        }
        final Set<String> fields = new TreeSet<>(Arrays.asList(required.split(",")));
        final String optional = contract.get(collection + ".optional");
        if (withOptional && optional != null) {
            fields.addAll(Arrays.asList(optional.split(",")));
        }
        return fields;
    }
}
