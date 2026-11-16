package edu.thapar.campusnav.algorithm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Multi-criteria routing engine.
 *
 * <p>The caller never chooses an algorithm: {@link #chooseAlgorithm} picks the best one for the
 * question being asked, and the choice (with its reason) is reported back in the route summary.
 */
public class RouteOptimizer {

    /** Route modes offered in the app. */
    public enum Mode {
        SHORTEST, FASTEST, ACCESSIBLE, EMERGENCY;

        public static Mode from(String value) {
            if (value == null) {
                return SHORTEST;
            }
            return switch (value.trim().toLowerCase()) {
                case "fastest" -> FASTEST;
                case "accessible", "step-free", "wheelchair" -> ACCESSIBLE;
                case "emergency" -> EMERGENCY;
                default -> SHORTEST;
            };
        }

        public String key() {
            return name().toLowerCase();
        }

        SearchOptions options(boolean forceAccessible) {
            boolean accessible = forceAccessible || this == ACCESSIBLE;
            SearchOptions.Weight weight = (this == FASTEST || this == EMERGENCY)
                    ? SearchOptions.Weight.TIME : SearchOptions.Weight.DISTANCE;
            return SearchOptions.of(weight, accessible);
        }
    }

    /** The engine's automatic algorithm choice. */
    public record AlgorithmChoice(PathFinder finder, String reason) {
    }

    /** Cost multiplier applied to the primary route's walkways when looking for an alternative. */
    static final double ALTERNATIVE_PENALTY = 3.0;

    private final AStarPathFinder aStar = new AStarPathFinder();
    private final DijkstraPathFinder dijkstra = new DijkstraPathFinder();

    /**
     * Picks the algorithm for a query.
     * <ul>
     *   <li>One destination and a valid straight-line heuristic → A*: same optimal answer as
     *       Dijkstra, but explores fewer nodes because it is guided towards the destination.</li>
     *   <li>Several candidate destinations (e.g. nearest emergency facility) → Dijkstra: one
     *       search expanding outwards finds whichever destination is closest.</li>
     *   <li>If the heuristic could overestimate (a walkway recorded shorter than the straight line
     *       between its ends), A* is no longer guaranteed optimal, so Dijkstra is used.</li>
     * </ul>
     */
    public AlgorithmChoice chooseAlgorithm(CampusGraph graph, int destinationCount) {
        if (destinationCount > 1) {
            return new AlgorithmChoice(dijkstra,
                    "Several possible destinations, so Dijkstra searches outwards once and stops at the nearest.");
        }
        if (!isHeuristicAdmissible(graph)) {
            return new AlgorithmChoice(dijkstra,
                    "Some walkway lengths are shorter than the straight line, so Dijkstra is used to guarantee the shortest path.");
        }
        return new AlgorithmChoice(aStar,
                "One destination, so A* uses the straight-line distance to head towards it and explores fewer points than Dijkstra.");
    }

    /** True when no walkway is shorter than the straight-line distance between its endpoints. */
    static boolean isHeuristicAdmissible(CampusGraph graph) {
        for (CampusGraph.Node node : graph.nodes()) {
            for (CampusGraph.Edge edge : graph.outgoing(node.key())) {
                if (graph.hasNode(edge.to())
                        && edge.distanceMeters() + 0.5 < graph.straightLineDistance(edge.from(), edge.to())) {
                    return false;
                }
            }
        }
        return true;
    }

    public Optional<RouteResult> route(CampusGraph graph, String source, String target, Mode mode,
                                       boolean forceAccessible) {
        AlgorithmChoice choice = chooseAlgorithm(graph, 1);
        return choice.finder().findPath(graph, source, target, mode.options(forceAccessible))
                .map(path -> format(graph, path, mode, choice.reason()));
    }

    /**
     * Finds a genuinely different second route by making the primary route's walkways three
     * times as expensive. The detour may still share the first or last stretch when there is
     * no other way in or out, which is how real walking alternatives behave.
     */
    public Optional<RouteResult> alternative(CampusGraph graph, String source, String target,
                                             List<String> primaryPath, Mode mode, boolean forceAccessible) {
        SearchOptions base = mode.options(forceAccessible);
        AlgorithmChoice choice = chooseAlgorithm(graph, 1);

        List<String> primary = primaryPath;
        if (primary == null || primary.size() < 2) {
            primary = choice.finder().findPath(graph, source, target, base).map(PathResult::nodes).orElse(List.of());
        }
        if (primary.size() < 2) {
            return Optional.empty();
        }

        Set<String> primaryEdges = new HashSet<>();
        for (int i = 0; i < primary.size() - 1; i++) {
            primaryEdges.add(CampusGraph.edgeKey(primary.get(i), primary.get(i + 1)));
            primaryEdges.add(CampusGraph.edgeKey(primary.get(i + 1), primary.get(i)));
        }
        SearchOptions penalised = base.withPenalties(primaryEdges, ALTERNATIVE_PENALTY);

        List<String> finalPrimary = primary;
        return choice.finder().findPath(graph, source, target, penalised)
                .filter(path -> !path.nodes().equals(finalPrimary))
                .map(path -> format(graph, path, mode, choice.reason()));
    }

    /** Route to whichever of the given facility nodes can be reached fastest. */
    public Optional<RouteResult> nearestOf(CampusGraph graph, String source, Set<String> targets) {
        Set<String> reachable = new HashSet<>();
        for (String t : targets) {
            if (graph.hasNode(t)) {
                reachable.add(t);
            }
        }
        if (reachable.isEmpty()) {
            return Optional.empty();
        }
        AlgorithmChoice choice = chooseAlgorithm(graph, reachable.size());
        SearchOptions options = Mode.EMERGENCY.options(false);
        Optional<PathResult> path = reachable.size() == 1
                ? choice.finder().findPath(graph, source, reachable.iterator().next(), options)
                : dijkstra.findNearest(graph, source, reachable, options);
        return path.map(p -> format(graph, p, Mode.EMERGENCY, choice.reason()));
    }

    // ------------------------------------------------------------------ formatting

    private RouteResult format(CampusGraph graph, PathResult path, Mode mode, String reason) {
        List<List<Double>> coordinates = new ArrayList<>();
        List<RouteResult.NodeDetail> details = new ArrayList<>();
        for (String key : path.nodes()) {
            CampusGraph.Node n = graph.node(key);
            coordinates.add(List.of(n.longitude(), n.latitude()));
            details.add(new RouteResult.NodeDetail(n.key(), n.label(), n.latitude(), n.longitude(), n.type(),
                    n.accessible(), n.buildingId()));
        }

        double meters = Math.round(path.distanceMeters() * 10) / 10.0;
        double seconds = path.seconds();
        boolean stairs = path.hasStairs();

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", mode.key());
        properties.put("algorithm", path.algorithm().displayName());
        properties.put("distance_meters", meters);
        properties.put("distance_text", GeoUtils.formatDistance(meters));
        properties.put("duration_seconds", Math.round(seconds));
        properties.put("duration_text", GeoUtils.formatDuration(seconds));
        properties.put("has_stairs", stairs);

        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "LineString");
        geometry.put("coordinates", coordinates);

        Map<String, Object> geojson = new LinkedHashMap<>();
        geojson.put("type", "Feature");
        geojson.put("geometry", geometry);
        geojson.put("properties", properties);

        RouteResult.Summary summary = new RouteResult.Summary(meters, GeoUtils.formatDistance(meters),
                Math.round(seconds), GeoUtils.formatDuration(seconds), path.nodes().size(),
                path.algorithm().displayName(), reason, path.nodesExplored(), mode.key(), stairs, !stairs);

        return new RouteResult(path.nodes(), details, geojson, buildSteps(graph, path), summary, null);
    }

    /** Turn-by-turn directions; straight-ahead segments are merged into one step. */
    static List<RouteResult.Step> buildSteps(CampusGraph graph, PathResult path) {
        List<RouteResult.Step> steps = new ArrayList<>();
        List<String> nodes = path.nodes();
        if (nodes.size() == 1) {
            steps.add(new RouteResult.Step(1, "You are already at " + graph.node(nodes.get(0)).label() + ".",
                    0, "0 m", 0, nodes.get(0), nodes.get(0), false, false, true));
            return steps;
        }

        String action = null;
        String from = null;
        String to = null;
        double distance = 0;
        double stepBearing = 0;
        boolean stairs = false;
        boolean ramp = false;
        Double previousBearing = null;

        for (CampusGraph.Edge edge : path.edges()) {
            CampusGraph.Node u = graph.node(edge.from());
            CampusGraph.Node v = graph.node(edge.to());
            double segment = GeoUtils.haversine(u.latitude(), u.longitude(), v.latitude(), v.longitude());
            double bearing = GeoUtils.bearing(u.latitude(), u.longitude(), v.latitude(), v.longitude());

            String turn = previousBearing == null
                    ? "Head " + GeoUtils.compassDirection(bearing)
                    : turnInstruction(bearing - previousBearing);

            boolean straight = previousBearing != null && turn.equals("Continue straight");
            if (straight && action != null && !edge.hasStairs() && !stairs) {
                distance += edge.distanceMeters(); // merge into the current step
                to = edge.to();
                ramp |= edge.hasRamp();
            } else {
                if (action != null) {
                    steps.add(step(graph, steps.size() + 1, action, from, to, distance, stepBearing, stairs, ramp));
                }
                action = turn;
                from = edge.from();
                to = edge.to();
                distance = edge.distanceMeters();
                stepBearing = bearing;
                stairs = edge.hasStairs();
                ramp = edge.hasRamp();
            }
            if (segment > 0) {
                previousBearing = bearing;
            }
        }
        steps.add(step(graph, steps.size() + 1, action, from, to, distance, stepBearing, stairs, ramp));

        String destination = nodes.get(nodes.size() - 1);
        steps.add(new RouteResult.Step(steps.size() + 1, "Arrive at " + graph.node(destination).label(),
                0, "0 m", Math.round(stepBearing * 10) / 10.0, destination, destination, false, false, true));
        return steps;
    }

    private static RouteResult.Step step(CampusGraph graph, int number, String action, String from, String to,
                                         double meters, double bearing, boolean stairs, boolean ramp) {
        String instruction = action + " toward " + graph.node(to).label();
        if (stairs) {
            instruction += " (includes stairs)";
        } else if (ramp) {
            instruction += " (ramp available)";
        }
        double rounded = Math.round(meters * 10) / 10.0;
        return new RouteResult.Step(number, instruction, rounded, GeoUtils.formatDistance(rounded),
                Math.round(bearing * 10) / 10.0, from, to, stairs, ramp, false);
    }

    /** Converts a change of heading (degrees) into a walking instruction. */
    static String turnInstruction(double bearingChange) {
        double change = ((bearingChange + 180) % 360 + 360) % 360 - 180; // normalise to [-180, 180)
        double magnitude = Math.abs(change);
        if (magnitude < 25) {
            return "Continue straight";
        }
        String side = change > 0 ? "right" : "left";
        if (magnitude < 60) {
            return "Bear " + side;
        }
        if (magnitude < 135) {
            return "Turn " + side;
        }
        return "Make a sharp " + side;
    }
}
