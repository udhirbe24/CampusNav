package edu.thapar.campusnav.algorithm;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Dijkstra's algorithm with a binary-heap priority queue — O((V + E) log V).
 *
 * <p>It expands nodes in order of their distance from the source, so the first time any
 * target is settled we already have the cheapest path to it. That makes it the right tool
 * for one-to-many questions such as "which emergency facility is closest?", where A*'s
 * single-target heuristic does not apply.
 */
public class DijkstraPathFinder implements PathFinder {

    private record QueueEntry(String node, double cost) {
    }

    @Override
    public Algorithm algorithm() {
        return Algorithm.DIJKSTRA;
    }

    @Override
    public Optional<PathResult> findPath(CampusGraph graph, String source, String target, SearchOptions options) {
        return findNearest(graph, source, Set.of(target), options);
    }

    /** Single search that stops at whichever of {@code targets} is cheapest to reach. */
    public Optional<PathResult> findNearest(CampusGraph graph, String source, Set<String> targets,
                                            SearchOptions options) {
        if (!graph.hasNode(source) || targets.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Double> best = new HashMap<>();
        Map<String, CampusGraph.Edge> cameBy = new HashMap<>();
        Set<String> settled = new HashSet<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::cost));

        best.put(source, 0.0);
        queue.add(new QueueEntry(source, 0.0));

        while (!queue.isEmpty()) {
            QueueEntry current = queue.poll();
            if (!settled.add(current.node())) {
                continue; // stale queue entry
            }
            if (targets.contains(current.node())) {
                return Optional.of(PathFinder.reconstruct(source, current.node(), cameBy, current.cost(),
                        settled.size(), Algorithm.DIJKSTRA));
            }
            for (CampusGraph.Edge edge : graph.outgoing(current.node())) {
                if (!options.canUse(edge) || settled.contains(edge.to())) {
                    continue;
                }
                double newCost = current.cost() + options.cost(edge);
                if (newCost < best.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) {
                    best.put(edge.to(), newCost);
                    cameBy.put(edge.to(), edge);
                    queue.add(new QueueEntry(edge.to(), newCost));
                }
            }
        }
        return Optional.empty();
    }
}
