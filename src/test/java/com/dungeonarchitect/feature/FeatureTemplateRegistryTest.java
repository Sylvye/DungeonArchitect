package com.dungeonarchitect.feature;

import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.IntVector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FeatureTemplateRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsValidFeatureAndRejectsMissingStructure() throws Exception {
        Path good = tempDir.resolve("good");
        Files.createDirectories(good);
        Files.writeString(good.resolve("feature.nbt"), "fake");
        FeatureTemplateIO.save(new FeatureTemplate("good", new IntVector3(1, 1, 1), Set.of(), good.resolve("feature.nbt")), good);

        Path bad = tempDir.resolve("bad");
        Files.createDirectories(bad);
        FeatureTemplateIO.save(new FeatureTemplate("bad", new IntVector3(1, 1, 1), Set.of(), bad.resolve("feature.nbt")), bad);

        FeatureTemplateRegistry registry = new FeatureTemplateRegistry(tempDir, null);
        var result = registry.reload();

        assertFalse(result.valid());
        assertTrue(registry.get("good").isPresent());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("missing feature.nbt")));
    }
}
