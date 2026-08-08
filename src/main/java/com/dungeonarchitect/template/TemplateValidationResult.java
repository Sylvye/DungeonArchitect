package com.dungeonarchitect.template;

import java.util.ArrayList;
import java.util.List;

public final class TemplateValidationResult {
    private final List<String> errors = new ArrayList<>();

    public void add(String error) {
        errors.add(error);
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
}
