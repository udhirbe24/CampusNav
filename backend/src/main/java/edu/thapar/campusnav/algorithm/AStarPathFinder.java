package edu.thapar.campusnav.algorithm;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* search: f(n) = g(n) + h(n).
 *
 * <p>g(n) is the exact cost from the source; h(n) is the straight-line (Haversine) distance to
 * the target — or that distance divided by the fastest walking speed in the graph when
 * optimising for time. A walkway can never be shorter than the straight line between its ends,
 * so h never overestimates (admissible) and A* returns the same optimal path as Dijkstra while
 * exploring fewer nodes, because it is pulled towards the destination.
 */
public class AStarPathFinder implements PathFinder {

    private record QueueEntry(String node, double g, double f) {
    }

    @Override
    public Algorithm algorithm() {
        return Algorithm.ASTAR;
    }

    /** Lower bound on the remaining cost from {@code node} to {@code target}. */
    static double heuristic(CampusGraph graph, String node, String target, SearchOptions options) {
        double meters = graph.straightLineDistance(node, target);
        return options.weight() == SearchOptions.Weight.TIME ? meters / graph.maxSpeed() : meters;
    }

    @Override
    public Optional<PathResult> findPath(CampusGraph graph, String source, String target, SearchOptions options) {
        if (!graph.hasNode(source) || !graph.hasNode(target)) {
            return Optional.empty();
        }

        Map<String, Double> gScore = new HashMap<>();
        Map<String, CampusGraph.Edge> cameBy = new HashMap<>();
        Set<String> closed = new HashSet<>();
        PriorityQueue<QueueEntry> open = new PriorityQueue<>(
                Comparator.comparingDouble(QueueEntry::f).thenComparingDouble(e -> -e.g()));

        gScore.put(source, 0.0);
        open.add(new QueueEntry(source, 0.0, heuristic(graph, source, target, options)));

        while (!open.isEmpty()) {
            QueueEntry current = open.poll();
            if (!closed.add(current.node())) {
                continue; // stale queue entry
            }
            if (current.node().equals(target)) {
                return Optional.of(PathFinder.reconstruct(source, target, cameBy, current.g(),
                        closed.size(), Algorithm.ASTAR));
            }
            for (CampusGraph.Edge edge : graph.outgoing(current.node())) {
                if (!options.canUse(edge) || closed.contains(edge.to())) {
                    continue;
                }
                double tentativeG = current.g() + options.cost(edge);
                if (tentativeG < gScore.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) {
                    gScore.put(edge.to(), tentativeG);
                    cameBy.put(edge.to(), edge);
                    open.add(new QueueEntry(edge.to(), tentativeG,
                            tentativeG + heuristic(graph, edge.to(), target, options)));
                }
            }
        }
        return Optional.empty();
    }
}
