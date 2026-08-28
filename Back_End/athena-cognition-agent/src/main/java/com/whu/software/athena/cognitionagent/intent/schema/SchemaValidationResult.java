package com.whu.software.athena.cognitionagent.intent.schema;

import java.util.List;

/** Machine-readable result of validating one JSON value against a contract. */
public record SchemaValidationResult(boolean valid, List<String> violations) {

    public SchemaValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static SchemaValidationResult pass() {
        return new SchemaValidationResult(true, List.of());
    }

    public static SchemaValidationResult fail(String violation) {
        return new SchemaValidationResult(false, List.of(violation));
    }
}
