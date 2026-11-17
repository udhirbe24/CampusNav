package edu.thapar.campusnav.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Dijkstra, A* and the route optimiser on a small hand-made campus:
 *
 * <pre>
 *   A ---100--- B ---100--- C
 *   |           |(stairs)   |
 *  150         60          150
 *   |           |           |
 *   D ---100--- E ---100--- F
 * </pre>
 * Coordinates are spaced ~100 m apart so the straight-line heuristic is realistic.
 */
class RouteAlgorithmsTest {

    private CampusGraph graph;
    private final AStarPathFinder aStar = new AStarPathFinder();
    private final DijkstraPathFinder dijkstra = new DijkstraPathFinder();
    private final RouteOptimizer optimizer = new RouteOptimizer();

    private static final double LAT = 30.3540;
    private static final double LON = 76.3600;
    private static final double STEP = 0.0009; // ~100 m in both directions here

    @BeforeEach
    void buildGraph() {
        graph = new CampusGraph();
        node("A", 1, 0);
        node("B", 1, 1);
        node("C", 1, 2);
        node("D", 0, 0);
        node("E", 0, 1);
        node("F", 0, 2);
        edge("A", "B", 100, false);
        edge("B", "C", 100, false);
        edge("A", "D", 150, false);
        edge("B", "E", 110, true);   // short cut, but has stairs
        edge("C", "F", 150, false);
        edge("D", "E", 100, false);
        edge("E", "F", 100, false);
    }

    private void node(String key, int row, int col) {
        graph.addNode(new CampusGraph.Node(key, LAT + row * STEP, LON + col * STEP, true, "intersection",
                "Point " + key, null));
    }

    private void edge(String a, String b, double meters, boolean stairs) {
        graph.addEdge(new CampusGraph.Edge(a, b, meters, meters / 1.4, stairs, false, !stairs, false,
                "paved", null), true);
    }

    private SearchOptions shortest() {
        return SearchOptions.of(SearchOptions.Weight.DISTANCE, false);
    }

    @Test
    void dijkstraFindsShortestPath() {
        PathResult r = dijkstra.findPath(graph, "A", "F", shortest()).orElseThrow();
        assertThat(r.distanceMeters()).isEqualTo(310.0); // A-B-E-F via stairs
        assertThat(r.nodes()).containsExactly("A", "B", "E", "F");
    }

    @Test
    void aStarMatchesDijkstraForEveryPair() {
        for (CampusGraph.Node from : graph.nodes()) {
            for (CampusGraph.Node to : graph.nodes()) {
                double d = dijkstra.findPath(graph, from.key(), to.key(), shortest()).orElseThrow().cost();
                double a = aStar.findPath(graph, from.key(), to.key(), shortest()).orElseThrow().cost();
                assertThat(a).as(from.key() + " -> " + to.key()).isEqualTo(d);
            }
        }
    }

    @Test
    void stepFreeRouteAvoidsStairs() {
        PathResult r = aStar.findPath(graph, "A", "F", SearchOptions.of(SearchOptions.Weight.DISTANCE, true))
                .orElseThrow();
        assertThat(r.hasStairs()).isFalse();
        assertThat(r.distanceMeters()).isEqualTo(350.0);
    }

    @Test
    void blockedPathIsAvoided() {
        CampusGraph g = new CampusGraph();
        graph.nodes().forEach(g::addNode);
        for (CampusGraph.Node n : graph.nodes()) {
            for (CampusGraph.Edge e : graph.outgoing(n.key())) {
                boolean block = e.from().equals("B") && e.to().equals("E") || e.from().equals("E") && e.to().equals("B");
                g.addEdge(new CampusGraph.Edge(e.from(), e.to(), e.distanceMeters(), e.seconds(), e.hasStairs(),
                        e.hasRamp(), e.accessible(), block, e.surface(), null), false);
            }
        }
        PathResult r = aStar.findPath(g, "A", "F", shortest()).orElseThrow();
        assertThat(r.nodes()).doesNotContainSequence("B", "E");
    }

    @Test
    void unreachableReturnsEmpty() {
        graph.addNode(new CampusGraph.Node("Z", LAT, LON + 0.01, true, "intersection", "Island", null));
        assertThat(aStar.findPath(graph, "A", "Z", shortest())).isEmpty();
        assertThat(dijkstra.findPath(graph, "A", "Z", shortest())).isEmpty();
    }

    @Test
    void sameStartAndEndIsZeroLength() {
        PathResult r = aStar.findPath(graph, "C", "C", shortest()).orElseThrow();
        assertThat(r.nodes()).containsExactly("C");
        assertThat(r.distanceMeters()).isZero();
    }

    @Test
    void aStarExploresNoMoreNodesThanDijkstra() {
        PathResult a = aStar.findPath(graph, "D", "C", shortest()).orElseThrow();
        PathResult d = dijkstra.findPath(graph, "D", "C", shortest()).orElseThrow();
        assertThat(a.nodesExplored()).isLessThanOrEqualTo(d.nodesExplored());
    }

    @Test
    void engineChoosesAStarForOneDestinationAndDijkstraForMany() {
        assertThat(optimizer.chooseAlgorithm(graph, 1).finder().algorithm()).isEqualTo(Algorithm.ASTAR);
        assertThat(optimizer.chooseAlgorithm(graph, 3).finder().algorithm()).isEqualTo(Algorithm.DIJKSTRA);
    }

