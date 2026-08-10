package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.IntVector3;

public record TemplateDiagnostic(
    DiagnosticSeverity severity,
    String templateType,
    String templateId,
    String componentType,
    String componentId,
    String message,
    String suggestion,
    IntVector3 localPosition
) {
    public TemplateDiagnostic {
        if (severity == null) {
            severity = DiagnosticSeverity.ERROR;
        }
    }

    public static TemplateDiagnostic error(String message) {
        return new TemplateDiagnostic(DiagnosticSeverity.ERROR, null, null, null, null, message, null, null);
    }

    public static TemplateDiagnostic warning(String message) {
        return new TemplateDiagnostic(DiagnosticSeverity.WARNING, null, null, null, null, message, null, null);
    }

    public String display() {
        StringBuilder builder = new StringBuilder();
        if (templateId != null && !message.startsWith(templateId + ":")) {
            builder.append(templateId).append(": ");
        }
        builder.append(message);
        if (suggestion != null && !suggestion.isBlank()) {
            builder.append(" Fix: ").append(suggestion);
        }
        return builder.toString();
    }
}
