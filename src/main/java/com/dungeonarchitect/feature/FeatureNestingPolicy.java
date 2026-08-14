package com.dungeonarchitect.feature;

/** Shared, reloadable limits for recursive feature expansion. */
public final class FeatureNestingPolicy {
    public static final int DEFAULT_MAX_DEPTH = 4;
    public static final int DEFAULT_MAX_EXPANDED_PLACEMENTS = 256;

    private volatile int maxDepth;
    private volatile int maxExpandedPlacements;

    public FeatureNestingPolicy() {
        this(DEFAULT_MAX_DEPTH, DEFAULT_MAX_EXPANDED_PLACEMENTS);
    }

    public FeatureNestingPolicy(int maxDepth, int maxExpandedPlacements) {
        update(maxDepth, maxExpandedPlacements);
    }

    public void update(int maxDepth, int maxExpandedPlacements) {
        if (maxDepth <= 0 || maxExpandedPlacements <= 0) {
            throw new IllegalArgumentException("Feature nesting limits must be positive");
        }
        this.maxDepth = maxDepth;
        this.maxExpandedPlacements = maxExpandedPlacements;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public int maxExpandedPlacements() {
        return maxExpandedPlacements;
    }
}
