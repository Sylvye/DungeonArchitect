package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorSlotEntry;
import com.dungeonarchitect.domain.DoorTemplate;
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
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class DeterministicDungeonGenerator {
    private final int maxAttempts;
    private final int spawnY;
    private final Supplier<Collection<DoorTemplate>> doorTemplates;
    private final DungeonGraphValidator validator = new DungeonGraphValidator();

    public DeterministicDungeonGenerator(int maxAttempts, int spawnY) {
        this(maxAttempts, spawnY, List::of);
    }

    public DeterministicDungeonGenerator(int maxAttempts, int spawnY, Supplier<Collection<DoorTemplate>> doorTemplates) {
        this.maxAttempts = maxAttempts;
        this.spawnY = spawnY;
        this.doorTemplates = doorTemplates;
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
        Map<String, DoorTemplate> doorsById = doorTemplates.get().stream()
            .collect(Collectors.toMap(DoorTemplate::id, java.util.function.Function.identity(), (first, second) -> first));
        boolean templateDoorMode = !doorsById.isEmpty();
        List<DungeonNode> nodes = new ArrayList<>();
        List<DungeonEdge> edges = new ArrayList<>();
        List<OpenDoor> openDoors = new ArrayList<>();

        RoomTemplate start = starts.getFirst();
        RoomTransform startTransform = new RoomTransform(new IntVector3(0, spawnY, 0), Rotation.NONE, start.size());
        nodes.add(new DungeonNode(0, start.id(), start.category(), 0, startTransform));
        for (DoorSocket door : start.doors()) {
            if (!templateDoorMode || !doorChoices(door, doorsById).isEmpty()) {
                openDoors.add(new OpenDoor(0, door, startTransform));
            }
        }

        int attempts = 0;
        while (nodes.size() < request.roomCount() && attempts++ < maxAttempts) {
            if (openDoors.isEmpty()) {
                return DungeonGenerationResult.failure("No open doors remain before target room count was reached");
            }
            int openIndex = random.nextInt(openDoors.size());
            OpenDoor existing = openDoors.get(openIndex);
            Placement placement = choosePlacement(existing, orderedTemplates, nodes, doorsById, templateDoorMode, random);
            if (placement == null) {
                continue;
            }

            openDoors.remove(openIndex);
            int nodeIndex = nodes.size();
            nodes.add(new DungeonNode(nodeIndex, placement.template.id(), placement.template.category(), nodes.get(existing.nodeIndex).depth() + 1, placement.transform));
            edges.add(new DungeonEdge(existing.nodeIndex, existing.door.id(), placement.existingDoorTemplateId, nodeIndex, placement.door.id(), placement.candidateDoorTemplateId));
            for (DoorSocket door : placement.template.doors()) {
                if (!door.id().equals(placement.door.id()) && (!templateDoorMode || !doorChoices(door, doorsById).isEmpty())) {
                    openDoors.add(new OpenDoor(nodeIndex, door, placement.transform));
                }
            }
        }

        if (nodes.size() != request.roomCount()) {
            return DungeonGenerationResult.failure("Exhausted placement attempts after generating " + nodes.size() + " rooms");
        }
        DungeonGraph graph = new DungeonGraph(nodes, edges);
        List<String> validationErrors = validator.validate(graph, orderedTemplates, doorsById.values());
        if (!validationErrors.isEmpty()) {
            return DungeonGenerationResult.failure(String.join("; ", validationErrors));
        }
        return DungeonGenerationResult.success(graph);
    }

    private Placement choosePlacement(OpenDoor existing, List<RoomTemplate> templates, List<DungeonNode> nodes, Map<String, DoorTemplate> doorsById, boolean templateDoorMode, Random random) {
        List<RoomTemplate> weighted = new ArrayList<>();
        for (RoomTemplate template : templates) {
            if (template.category() == RoomCategory.START || template.doors().isEmpty()) {
                continue;
            }
            for (int i = 0; i < template.weight(); i++) {
                weighted.add(template);
            }
        }
        List<DoorChoice> existingChoices = templateDoorMode ? doorChoices(existing.door, doorsById) : List.of();
        while (!weighted.isEmpty()) {
            RoomTemplate template = weighted.remove(random.nextInt(weighted.size()));
            List<DoorSocket> candidateDoors = new ArrayList<>(template.doors());
            while (!candidateDoors.isEmpty()) {
                DoorSocket candidateDoor = candidateDoors.remove(random.nextInt(candidateDoors.size()));
                if (!candidateDoor.compatibleWith(existing.door)) {
                    continue;
                }
                List<DoorChoice> candidateChoices = templateDoorMode ? doorChoices(candidateDoor, doorsById) : List.of();
                List<Rotation> rotations = new ArrayList<>(List.of(Rotation.values()));
                while (!rotations.isEmpty()) {
                    Rotation rotation = rotations.remove(random.nextInt(rotations.size()));
                    Direction3 existingFacing = existing.transform.transformFacing(existing.door.facing());
                    if (candidateDoor.facing().rotateY(rotation) != existingFacing.opposite()) {
                        continue;
                    }
                    if (!templateDoorMode) {
                        BoundingBox3i existingDoorBounds = DoorGeometry.transformedBounds(existing.door, existing.transform);
                        BoundingBox3i targetDoorBounds = DoorGeometry.shifted(existingDoorBounds, existingFacing.vector());
                        BoundingBox3i candidateRelativeBounds = DoorGeometry.relativeBounds(candidateDoor, rotation, template.size());
                        if (!candidateRelativeBounds.size().equals(existingDoorBounds.size())) {
                            continue;
                        }
                        RoomTransform transform = new RoomTransform(targetDoorBounds.min().subtract(candidateRelativeBounds.min()), rotation, template.size());
                        if (!collides(nodes, transform.transformedBounds())) {
                            return new Placement(template, candidateDoor, transform, null, null);
                        }
                        continue;
                    }
                    List<DoorChoice> shuffledExistingChoices = new ArrayList<>(existingChoices);
                    while (!shuffledExistingChoices.isEmpty()) {
                        DoorChoice existingChoice = shuffledExistingChoices.remove(random.nextInt(shuffledExistingChoices.size()));
                        GatewayPlacement existingGateway = gatewayPlacement(existing.door, existingChoice.template, existing.transform);
                        if (existingGateway.facing != existingFacing) {
                            continue;
                        }
                        List<DoorChoice> shuffledCandidateChoices = new ArrayList<>(candidateChoices);
                        while (!shuffledCandidateChoices.isEmpty()) {
                            DoorChoice candidateChoice = shuffledCandidateChoices.remove(random.nextInt(shuffledCandidateChoices.size()));
                            GatewayPlacement relativeGateway = relativeGatewayPlacement(candidateDoor, candidateChoice.template, rotation, template.size());
                            if (relativeGateway.facing != existingGateway.facing.opposite()) {
                                continue;
                            }
                            if (!relativeGateway.bounds.size().equals(existingGateway.bounds.size())) {
                                continue;
                            }
                            BoundingBox3i targetGatewayBounds = DoorGeometry.shifted(existingGateway.bounds, existingGateway.facing.vector());
                            RoomTransform transform = new RoomTransform(targetGatewayBounds.min().subtract(relativeGateway.bounds.min()), rotation, template.size());
                            if (!collides(nodes, transform.transformedBounds())) {
                                return new Placement(template, candidateDoor, transform, existingChoice.template.id(), candidateChoice.template.id());
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private List<DoorChoice> doorChoices(DoorSocket slot, Map<String, DoorTemplate> doorsById) {
        List<DoorChoice> choices = new ArrayList<>();
        for (DoorSlotEntry entry : slot.entries()) {
            if (entry.doorId().equals(DoorSlotEntry.EMPTY)) {
                continue;
            }
            DoorTemplate template = doorsById.get(entry.doorId());
            if (template == null) {
                continue;
            }
            if (!com.dungeonarchitect.door.DoorTemplateMatcher.matches(slot, template)) {
                continue;
            }
            for (int i = 0; i < entry.weight(); i++) {
                choices.add(new DoorChoice(template));
            }
        }
        return choices;
    }

    private GatewayPlacement gatewayPlacement(DoorSocket slot, DoorTemplate door, RoomTransform roomTransform) {
        RoomTransform doorTransform = DoorGeometry.doorTransform(slot, door, roomTransform);
        return new GatewayPlacement(
            DoorGeometry.transformedBounds(door.gateway(), doorTransform),
            DoorGeometry.gatewayFacing(door, doorTransform)
        );
    }

    private GatewayPlacement relativeGatewayPlacement(DoorSocket slot, DoorTemplate door, Rotation roomRotation, IntVector3 roomSize) {
        return gatewayPlacement(slot, door, new RoomTransform(IntVector3.ZERO, roomRotation, roomSize));
    }

    private boolean collides(List<DungeonNode> nodes, BoundingBox3i bounds) {
        return nodes.stream()
            .map(node -> node.transform().transformedBounds())
            .anyMatch(bounds::intersects);
    }

    private record OpenDoor(int nodeIndex, DoorSocket door, RoomTransform transform) {
    }

    private record Placement(RoomTemplate template, DoorSocket door, RoomTransform transform, String existingDoorTemplateId, String candidateDoorTemplateId) {
    }

    private record DoorChoice(DoorTemplate template) {
    }

    private record GatewayPlacement(BoundingBox3i bounds, Direction3 facing) {
    }
}
