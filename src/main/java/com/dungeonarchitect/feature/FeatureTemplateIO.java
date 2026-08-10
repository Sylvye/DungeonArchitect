package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.template.StructureSizeReader;
import com.dungeonarchitect.template.TemplateLoadStatus;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class FeatureTemplateIO {
    private FeatureTemplateIO() {
    }

    public static FeatureTemplate load(Path featureDirectory) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(featureDirectory.resolve("feature.yml").toFile());
        String id = yaml.getString("id", featureDirectory.getFileName().toString());
        IntVector3 size = vector(yaml.getIntegerList("size"));
        Set<String> tags = Set.copyOf(yaml.getStringList("tags"));
        return new FeatureTemplate(id, size, tags, featureDirectory.resolve("feature.nbt"));
    }

    public static TemplateLoadStatus<FeatureTemplate> loadRecovering(Path featureDirectory, StructureSizeReader sizeReader) {
        List<String> repairs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String id = featureDirectory.getFileName().toString();
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(featureDirectory.resolve("feature.yml").toFile());
            String configuredId = yaml.getString("id");
            if (configuredId == null || configuredId.isBlank()) {
                repairs.add(id + ": repaired missing feature id from directory name");
            } else {
                id = configuredId;
            }
            IntVector3 size = size(yaml, featureDirectory.resolve("feature.nbt"), sizeReader, id, "feature", repairs);
            Set<String> tags = Set.copyOf(yaml.getStringList("tags"));
            FeatureTemplate template = new FeatureTemplate(id, size, tags, featureDirectory.resolve("feature.nbt"));
            return new TemplateLoadStatus<>(template, template.id(), featureDirectory, true, errors, repairs);
        } catch (RuntimeException ex) {
            errors.add(id + ": failed to load feature metadata: " + ex.getMessage());
            return new TemplateLoadStatus<>(null, id, featureDirectory, false, errors, repairs);
        }
    }

    public static void save(FeatureTemplate template, Path featureDirectory) throws IOException {
        Files.createDirectories(featureDirectory);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", template.id());
        yaml.set("size", list(template.size()));
        yaml.set("tags", new ArrayList<>(template.tags()));
        yaml.save(featureDirectory.resolve("feature.yml").toFile());
    }

    private static IntVector3 vector(List<Integer> values) {
        if (values.size() != 3) {
            throw new IllegalArgumentException("Expected vector with 3 integers, got " + values);
        }
        return new IntVector3(values.get(0), values.get(1), values.get(2));
    }

    private static IntVector3 size(YamlConfiguration yaml, Path structureFile, StructureSizeReader sizeReader, String id, String type, List<String> repairs) {
        try {
            if (yaml.isList("size")) {
                return vector(yaml.getIntegerList("size"));
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to structure-derived repair.
        }
        if (sizeReader == null) {
            throw new IllegalArgumentException("Expected vector with 3 integers, got " + yaml.getIntegerList("size"));
        }
        try {
            IntVector3 repaired = sizeReader.loadSize(structureFile);
            repairs.add(id + ": repaired missing or malformed " + type + ".yml size from " + type + ".nbt size " + repaired);
            return repaired;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to repair missing or malformed size from " + type + ".nbt: " + ex.getMessage(), ex);
        }
    }

    private static List<Integer> list(IntVector3 vector) {
        return List.of(vector.x(), vector.y(), vector.z());
    }
}
