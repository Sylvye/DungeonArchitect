package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.IntVector3;
import org.bukkit.Server;
import org.bukkit.util.BlockVector;

import java.io.IOException;
import java.nio.file.Path;

public final class RoomStructureService {
    private final Server server;

    public RoomStructureService(Server server) {
        this.server = server;
    }

    public IntVector3 loadSize(Path structureFile) throws IOException {
        BlockVector size = server.getStructureManager().loadStructure(structureFile.toFile()).getSize();
        return new IntVector3(size.getBlockX(), size.getBlockY(), size.getBlockZ());
    }

    public Server server() {
        return server;
    }
}
