package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;

import java.io.IOException;

/** Coordinates persisted asset reference rewrites and dependency-ordered reloads. */
public final class AssetRenameCoordinator {
    private final RoomTemplateRegistry rooms;
    private final FeatureTemplateRegistry features;
    private final DoorTemplateRegistry doors;

    public AssetRenameCoordinator(RoomTemplateRegistry rooms, FeatureTemplateRegistry features, DoorTemplateRegistry doors) {
        this.rooms = rooms;
        this.features = features;
        this.doors = doors;
    }

    public FeatureTemplate renameFeature(String oldId, String newId) throws IOException {
        FeatureTemplate renamed = features.renameFeature(oldId, newId);
        doors.replaceFeatureReferences(oldId, renamed.id());
        rooms.replaceFeatureReferences(oldId, renamed.id());
        reloadAll();
        return renamed;
    }

    public DoorTemplate renameDoor(String oldId, String newId) throws IOException {
        DoorTemplate renamed = doors.renameDoor(oldId, newId);
        rooms.replaceDoorReferences(oldId, renamed.id());
        reloadAll();
        return renamed;
    }

    public RoomTemplate renameRoom(String oldId, String newId) throws IOException {
        RoomTemplate renamed = rooms.renameRoom(oldId, newId);
        reloadAll();
        return renamed;
    }

    public void reloadAll() {
        features.reload();
        doors.reload();
        rooms.reload();
    }
}
