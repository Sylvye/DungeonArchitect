package com.dungeonarchitect.template;

import java.nio.file.Path;
import java.util.List;

public record TemplateLoadStatus<T>(
    T template,
    String id,
    Path directory,
    boolean valid,
    List<String> errors,
    List<String> repairs
) {
    public TemplateLoadStatus {
        errors = errors == null ? List.of() : List.copyOf(errors);
        repairs = repairs == null ? List.of() : List.copyOf(repairs);
    }

    public boolean loadable() {
        return template != null;
    }
}
