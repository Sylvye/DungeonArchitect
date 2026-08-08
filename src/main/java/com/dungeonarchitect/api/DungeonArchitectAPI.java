package com.dungeonarchitect.api;

import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.runtime.DungeonInstance;
import com.dungeonarchitect.runtime.DungeonRequest;
import com.dungeonarchitect.runtime.RoomInstance;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DungeonArchitectAPI {
    DungeonInstance createDungeon(DungeonRequest request);

    void destroyDungeon(UUID dungeonId);

    Optional<DungeonInstance> getDungeon(UUID dungeonId);

    Optional<DungeonInstance> getDungeon(Player player);

    Optional<RoomInstance> getRoom(Player player);

    Collection<RoomTemplate> getRoomTemplates();
}
