package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.IntVector3;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface StructureSizeReader {
    IntVector3 loadSize(Path structureFile) throws IOException;
}