    @Test
    void engineFallsBackToDijkstraWhenHeuristicWouldOverestimate() {
        edge("A", "F", 5, false); // impossible: shorter than the straight line
        assertThat(optimizer.chooseAlgorithm(graph, 1).finder().algorithm()).isEqualTo(Algorithm.DIJKSTRA);
    }

    @Test
    void nearestOfSeveralTargetsPicksClosest() {
        RouteResult r = optimizer.nearestOf(graph, "A", Set.of("C", "D")).orElseThrow();
        // A-D is 150 m, A-B-C is 200 m, so D must win, and Dijkstra is the engine's choice
        assertThat(r.pathNodes()).endsWith("D");
        assertThat(r.summary().algorithm()).isEqualTo("Dijkstra");
    }

    @Test
    void alternativeRouteIsDifferentFromPrimary() {
        RouteResult primary = optimizer.route(graph, "A", "F", RouteOptimizer.Mode.SHORTEST, false).orElseThrow();
        RouteResult alt = optimizer.alternative(graph, "A", "F", primary.pathNodes(),
                RouteOptimizer.Mode.SHORTEST, false).orElseThrow();
        assertThat(alt.pathNodes()).isNotEqualTo(primary.pathNodes());
        assertThat(alt.summary().totalDistanceMeters()).isGreaterThanOrEqualTo(primary.summary().totalDistanceMeters());
    }

    @Test
    void routeSummaryReportsAutoSelectedAlgorithm() {
        RouteResult r = optimizer.route(graph, "A", "F", RouteOptimizer.Mode.FASTEST, false).orElseThrow();
        assertThat(r.summary().algorithm()).isEqualTo("A* Search");
        assertThat(r.summary().algorithmReason()).isNotBlank();
        assertThat(r.steps().get(r.steps().size() - 1).isArrival()).isTrue();
        assertThat(r.steps().get(0).instruction()).startsWith("Head ");
    }

    @Test
    void fastestPrefersFewerJunctionsWhenDistancesAreClose() {
        // P -> Q direct is 105 m; P -> X -> Y -> Q is 100 m but passes two extra junctions
        CampusGraph g = new CampusGraph();
        String[] keys = {"P", "X", "Y", "Q"};
        for (int i = 0; i < keys.length; i++) {
            g.addNode(new CampusGraph.Node(keys[i], LAT, LON + i * 0.0003, true, "intersection", keys[i], null));
        }
        g.addEdge(new CampusGraph.Edge("P", "Q", 105, 75, false, false, true, false, "paved", null), true);
        g.addEdge(new CampusGraph.Edge("P", "X", 34, 24, false, false, true, false, "paved", null), true);
        g.addEdge(new CampusGraph.Edge("X", "Y", 33, 24, false, false, true, false, "paved", null), true);
        g.addEdge(new CampusGraph.Edge("Y", "Q", 33, 24, false, false, true, false, "paved", null), true);

        RouteResult shortest = optimizer.route(g, "P", "Q", RouteOptimizer.Mode.SHORTEST, false).orElseThrow();
        RouteResult fastest = optimizer.route(g, "P", "Q", RouteOptimizer.Mode.FASTEST, false).orElseThrow();
        assertThat(shortest.pathNodes()).containsExactly("P", "X", "Y", "Q");
        assertThat(fastest.pathNodes()).containsExactly("P", "Q");
    }

    @Test
    void longStraightStepsNameTheLandmarksPassed() {
        CampusGraph g = new CampusGraph();
        g.addNode(new CampusGraph.Node("S", LAT, LON, true, "entrance", "Hostel A", 1L));
        g.addNode(new CampusGraph.Node("M", LAT, LON + STEP, true, "entrance", "Library", 2L));
        g.addNode(new CampusGraph.Node("T", LAT, LON + 2 * STEP, true, "entrance", "CS Block", 3L));
        g.addEdge(new CampusGraph.Edge("S", "M", 100, 72, false, false, true, false, "paved", null), true);
        g.addEdge(new CampusGraph.Edge("M", "T", 100, 72, false, false, true, false, "paved", null), true);

        RouteResult r = optimizer.route(g, "S", "T", RouteOptimizer.Mode.SHORTEST, false).orElseThrow();
        assertThat(r.steps()).hasSize(2); // one merged walking step + arrival
        assertThat(r.steps().get(0).instruction()).isEqualTo("Head east toward CS Block, passing Library");
        assertThat(r.steps().get(0).distanceMeters()).isEqualTo(200.0);
    }

    @Test
    void turnInstructions() {
        assertThat(RouteOptimizer.turnInstruction(5)).isEqualTo("Continue straight");
        assertThat(RouteOptimizer.turnInstruction(90)).isEqualTo("Turn right");
        assertThat(RouteOptimizer.turnInstruction(-90)).isEqualTo("Turn left");
        assertThat(RouteOptimizer.turnInstruction(40)).isEqualTo("Bear right");
        assertThat(RouteOptimizer.turnInstruction(-170)).isEqualTo("Make a sharp left");
        assertThat(RouteOptimizer.turnInstruction(350)).isEqualTo("Continue straight");
    }

    @Test
    void haversineIsAccurate() {
        // Gate 1 to Gate 3 of Thapar is roughly 1.4 km apart
        double d = GeoUtils.haversine(30.3540, 76.3715, 30.3538, 76.3570);
        assertThat(d).isBetween(1380.0, 1410.0);
        assertThat(Optional.of(GeoUtils.formatDistance(d))).contains(String.format("%.2f km", d / 1000));
        assertThat(List.of(GeoUtils.formatDuration(30), GeoUtils.formatDuration(61))).containsExactly("< 1 min", "2 min");
    }
}
