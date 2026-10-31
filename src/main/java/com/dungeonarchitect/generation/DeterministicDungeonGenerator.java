package com.dungeonarchitect.generation;

import com.dungeonarchitect.domain.BoundingBox3i;
import com.dungeonarchitect.domain.Direction3;
import com.dungeonarchitect.domain.DoorSocket;
import com.dungeonarchitect.domain.DoorConnectionRules;
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
        List<SpreadReport> reports = new ArrayList<>();
        if (request.spread() == null) return generateAttempt(templates, request, reports, false);
        Map<String, RoomTemplate> byId = templates.stream().collect(Collectors.toMap(RoomTemplate::id, t -> t, (a, b) -> a));
        TeamSpread spread = request.spread();
        if (!byId.containsKey(spread.rootId()) || byId.get(spread.rootId()).category() != RoomCategory.START
            || !byId.containsKey(spread.spawnId()) || byId.get(spread.spawnId()).category() != RoomCategory.TEAM_SPAWN
            || spread.ordinaryIds().stream().anyMatch(id -> !byId.containsKey(id) || byId.get(id).category() == RoomCategory.START || byId.get(id).category() == RoomCategory.TEAM_SPAWN)) return DungeonGenerationResult.failure("Spread pools must reference a valid START root, TEAM_SPAWN template, and ordinary rooms");
        String failure = "No valid layout";
        for (int attempt = 0; attempt < 20; attempt++) {
            long seed = request.seed() + attempt * 0x9E3779B97F4A7C15L;
            DungeonGenerationResult result = generateAttempt(templates, new DungeonGenerationRequest(request.roomCount(), seed, request.spread()), reports, attempt < 10);
            if (result.successful()) return result;
            failure = String.join("; ", result.errors());
        }
        String best = reports.stream().max(Comparator.comparingInt((SpreadReport r) -> r.spawns().size()).thenComparingInt(SpreadReport::shortest)).map(r -> " Best measured spread: " + r.summary()).orElse(" No pair of spawn rooms could meet the minimum distance.");
        return DungeonGenerationResult.failure("No fair layout after 20 attempts. " + failure + best
            + ". Increase room count, check template connectivity, or edit spread settings.");
    }

    private DungeonGenerationResult generateAttempt(Collection<RoomTemplate> templates, DungeonGenerationRequest request, List<SpreadReport> reports, boolean guided) {
        List<RoomTemplate> orderedTemplates = templates.stream()
            .filter(template -> request.spread() == null ? template.category() != RoomCategory.TEAM_SPAWN
                : template.id().equals(request.spread().rootId()) || template.id().equals(request.spread().spawnId())
                    || request.spread().ordinaryIds().contains(template.id()))
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
            !doorsById.isEmpty(), request.spread(), guided
        );
        SearchState state = new SearchState();
        RoomTemplate start = starts.getFirst();
        RoomTransform startTransform = new RoomTransform(new IntVector3(0, spawnY, 0), Rotation.NONE, start.size());
        state.nodes.add(new DungeonNode(0, start.id(), start.category(), 0, startTransform));
        state.parents.add(-1);
        state.connectionCounts.add(0);
        state.minimumConnections.add(start.minimumConnections());
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
            if (stats.bestSpread != null) reports.add(stats.bestSpread);
            return DungeonGenerationResult.failure(failureMessage(stats));
        }
        return validatedResult(state, context);
    }

    private DungeonGenerationResult validatedResult(SearchState state, GenerationContext context) {
        if (hasRequiredOpenDoors(state)) {
            return DungeonGenerationResult.failure("Required door slots remain unconnected");
        }
        if (minimumConnectionDeficit(state) > 0) {
            return DungeonGenerationResult.failure(unmetMinimumMessage(state));
        }
        DungeonGraph graph = new DungeonGraph(state.nodes, state.edges);
        List<String> validationErrors = validator.validate(graph, context.allTemplates, context.doorsById.values());
        if (!validationErrors.isEmpty()) {
            return DungeonGenerationResult.failure(String.join("; ", validationErrors));
        }
        return DungeonGenerationResult.success(graph);
    }

    private boolean search(GenerationContext context, SearchState state, Random random, SearchStats stats) {
        if (++stats.searchSteps > maxSearchSteps) {
            stats.budgetExhausted = true;
            return false;
        }
        if (context.spread != null && (stats.bestSpread == null || state.spawnCount > stats.bestSpread.spawns().size())) stats.bestSpread = SpreadReport.measure(new DungeonGraph(state.nodes, state.edges));
        if (state.nodes.size() == context.targetRoomCount) {
            if (context.spread != null) {
                if (state.spawnCount != context.spread.teams() || state.shortestSpawnDistance < context.spread.minimumDistance()
                    || state.longestSpawnDistance > state.shortestSpawnDistance * context.spread.maximumRatio()) return false;
            }
            return !hasRequiredOpenDoors(state) && minimumConnectionDeficit(state) == 0;
        }
        if (requiredOpenDoorCount(state) > context.targetRoomCount - state.nodes.size()) {
            return false;
        }
        if (!canMeetMinimumConnections(state, context.targetRoomCount - state.nodes.size())) {
            stats.minimumConnectionRejects++;
            stats.bestMinimumConnectionDeficit = Math.max(stats.bestMinimumConnectionDeficit, minimumConnectionDeficit(state));
            return false;
        }
        stats.observe(state);

        Selection selection = selectFrontierDoor(context, state, stats);
        if (selection == null) {
            return false;
        }

        OpenDoor selectedDoor = state.openDoors.get(selection.openIndex);
        List<Placement> selectedCandidates = selection.candidates;
        OpenDoor existing = state.openDoors.remove(selection.openIndex);
        if (selectedCandidates.isEmpty()) {
            if (existing.door.connectionRules().mustConnect()) {
                state.openDoors.add(selection.openIndex, existing);
                return false;
            }
            state.exhaustedDoors.add(existing);
            if (search(context, state, random, stats)) {
                return true;
            }
            state.exhaustedDoors.removeLast();
            state.openDoors.add(selection.openIndex, existing);
            return false;
        }

        List<Placement> ordered = orderCandidates(selectedCandidates, random);
        if (context.spread != null) {
            boolean preferSpawn = state.nodes.get(existing.nodeIndex).depth() + 1 >= context.preferredSpawnDepth;
            ordered = ordered.stream().sorted(Comparator.comparingInt(p -> (p.template.category() == RoomCategory.TEAM_SPAWN) == preferSpawn ? 0 : 1)).toList();
        }
        for (Placement placement : ordered) {
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

    private Selection selectFrontierDoor(GenerationContext context, SearchState state, SearchStats stats) {
        Selection bestRequired = null;
        Selection bestMinimum = null;
        Selection bestOptional = null;
        for (int i = 0; i < state.openDoors.size(); i++) {
            List<Placement> candidates = candidatePlacements(context, state, state.openDoors.get(i), stats);
            OpenDoor openDoor = state.openDoors.get(i);
            if (candidates.isEmpty() && openDoor.door.connectionRules().mustConnect()) {
                return new Selection(i, candidates);
            }
            Selection candidate = new Selection(i, candidates);
            if (openDoor.door.connectionRules().mustConnect()) {
                if (bestRequired == null || candidates.size() < bestRequired.candidates.size()) {
                    bestRequired = candidate;
                }
            } else if (state.connectionCounts.get(openDoor.nodeIndex) < state.minimumConnections.get(openDoor.nodeIndex)) {
                if (bestMinimum == null || candidates.size() < bestMinimum.candidates.size()) {
                    bestMinimum = candidate;
                }
            } else if (bestOptional == null || (context.spread != null && state.spawnCount < context.spread.teams()
                ? spreadFrontierScore(context, state, candidate) > spreadFrontierScore(context, state, bestOptional)
                : candidates.size() < bestOptional.candidates.size())) {
                bestOptional = candidate;
            }
        }
        if (bestRequired != null) {
            return bestRequired;
        }
        return bestMinimum != null ? bestMinimum : bestOptional;
    }

    private int spreadFrontierScore(GenerationContext context, SearchState state, Selection selection) {
        if (selection.candidates.isEmpty()) return Integer.MIN_VALUE;
        OpenDoor door = state.openDoors.get(selection.openIndex);
        DungeonNode node = state.nodes.get(door.nodeIndex);
        boolean canSpawn = node.depth() + 1 >= context.preferredSpawnDepth
            && selection.candidates.stream().anyMatch(p -> p.template.category() == RoomCategory.TEAM_SPAWN);
        // Grow a branch to a useful depth, place its spawn, then grow other branches.
        int depthScore = node.depth() < context.preferredSpawnDepth ? node.depth() : -node.depth();
        return (node.category() == RoomCategory.TEAM_SPAWN ? -100000 : 0) + (canSpawn ? 10000 : 0) + depthScore;
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
            existing.door.entries(),
            existing.door.connectionRules(),
            existing.roomTags
        );
        List<CandidatePrototype> prototypes = context.candidateCache.computeIfAbsent(signature, key -> buildCandidatePrototypes(context, existing.door, existing.roomTags, key));
        if (prototypes.isEmpty() && stats != null) {
            stats.compatibilityRejects++;
        }
        boolean allowSpawn = true;
        if (context.spread != null) {
            allowSpawn = state.spawnCount < context.spread.teams();
            int min = state.shortestSpawnDistance, max = state.longestSpawnDistance;
            for (DungeonNode node : state.nodes) if (node.category() == RoomCategory.TEAM_SPAWN) {
                int distance = treeDistance(state, existing.nodeIndex, node.index()) + 1;
                if (distance < context.searchMinimumDistance) allowSpawn = false;
                min = Math.min(min, distance); max = Math.max(max, distance);
            }
            if (state.spawnCount > 0 && max > min * context.spread.maximumRatio()) allowSpawn = false;
        }
        for (CandidatePrototype prototype : prototypes) {
            if (context.spread != null) {
                boolean spawn = prototype.template.category() == RoomCategory.TEAM_SPAWN;
                if (spawn && !allowSpawn) continue;
                if (!spawn && context.targetRoomCount - state.nodes.size() <= context.spread.teams() - state.spawnCount) continue;
            }
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

    private int treeDistance(SearchState state, int first, int second) {
        int distance = 0;
        while (first != second) {
            if (state.nodes.get(first).depth() >= state.nodes.get(second).depth()) first = state.parents.get(first);
            else second = state.parents.get(second);
            distance++;
        }
        return distance;
    }

    private List<CandidatePrototype> buildCandidatePrototypes(GenerationContext context, DoorSocket existingDoor, Set<String> existingRoomTags, WorldSlotSignature existing) {
        List<CandidatePrototype> prototypes = new ArrayList<>();
        List<DoorChoice> existingChoices = context.templateDoorMode ? doorChoices(existingDoor, context) : List.of();
        for (RoomTemplate template : context.candidateTemplates) {
            if (template.minimumConnections() > template.doors().size()) {
                continue;
            }
            for (DoorSocket candidateDoor : template.doors()) {
                if (!candidateDoor.compatibleWith(existingDoor, template.tags(), existingRoomTags)) {
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
        int previousConnectionCount = state.connectionCounts.get(existing.nodeIndex);
        int previousShortest = state.shortestSpawnDistance, previousLongest = state.longestSpawnDistance;
        if (placement.template.category() == RoomCategory.TEAM_SPAWN) for (DungeonNode node : state.nodes) if (node.category() == RoomCategory.TEAM_SPAWN) {
            int distance = treeDistance(state, existing.nodeIndex, node.index()) + 1;
            state.shortestSpawnDistance = Math.min(state.shortestSpawnDistance, distance);
            state.longestSpawnDistance = Math.max(state.longestSpawnDistance, distance);
        }
        state.nodes.add(new DungeonNode(nodeIndex, placement.template.id(), placement.template.category(), state.nodes.get(existing.nodeIndex).depth() + 1, placement.transform));
        state.parents.add(existing.nodeIndex);
        if (placement.template.category() == RoomCategory.TEAM_SPAWN) state.spawnCount++;
        state.connectionCounts.set(existing.nodeIndex, previousConnectionCount + 1);
        state.connectionCounts.add(1);
        state.minimumConnections.add(placement.template.minimumConnections());
        state.edges.add(new DungeonEdge(existing.nodeIndex, existing.door.id(), placement.existingDoorTemplateId, nodeIndex, placement.door.id(), placement.candidateDoorTemplateId));
        state.occupancy.add(nodeIndex, placement.bounds);
        addFrontierDoors(context, state, nodeIndex, placement.template, placement.transform, placement.door.id());
        return new UndoRecord(nodeIndex, firstNewOpenDoor, existing.nodeIndex, previousConnectionCount, previousShortest, previousLongest);
    }

    private void undo(SearchState state, UndoRecord undo) {
        while (state.openDoors.size() > undo.firstNewOpenDoor) {
            state.openDoors.removeLast();
        }
        state.edges.removeLast();
        state.occupancy.remove(undo.nodeIndex);
        state.connectionCounts.removeLast();
        state.minimumConnections.removeLast();
        state.connectionCounts.set(undo.sourceNodeIndex, undo.previousSourceConnectionCount);
        if (state.nodes.removeLast().category() == RoomCategory.TEAM_SPAWN) state.spawnCount--;
        state.parents.removeLast();
        state.shortestSpawnDistance = undo.previousShortest;
        state.longestSpawnDistance = undo.previousLongest;
    }

    private void addFrontierDoors(GenerationContext context, SearchState state, int nodeIndex, RoomTemplate template, RoomTransform transform, String connectedDoorId) {
        for (DoorSocket door : template.doors()) {
            if (door.id().equals(connectedDoorId)) {
                continue;
            }
            if (!context.templateDoorMode || !doorChoices(door, context).isEmpty() || door.connectionRules().mustConnect()) {
                state.openDoors.add(new OpenDoor(nodeIndex, door, transform, template.tags()));
            }
        }
    }

    private boolean hasRequiredOpenDoors(SearchState state) {
        return requiredOpenDoorCount(state) > 0;
    }

    private int requiredOpenDoorCount(SearchState state) {
        return (int) state.openDoors.stream().filter(openDoor -> openDoor.door.connectionRules().mustConnect()).count();
    }

    private boolean canMeetMinimumConnections(SearchState state, int remainingPlacements) {
        if (minimumConnectionDeficit(state) > remainingPlacements) {
            return false;
        }
        for (int nodeIndex = 0; nodeIndex < state.nodes.size(); nodeIndex++) {
            int deficit = state.minimumConnections.get(nodeIndex) - state.connectionCounts.get(nodeIndex);
            if (deficit <= 0) {
                continue;
            }
            int availableDoors = 0;
            for (OpenDoor openDoor : state.openDoors) {
                if (openDoor.nodeIndex == nodeIndex) {
                    availableDoors++;
                }
            }
            if (availableDoors < deficit) {
                return false;
            }
        }
        return true;
    }

    private static int minimumConnectionDeficit(SearchState state) {
        int deficit = 0;
        for (int nodeIndex = 0; nodeIndex < state.connectionCounts.size(); nodeIndex++) {
            deficit += Math.max(0, state.minimumConnections.get(nodeIndex) - state.connectionCounts.get(nodeIndex));
        }
        return deficit;
    }

    private String unmetMinimumMessage(SearchState state) {
        for (int nodeIndex = 0; nodeIndex < state.nodes.size(); nodeIndex++) {
            int count = state.connectionCounts.get(nodeIndex);
            int minimum = state.minimumConnections.get(nodeIndex);
            if (count < minimum) {
                return "Room " + nodeIndex + " (" + state.nodes.get(nodeIndex).templateId() + ") has " + count + " connections; requires " + minimum;
            }
        }
        return "Room minimum connections remain unmet";
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
            + ", unmet room connections=" + stats.bestMinimumConnectionDeficit
            + ", minimum connection rejects=" + stats.minimumConnectionRejects
            + ", collision rejects=" + stats.collisionRejects
            + ", compatibility rejects=" + stats.compatibilityRejects
            + ", search steps=" + stats.searchSteps
            + (stats.bestSpread == null ? "" : ", best spread: " + stats.bestSpread.summary())
            + ". Run /da diagnose for template compatibility hints.";
    }

    private static final class GenerationContext {
        private final int targetRoomCount;
        private final List<RoomTemplate> allTemplates;
        private final List<RoomTemplate> candidateTemplates;
        private final Map<String, DoorTemplate> doorsById;
        private final boolean templateDoorMode;
        private final TeamSpread spread;
        private final int searchMinimumDistance;
        private final int preferredSpawnDepth;
        private final Map<DoorSocket, List<DoorChoice>> doorChoicesCache = new HashMap<>();
        private final Map<WorldSlotSignature, List<CandidatePrototype>> candidateCache = new HashMap<>();

        private GenerationContext(int targetRoomCount, List<RoomTemplate> allTemplates, List<RoomTemplate> candidateTemplates, Map<String, DoorTemplate> doorsById, boolean templateDoorMode, TeamSpread spread, boolean guided) {
            this.targetRoomCount = targetRoomCount;
            this.allTemplates = allTemplates;
            this.candidateTemplates = candidateTemplates;
            this.doorsById = doorsById;
            this.templateDoorMode = templateDoorMode;
            this.spread = spread;
            int branchLevels = 0;
            if (spread != null && guided && spread.maximumRatio() > 1) {
                int rootDoors = allTemplates.stream().filter(t -> t.id().equals(spread.rootId())).findFirst().orElseThrow().doors().size();
                int branches = Math.max(2, candidateTemplates.stream().filter(t -> t.category() != RoomCategory.TEAM_SPAWN).mapToInt(t -> t.doors().size() - 1).max().orElse(2));
                double groups = spread.teams() / (double) Math.max(1, rootDoors);
                if (groups > 1) branchLevels = (int) Math.ceil(Math.log(groups) / Math.log(branches));
            }
            // Shared branch stems need longer arms to keep pairwise routes comparable.
            searchMinimumDistance = spread == null ? 0 : Math.max(spread.minimumDistance(), branchLevels == 0 ? 0 : (int) Math.ceil(2.0 * branchLevels / (spread.maximumRatio() - 1)));
            preferredSpawnDepth = (searchMinimumDistance + 1) / 2 + branchLevels;
        }
    }

    private static final class SearchState {
        private int spawnCount;
        private int shortestSpawnDistance = Integer.MAX_VALUE;
        private int longestSpawnDistance;
        private final List<Integer> parents = new ArrayList<>();
        private final List<DungeonNode> nodes = new ArrayList<>();
        private final List<DungeonEdge> edges = new ArrayList<>();
        private final List<OpenDoor> openDoors = new ArrayList<>();
        private final List<OpenDoor> exhaustedDoors = new ArrayList<>();
        private final List<Integer> connectionCounts = new ArrayList<>();
        private final List<Integer> minimumConnections = new ArrayList<>();
        private final SpatialRoomIndex occupancy = new SpatialRoomIndex();
    }

    private static final class SearchStats {
        private SpreadReport bestSpread;
        private int searchSteps;
        private int collisionRejects;
        private int compatibilityRejects;
        private boolean budgetExhausted;
        private int bestRooms;
        private int bestOpenDoors;
        private int bestExhaustedDoors;
        private int bestMinimumConnectionDeficit;
        private int minimumConnectionRejects;

        private void observe(SearchState state) {
            if (state.nodes.size() > bestRooms || (state.nodes.size() == bestRooms && state.exhaustedDoors.size() > bestExhaustedDoors)) {
                bestRooms = state.nodes.size();
                bestOpenDoors = state.openDoors.size();
                bestExhaustedDoors = state.exhaustedDoors.size();
                bestMinimumConnectionDeficit = minimumConnectionDeficit(state);
            }
        }
    }

    private record OpenDoor(int nodeIndex, DoorSocket door, RoomTransform transform, Set<String> roomTags) {
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

    private record UndoRecord(int nodeIndex, int firstNewOpenDoor, int sourceNodeIndex, int previousSourceConnectionCount, int previousShortest, int previousLongest) {
    }

    private record WorldSlotSignature(boolean templateDoorMode, Direction3 facing, IntVector3 size, SocketType socketType, Set<String> tags, List<DoorSlotEntry> entries, DoorConnectionRules connectionRules, Set<String> roomTags) {
    }
}
