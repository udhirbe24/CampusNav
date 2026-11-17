package edu.thapar.campusnav.algorithm;

import java.util.Map;
import java.util.Set;

/**
 * What a path search should optimise and which edges it may use.
 *
 * @param weight          minimise distance or walking time
 * @param accessibleOnly  only use step-free, wheelchair-accessible walkways
 * @param penalties       extra cost multipliers per edge key (used to find alternative routes)
 */
public record SearchOptions(Weight weight, boolean accessibleOnly, Map<String, Double> penalties) {

    public enum Weight { DISTANCE, TIME }

    public static SearchOptions of(Weight weight, boolean accessibleOnly) {
        return new SearchOptions(weight, accessibleOnly, Map.of());
    }

    /** Whether the search is allowed to walk along this edge at all. */
    public boolean canUse(CampusGraph.Edge edge) {
        if (edge.blocked()) {
            return false;
        }
        return !accessibleOnly || (edge.accessible() && !edge.hasStairs());
    }

    /**
     * Seconds lost at every junction (looking for traffic, crossing, choosing a turn).
     * It makes the Fastest mode prefer routes with fewer junctions, not just fewer meters.
     */
    public static final double JUNCTION_DELAY_SECONDS = 5.0;

    /** Cost of walking along an edge under this search's weighting. */
    public double cost(CampusGraph.Edge edge) {
        double base = weight == Weight.TIME ? edge.seconds() + JUNCTION_DELAY_SECONDS : edge.distanceMeters();
        Double penalty = penalties.get(CampusGraph.edgeKey(edge.from(), edge.to()));
        return penalty == null ? base : base * penalty;
    }

    public SearchOptions withPenalties(Set<String> edgeKeys, double multiplier) {
        Map<String, Double> map = new java.util.HashMap<>(penalties);
        edgeKeys.forEach(k -> map.put(k, multiplier));
        return new SearchOptions(weight, accessibleOnly, Map.copyOf(map));
    }
}
