package com.dungeonarchitect.template;

import java.util.ArrayList;
import java.util.List;

public final class TemplateValidationResult {
    private final List<String> repairs = new ArrayList<>();
    private final List<ValidationIssue> issues = new ArrayList<>();
    private final List<TemplateDiagnostic> diagnostics = new ArrayList<>();

    public void add(String error) {
        diagnostics.add(TemplateDiagnostic.error(error));
    }

    public void add(String error, com.dungeonarchitect.domain.IntVector3 localPosition) {
        diagnostics.add(new TemplateDiagnostic(DiagnosticSeverity.ERROR, null, null, null, null, error, null, localPosition));
        issues.add(new ValidationIssue(error, localPosition));
    }

    public void addAll(List<String> errors) {
        errors.forEach(this::add);
    }

    public void addDiagnostic(TemplateDiagnostic diagnostic) {
        diagnostics.add(diagnostic);
        if (diagnostic.localPosition() != null && diagnostic.severity() == DiagnosticSeverity.ERROR) {
            issues.add(new ValidationIssue(diagnostic.display(), diagnostic.localPosition()));
        }
    }

    public void addDiagnostics(List<TemplateDiagnostic> diagnostics) {
        diagnostics.forEach(this::addDiagnostic);
    }

    public void addRepair(String repair) {
        repairs.add(repair);
    }

    public void addRepairs(List<String> repairs) {
        this.repairs.addAll(repairs);
    }

    public boolean valid() {
        return diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    public List<String> errors() {
        return diagnostics.stream()
            .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR)
            .map(TemplateDiagnostic::display)
            .toList();
    }

    public List<String> warnings() {
        return diagnostics.stream()
            .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.WARNING)
            .map(TemplateDiagnostic::display)
            .toList();
    }

    public List<TemplateDiagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public List<String> repairs() {
        return List.copyOf(repairs);
    }

    public List<ValidationIssue> issues() {
        return List.copyOf(issues);
    }

    public record ValidationIssue(String message, com.dungeonarchitect.domain.IntVector3 localPosition) {
    }
}
