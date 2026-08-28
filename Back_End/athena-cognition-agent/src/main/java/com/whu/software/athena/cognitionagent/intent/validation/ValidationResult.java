package com.whu.software.athena.cognitionagent.intent.validation;

import java.util.List;

public record ValidationResult(List<ValidationIssue> issues) {

    public boolean isValid() {
        return issues == null || issues.isEmpty();
    }

    public ValidationIssue firstIssue() {
        return issues == null || issues.isEmpty() ? null : issues.get(0);
    }
}
