package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.IntVector3;
import org.bukkit.Server;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class RoomStructureService implements StructureSizeReader {
    private final Server server;
    private final Map<Path, Structure> cache = new HashMap<>();

    public RoomStructureService(Server server) {
        this.server = server;
    }

    @Override
    public synchronized IntVector3 loadSize(Path structureFile) throws IOException {
        BlockVector size = loadStructure(structureFile).getSize();
        return new IntVector3(size.getBlockX(), size.getBlockY(), size.getBlockZ());
    }

    public synchronized Structure loadStructure(Path structureFile) throws IOException {
        Path path = structureFile.toAbsolutePath().normalize();
        Structure cached = cache.get(path);
        if (cached != null) {
            return cached;
        }
        Structure structure = server.getStructureManager().loadStructure(path.toFile());
        cache.put(path, structure);
        return structure;
    }

    public synchronized void invalidate(Path structureFile) {
        cache.remove(structureFile.toAbsolutePath().normalize());
    }

    public synchronized void clearCache() {
        cache.clear();
    }

    public Server server() {
        return server;
    }

}
