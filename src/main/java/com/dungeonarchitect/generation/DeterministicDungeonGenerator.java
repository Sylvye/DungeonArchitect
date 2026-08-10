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
import com.dungeonarchitect.domain.SocketType;
import com.dungeonarchitect.door.DoorTemplateMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class DeterministicDungeonGenerator {
    private final int maxSearchSteps;
    private final int spawnY;
    private final Supplier<Collection<DoorTemplate>> doorTemplates;
    private final DungeonGraphValidator validator = new DungeonGraphValidator();

    public DeterministicDungeonGenerator(int maxSearchSteps, int spawnY) {
        this(maxSearchSteps, spawnY, List::of);
    }

    public DeterministicDungeonGenerator(int maxSearchSteps, int spawnY, Supplier<Collection<DoorTemplate>> doorTemplates) {
        this.maxSearchSteps = maxSearchSteps;
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

        Map<String, DoorTemplate> doorsById = doorTemplates.get().stream()
            .collect(Collectors.toMap(DoorTemplate::id, java.util.function.Function.identity(), (first, second) -> first));
        GenerationContext context = new GenerationContext(
            request.roomCount(),
            orderedTemplates,
            orderedTemplates.stream()
                .filter(template -> template.category() != RoomCategory.START && !template.doors().isEmpty())
                .sorted(Comparator.comparing(RoomTemplate::id))
                .toList(),
            doorsById,
            !doorsById.isEmpty()
        );
        SearchState state = new SearchState();
        RoomTemplate start = starts.getFirst();
        RoomTransform startTransform = new RoomTransform(new IntVector3(0, spawnY, 0), Rotation.NONE, start.size());
        state.nodes.add(new DungeonNode(0, start.id(), start.category(), 0, startTransform));
        state.occupancy.add(0, startTransform.transformedBounds());
        addFrontierDoors(context, state, 0, start, startTransform, null);

        if (state.nodes.size() == request.roomCount()) {
            return validatedResult(state, context);
        }
        if (state.openDoors.isEmpty()) {
            return DungeonGenerationResult.failure("No open doors remain before target room count was reached");
        }

        SearchStats stats = new SearchStats();
        Random random = new Random(request.seed());
        boolean completed = search(context, state, random, stats);
        if (!completed) {
            return DungeonGenerationResult.failure(failureMessage(stats));
        }
        return validatedResult(state, context);
    }

    private DungeonGenerationResult validatedResult(SearchState state, GenerationContext context) {
        DungeonGraph graph = new DungeonGraph(state.nodes, state.edges);
        List<String> validationErrors = validator.validate(graph, context.allTemplates, context.doorsById.values());
        if (!validationErrors.isEmpty()) {
            return DungeonGenerationResult.failure(String.join("; ", validationErrors));
        }
        return DungeonGenerationResult.success(graph);
    }

    private boolean search(GenerationContext context, SearchState state, Random random, SearchStats stats) {
        if (state.nodes.size() == context.targetRoomCount) {
            return true;
        }
        stats.observe(state);
        if (stats.searchSteps >= maxSearchSteps) {
            stats.budgetExhausted = true;
            return false;
        }
        stats.searchSteps++;

        Selection selection = selectFrontierDoor(context, state);
        if (selection == null) {
            return false;
        }

        OpenDoor selectedDoor = state.openDoors.get(selection.openIndex);
        List<Placement> selectedCandidates = candidatePlacements(context, state, selectedDoor, stats);
        OpenDoor existing = state.openDoors.remove(selection.openIndex);
        if (selectedCandidates.isEmpty()) {
            state.exhaustedDoors.add(existing);
            if (search(context, state, random, stats)) {
                return true;
            }
            state.exhaustedDoors.removeLast();
            state.openDoors.add(selection.openIndex, existing);
            return false;
        }

        for (Placement placement : orderCandidates(selectedCandidates, random)) {
            UndoRecord undo = applyPlacement(context, state, existing, placement);
            if (search(context, state, random, stats)) {
                return true;
            }
            undo(state, undo);
            if (stats.budgetExhausted) {
                state.openDoors.add(selection.openIndex, existing);
                return false;
            }
        }

        state.exhaustedDoors.add(existing);
        if (search(context, state, random, stats)) {
            return true;
        }
        state.exhaustedDoors.removeLast();
        state.openDoors.add(selection.openIndex, existing);
        return false;
    }

    private Selection selectFrontierDoor(GenerationContext context, SearchState state) {
        Selection best = null;
        for (int i = 0; i < state.openDoors.size(); i++) {
            List<Placement> candidates = candidatePlacements(context, state, state.openDoors.get(i), null);
            if (candidates.isEmpty()) {
                return new Selection(i, candidates);
            }
            if (best == null || candidates.size() < best.candidates.size()) {
                best = new Selection(i, candidates);
            }
        }
        return best;
    }

    private List<Placement> candidatePlacements(GenerationContext context, SearchState state, OpenDoor existing, SearchStats stats) {
        List<Placement> placements = new ArrayList<>();
        Direction3 existingFacing = existing.transform.transformFacing(existing.door.facing());
        BoundingBox3i existingDoorBounds = DoorGeometry.transformedBounds(existing.door, existing.transform);
        WorldSlotSignature signature = new WorldSlotSignature(
            context.templateDoorMode,
            existingFacing,
            existingDoorBounds.size(),
            existing.door.socketType(),
            existing.door.tags(),
            existing.door.entries()
        );
        List<CandidatePrototype> prototypes = context.candidateCache.computeIfAbsent(signature, key -> buildCandidatePrototypes(context, existing.door, key));
        if (prototypes.isEmpty() && stats != null) {
            stats.compatibilityRejects++;
        }
        for (CandidatePrototype prototype : prototypes) {
            Placement placement = placementFromPrototype(existing, prototype, context);
            if (placement == null) {
                if (stats != null) {
                    stats.compatibilityRejects++;
                }
                continue;
            }
            if (state.occupancy.intersects(placement.bounds)) {
                if (stats != null) {
                    stats.collisionRejects++;
                }
                continue;
            }
            placements.add(placement);
        }
        return placements;
    }

    private List<CandidatePrototype> buildCandidatePrototypes(GenerationContext context, DoorSocket existingDoor, WorldSlotSignature existing) {
        List<CandidatePrototype> prototypes = new ArrayList<>();
        List<DoorChoice> existingChoices = context.templateDoorMode ? doorChoices(existingDoor, context) : List.of();
        for (RoomTemplate template : context.candidateTemplates) {
            for (DoorSocket candidateDoor : template.doors()) {
                if (!candidateDoor.compatibleWith(existingDoor)) {
                    continue;
                }
                List<DoorChoice> candidateChoices = context.templateDoorMode ? doorChoices(candidateDoor, context) : List.of();
                for (Rotation rotation : Rotation.values()) {
                    if (candidateDoor.facing().rotateY(rotation) != existing.facing.opposite()) {
                        continue;
                    }
                    if (!context.templateDoorMode) {
                        BoundingBox3i candidateRelativeBounds = DoorGeometry.relativeBounds(candidateDoor, rotation, template.size());
                        if (candidateRelativeBounds.size().equals(existing.size)) {
                            prototypes.add(new CandidatePrototype(template, candidateDoor, rotation, candidateRelativeBounds, null, null, template.weight()));
                        }
                        continue;
                    }
                    if (existingChoices.isEmpty() || candidateChoices.isEmpty()) {
                        continue;
                    }
                    for (DoorChoice existingChoice : existingChoices) {
                        for (DoorChoice candidateChoice : candidateChoices) {
                            GatewayPlacement relativeGateway = relativeGatewayPlacement(candidateDoor, candidateChoice.template, rotation, template.size());
                            if (relativeGateway.facing != existing.facing.opposite()) {
                                continue;
                            }
                            int weight = Math.max(1, template.weight() * existingChoice.weight * candidateChoice.weight);
                            prototypes.add(new CandidatePrototype(template, candidateDoor, rotation, relativeGateway.bounds, existingChoice.template.id(), candidateChoice.template.id(), weight));
                        }
                    }
                }
            }
        }
        return List.copyOf(prototypes);
    }

    private Placement placementFromPrototype(OpenDoor existing, CandidatePrototype prototype, GenerationContext context) {
        Direction3 existingFacing = existing.transform.transformFacing(existing.door.facing());
        BoundingBox3i existingAnchorBounds;
        if (context.templateDoorMode) {
            DoorTemplate existingDoorTemplate = context.doorsById.get(prototype.existingDoorTemplateId);
            if (existingDoorTemplate == null) {
                return null;
            }
            GatewayPlacement existingGateway = gatewayPlacement(existing.door, existingDoorTemplate, existing.transform);
            if (existingGateway.facing != existingFacing || !existingGateway.bounds.size().equals(prototype.relativeAnchorBounds.size())) {
                return null;
            }
            existingAnchorBounds = existingGateway.bounds;
        } else {
            existingAnchorBounds = DoorGeometry.transformedBounds(existing.door, existing.transform);
        }
        BoundingBox3i targetAnchorBounds = DoorGeometry.shifted(existingAnchorBounds, existingFacing.vector());
        RoomTransform transform = new RoomTransform(targetAnchorBounds.min().subtract(prototype.relativeAnchorBounds.min()), prototype.rotation, prototype.template.size());
        return new Placement(
            prototype.template,
            prototype.door,
            transform,
            transform.transformedBounds(),
            prototype.existingDoorTemplateId,
            prototype.candidateDoorTemplateId,
            prototype.weight
        );
    }

    private List<Placement> orderCandidates(List<Placement> candidates, Random random) {
        return candidates.stream()
            .map(candidate -> new OrderedPlacement(candidate, weightedOrderKey(candidate.weight, random)))
            .sorted(Comparator.comparingDouble(OrderedPlacement::orderKey))
            .map(OrderedPlacement::placement)
            .toList();
    }

    private double weightedOrderKey(int weight, Random random) {
        double roll = Math.max(0.0000000001, random.nextDouble());
        return -Math.log(roll) / Math.max(1, weight);
    }

    private UndoRecord applyPlacement(GenerationContext context, SearchState state, OpenDoor existing, Placement placement) {
        int nodeIndex = state.nodes.size();
        int firstNewOpenDoor = state.openDoors.size();
        state.nodes.add(new DungeonNode(nodeIndex, placement.template.id(), placement.template.category(), state.nodes.get(existing.nodeIndex).depth() + 1, placement.transform));
        state.edges.add(new DungeonEdge(existing.nodeIndex, existing.door.id(), placement.existingDoorTemplateId, nodeIndex, placement.door.id(), placement.candidateDoorTemplateId));
        state.occupancy.add(nodeIndex, placement.bounds);
        addFrontierDoors(context, state, nodeIndex, placement.template, placement.transform, placement.door.id());
        return new UndoRecord(nodeIndex, firstNewOpenDoor);
    }

    private void undo(SearchState state, UndoRecord undo) {
        while (state.openDoors.size() > undo.firstNewOpenDoor) {
            state.openDoors.removeLast();
        }
        state.edges.removeLast();
        state.occupancy.remove(undo.nodeIndex);
        state.nodes.removeLast();
    }

    private void addFrontierDoors(GenerationContext context, SearchState state, int nodeIndex, RoomTemplate template, RoomTransform transform, String connectedDoorId) {
        for (DoorSocket door : template.doors()) {
            if (door.id().equals(connectedDoorId)) {
                continue;
            }
            if (!context.templateDoorMode || !doorChoices(door, context).isEmpty()) {
                state.openDoors.add(new OpenDoor(nodeIndex, door, transform));
            }
        }
    }

    private List<DoorChoice> doorChoices(DoorSocket slot, GenerationContext context) {
        return context.doorChoicesCache.computeIfAbsent(slot, ignored -> {
            List<DoorChoice> choices = new ArrayList<>();
            for (DoorSlotEntry entry : slot.entries()) {
                if (entry.doorId().equals(DoorSlotEntry.EMPTY)) {
                    continue;
                }
                DoorTemplate template = context.doorsById.get(entry.doorId());
                if (template == null) {
                    continue;
                }
                if (!DoorTemplateMatcher.matches(slot, template)) {
                    continue;
                }
                choices.add(new DoorChoice(template, entry.weight()));
            }
            return List.copyOf(choices);
        });
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

    private String failureMessage(SearchStats stats) {
        String reason = stats.budgetExhausted ? "Search budget exhausted" : "No valid continuation found";
        return reason
            + ": rooms placed=" + stats.bestRooms
            + ", open doors=" + stats.bestOpenDoors
            + ", exhausted doors=" + stats.bestExhaustedDoors
            + ", collision rejects=" + stats.collisionRejects
            + ", compatibility rejects=" + stats.compatibilityRejects
            + ", search steps=" + stats.searchSteps
            + ". Run /da diagnose for template compatibility hints.";
    }

    private static final class GenerationContext {
        private final int targetRoomCount;
        private final List<RoomTemplate> allTemplates;
        private final List<RoomTemplate> candidateTemplates;
        private final Map<String, DoorTemplate> doorsById;
        private final boolean templateDoorMode;
        private final Map<DoorSocket, List<DoorChoice>> doorChoicesCache = new HashMap<>();
        private final Map<WorldSlotSignature, List<CandidatePrototype>> candidateCache = new HashMap<>();

        private GenerationContext(int targetRoomCount, List<RoomTemplate> allTemplates, List<RoomTemplate> candidateTemplates, Map<String, DoorTemplate> doorsById, boolean templateDoorMode) {
            this.targetRoomCount = targetRoomCount;
            this.allTemplates = allTemplates;
            this.candidateTemplates = candidateTemplates;
            this.doorsById = doorsById;
            this.templateDoorMode = templateDoorMode;
        }
    }

    private static final class SearchState {
        private final List<DungeonNode> nodes = new ArrayList<>();
        private final List<DungeonEdge> edges = new ArrayList<>();
        private final List<OpenDoor> openDoors = new ArrayList<>();
        private final List<OpenDoor> exhaustedDoors = new ArrayList<>();
        private final SpatialRoomIndex occupancy = new SpatialRoomIndex();
    }

    private static final class SearchStats {
        private int searchSteps;
        private int collisionRejects;
        private int compatibilityRejects;
        private boolean budgetExhausted;
        private int bestRooms;
        private int bestOpenDoors;
        private int bestExhaustedDoors;

        private void observe(SearchState state) {
            if (state.nodes.size() > bestRooms || (state.nodes.size() == bestRooms && state.exhaustedDoors.size() > bestExhaustedDoors)) {
                bestRooms = state.nodes.size();
                bestOpenDoors = state.openDoors.size();
                bestExhaustedDoors = state.exhaustedDoors.size();
            }
        }
    }

    private record OpenDoor(int nodeIndex, DoorSocket door, RoomTransform transform) {
    }

    private record Placement(RoomTemplate template, DoorSocket door, RoomTransform transform, BoundingBox3i bounds, String existingDoorTemplateId, String candidateDoorTemplateId, int weight) {
    }

    private record CandidatePrototype(RoomTemplate template, DoorSocket door, Rotation rotation, BoundingBox3i relativeAnchorBounds, String existingDoorTemplateId, String candidateDoorTemplateId, int weight) {
    }

    private record DoorChoice(DoorTemplate template, int weight) {
    }

    private record GatewayPlacement(BoundingBox3i bounds, Direction3 facing) {
    }

    private record Selection(int openIndex, List<Placement> candidates) {
    }

    private record OrderedPlacement(Placement placement, double orderKey) {
    }

    private record UndoRecord(int nodeIndex, int firstNewOpenDoor) {
    }

    private record WorldSlotSignature(boolean templateDoorMode, Direction3 facing, IntVector3 size, SocketType socketType, Set<String> tags, List<DoorSlotEntry> entries) {
    }
}
