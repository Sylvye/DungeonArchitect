package com.dungeonarchitect.template;

import com.dungeonarchitect.domain.DoorTemplate;
import com.dungeonarchitect.domain.FeatureTemplate;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.door.DoorTemplateRegistry;
import com.dungeonarchitect.feature.FeatureTemplateRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        features.replaceFeatureReferences(oldId, renamed.id());
        doors.replaceFeatureReferences(oldId, renamed.id());
        rooms.replaceFeatureReferences(oldId, renamed.id());
        reloadAll();
        return renamed;
    }

    public void deleteFeature(String featureId) throws IOException {
        List<String> owners = new ArrayList<>(features.featureOwnersReferencing(featureId));
        doors.visible().stream()
            .filter(owner -> owner.featureSlots().stream().flatMap(slot -> slot.entries().stream()).anyMatch(entry -> entry.featureId().equalsIgnoreCase(featureId)))
            .map(owner -> "door " + owner.id()).forEach(owners::add);
        rooms.visible().stream()
            .filter(owner -> owner.featureSlots().stream().flatMap(slot -> slot.entries().stream()).anyMatch(entry -> entry.featureId().equalsIgnoreCase(featureId)))
            .map(owner -> "room " + owner.id()).forEach(owners::add);
        if (!owners.isEmpty()) {
            throw new IllegalStateException("Feature " + featureId + " is referenced by " + String.join(", ", owners.stream().sorted().toList()));
        }
        features.deleteFeature(featureId);
        reloadAll();
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
