package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DungeonEdge;
import com.dungeonarchitect.domain.DungeonGraph;
import com.dungeonarchitect.domain.DungeonNode;
import com.dungeonarchitect.domain.IntVector3;
import com.dungeonarchitect.domain.RoomCategory;
import com.dungeonarchitect.domain.RoomTemplate;
import com.dungeonarchitect.domain.RoomTransform;
import com.dungeonarchitect.domain.Rotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class DeterministicDungeonGenerator {
    private final int maxAttempts;
    private final int spawnY;
    private final DungeonGraphValidator validator = new DungeonGraphValidator();

    public DeterministicDungeonGenerator(int maxAttempts, int spawnY) {
        this.maxAttempts = maxAttempts;
        this.spawnY = spawnY;
    }

    public DungeonGenerationResult generate(Collection<RoomTemplate> templates, DungeonGenerationRequest request) {
        List<RoomTemplate> orderedTemplates = templates.stream()
            .sorted(Comparator.comparing(RoomTemplate::id))
            .toList();
        List<RoomTemplate> starts = orderedTemplates.stream()
            .filter(template -> template.category() == RoomCategory.START)
            .toList();
        if (starts.size() != 1) {
            return DungeonGenerationResult.failure("Expected exactly one START room template, found " + starts.size());
        }
        if (orderedTemplates.stream().noneMatch(template -> template.category() != RoomCategory.START && !template.doors().isEmpty())) {
            return DungeonGenerationResult.failure("At least one non-START template with doors is required");
        }

        Random random = new Random(request.seed());
        List<DungeonNode> nodes = new ArrayList<>();
        List<DungeonEdge> edges = new ArrayList<>();
        List<OpenDoor> openDoors = new ArrayList<>();

        RoomTemplate start = starts.getFirst();
        RoomTransform startTransform = new RoomTransform(new IntVector3(0, spawnY, 0), Rotation.NONE, start.size());
        nodes.add(new DungeonNode(0, start.id(), start.category(), 0, startTransform));
        for (DoorSocket door : start.doors()) {
            openDoors.add(new OpenDoor(0, door, startTransform));
        }

        int attempts = 0;
        while (nodes.size() < request.roomCount() && attempts++ < maxAttempts) {
            if (openDoors.isEmpty()) {
                return DungeonGenerationResult.failure("No open doors remain before target room count was reached");
            }
            OpenDoor existing = openDoors.remove(random.nextInt(openDoors.size()));
            Placement placement = choosePlacement(existing, orderedTemplates, nodes, random);
            if (placement == null) {
                continue;
            }

            int nodeIndex = nodes.size();
            nodes.add(new DungeonNode(nodeIndex, placement.template.id(), placement.template.category(), nodes.get(existing.nodeIndex).depth() + 1, placement.transform));
            edges.add(new DungeonEdge(existing.nodeIndex, existing.door.id(), nodeIndex, placement.door.id()));
            for (DoorSocket door : placement.template.doors()) {
                if (!door.id().equals(placement.door.id())) {
                    openDoors.add(new OpenDoor(nodeIndex, door, placement.transform));
                }
            }
        }

        if (nodes.size() != request.roomCount()) {
            return DungeonGenerationResult.failure("Exhausted placement attempts after generating " + nodes.size() + " rooms");
        }
        DungeonGraph graph = new DungeonGraph(nodes, edges);
        List<String> validationErrors = validator.validate(graph, orderedTemplates);
        if (!validationErrors.isEmpty()) {
            return DungeonGenerationResult.failure(String.join("; ", validationErrors));
        }
        return DungeonGenerationResult.success(graph);
    }

    private Placement choosePlacement(OpenDoor existing, List<RoomTemplate> templates, List<DungeonNode> nodes, Random random) {
        List<RoomTemplate> weighted = new ArrayList<>();
        for (RoomTemplate template : templates) {
            if (template.category() == RoomCategory.START || template.doors().isEmpty()) {
                continue;
            }
            for (int i = 0; i < template.weight(); i++) {
                weighted.add(template);
            }
        }
        while (!weighted.isEmpty()) {
            RoomTemplate template = weighted.remove(random.nextInt(weighted.size()));
            List<DoorSocket> candidateDoors = new ArrayList<>(template.doors());
            while (!candidateDoors.isEmpty()) {
                DoorSocket candidateDoor = candidateDoors.remove(random.nextInt(candidateDoors.size()));
                if (!candidateDoor.compatibleWith(existing.door)) {
                    continue;
                }
                List<Rotation> rotations = new ArrayList<>(List.of(Rotation.values()));
                while (!rotations.isEmpty()) {
                    Rotation rotation = rotations.remove(random.nextInt(rotations.size()));
                    Direction3 existingFacing = existing.transform.transformFacing(existing.door.facing());
                    if (candidateDoor.facing().rotateY(rotation) != existingFacing.opposite()) {
                        continue;
                    }
                    IntVector3 targetDoorWorld = existing.transform.transformLocal(existing.door.position()).add(existingFacing.vector());
                    IntVector3 rotatedDoor = rotation.rotate(candidateDoor.position(), template.size());
                    RoomTransform transform = new RoomTransform(targetDoorWorld.subtract(rotatedDoor), rotation, template.size());
                    BoundingBox3i bounds = transform.transformedBounds();
                    boolean collides = nodes.stream()
                        .map(node -> node.transform().transformedBounds())
                        .anyMatch(bounds::intersects);
                    if (!collides) {
                        return new Placement(template, candidateDoor, transform);
                    }
                }
            }
        }
        return null;
    }

    private record OpenDoor(int nodeIndex, DoorSocket door, RoomTransform transform) {
    }

    private record Placement(RoomTemplate template, DoorSocket door, RoomTransform transform) {
    }
}
