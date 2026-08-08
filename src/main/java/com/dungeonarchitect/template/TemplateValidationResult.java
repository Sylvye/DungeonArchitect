package com.dungeonarchitect.template;

import java.util.ArrayList;
import java.util.List;

public final class TemplateValidationResult {
    private final List<String> errors = new ArrayList<>();
    private final List<ValidationIssue> issues = new ArrayList<>();

    public void add(String error) {
        errors.add(error);
    }

    public void add(String error, com.dungeonarchitect.domain.IntVector3 localPosition) {
        errors.add(error);
        issues.add(new ValidationIssue(error, localPosition));
    }

    public void addAll(List<String> errors) {
        this.errors.addAll(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public List<ValidationIssue> issues() {
        return List.copyOf(issues);
    }

    public record ValidationIssue(String message, com.dungeonarchitect.domain.IntVector3 localPosition) {
    }
}
