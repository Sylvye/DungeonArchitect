package com.dungeonarchitect.api;

import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.runtime.DungeonInstance;
import com.dungeonarchitect.runtime.DungeonManager;
import com.dungeonarchitect.runtime.DungeonRequest;
import com.dungeonarchitect.runtime.RoomInstance;
import com.dungeonarchitect.template.RoomTemplateRegistry;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public final class DungeonArchitectService implements DungeonArchitectAPI {
    private final DungeonManager dungeonManager;
    private final RoomTemplateRegistry roomTemplateRegistry;

    public DungeonArchitectService(DungeonManager dungeonManager, RoomTemplateRegistry roomTemplateRegistry) {
        this.dungeonManager = dungeonManager;
        this.roomTemplateRegistry = roomTemplateRegistry;
    }

    @Override
    public DungeonInstance createDungeon(DungeonRequest request) {
        return dungeonManager.createDungeon(request);
    }

    @Override
    public void destroyDungeon(UUID dungeonId) {
        dungeonManager.destroyDungeon(dungeonId);
    }

    @Override
    public Optional<DungeonInstance> getDungeon(UUID dungeonId) {
        return dungeonManager.getDungeon(dungeonId);
    }

    @Override
    public Optional<DungeonInstance> getDungeon(Player player) {
        return dungeonManager.getDungeon(player);
    }

    @Override
    public Optional<RoomInstance> getRoom(Player player) {
        return dungeonManager.getRoom(player);
    }

    @Override
    public Collection<RoomTemplate> getRoomTemplates() {
        return roomTemplateRegistry.all();
    }
}
