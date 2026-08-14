package com.dungeonarchitect.loot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicFileWriterTest {
    @TempDir
    Path directory;

    @Test
    void replacesExistingFileAndCleansTemporaryFile() throws IOException {
        Path target = directory.resolve("table.yml");
        Files.writeString(target, "old");

        AtomicFileWriter.write(target, temporary -> Files.writeString(temporary, "new"));

        assertEquals("new", Files.readString(target));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void failedWritePreservesExistingFileAndCleansTemporaryFile() throws IOException {
        Path target = directory.resolve("table.yml");
        Files.writeString(target, "old");

        assertThrows(IOException.class, () -> AtomicFileWriter.write(target, temporary -> {
            Files.writeString(temporary, "partial");
            throw new IOException("simulated failure");
        }));

        assertEquals("old", Files.readString(target));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
