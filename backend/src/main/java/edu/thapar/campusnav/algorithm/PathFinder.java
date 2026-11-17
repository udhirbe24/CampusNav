package edu.thapar.campusnav.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Common contract for the shortest-path algorithms. */
public interface PathFinder {

    Algorithm algorithm();

    /** Finds the cheapest path from {@code source} to {@code target}, or empty if unreachable. */
    Optional<PathResult> findPath(CampusGraph graph, String source, String target, SearchOptions options);

    /** Rebuilds the node/edge lists by walking the predecessor map back from the target. */
    static PathResult reconstruct(String source, String target, Map<String, CampusGraph.Edge> cameBy,
                                  double cost, int explored, Algorithm algorithm) {
        List<String> nodes = new ArrayList<>();
        List<CampusGraph.Edge> edges = new ArrayList<>();
        String current = target;
        nodes.add(current);
        while (!current.equals(source)) {
            CampusGraph.Edge edge = cameBy.get(current);
            edges.add(edge);
            current = edge.from();
            nodes.add(current);
        }
        Collections.reverse(nodes);
        Collections.reverse(edges);
        return new PathResult(List.copyOf(nodes), List.copyOf(edges), cost, explored, algorithm);
    }
}
