package edu.thapar.campusnav.algorithm;

import java.util.List;

/**
 * Raw output of a path search.
 *
 * @param nodes         node keys from source to target
 * @param edges         edges walked, in order (one fewer than nodes)
 * @param cost          total cost under the search's weighting (includes penalties)
 * @param nodesExplored how many nodes the algorithm settled — shows how much work it did
 * @param algorithm     which algorithm produced this path
 */
public record PathResult(List<String> nodes, List<CampusGraph.Edge> edges, double cost,
                         int nodesExplored, Algorithm algorithm) {

    public double distanceMeters() {
        return edges.stream().mapToDouble(CampusGraph.Edge::distanceMeters).sum();
    }

    public double seconds() {
        return edges.stream().mapToDouble(CampusGraph.Edge::seconds).sum();
    }

    public boolean hasStairs() {
        return edges.stream().anyMatch(CampusGraph.Edge::hasStairs);
    }

    public String target() {
        return nodes.get(nodes.size() - 1);
    }
}
