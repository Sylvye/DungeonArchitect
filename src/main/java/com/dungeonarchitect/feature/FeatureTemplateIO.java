package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
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

    private static List<Integer> list(IntVector3 vector) {
        return List.of(vector.x(), vector.y(), vector.z());
    }
}
