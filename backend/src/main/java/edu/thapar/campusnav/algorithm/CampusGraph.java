package edu.thapar.campusnav.algorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Weighted graph of the campus walkway network, stored as an adjacency list.
 * Vertices are junctions / building entrances, edges are walkable pathways.
 */
public class CampusGraph {

    /** A point on the walkway network. {@code label} is a human-friendly name used in directions. */
    public record Node(String key, double latitude, double longitude, boolean accessible,
                       String type, String label, Long buildingId) {
    }

    /** One direction of a walkway between two nodes. */
    public record Edge(String from, String to, double distanceMeters, double seconds,
                       boolean hasStairs, boolean hasRamp, boolean accessible, boolean blocked,
                       String surface, Long pathwayId) {

        public double speedMetersPerSecond() {
            return seconds > 0 ? distanceMeters / seconds : GeoUtils.WALKING_SPEED_MPS;
        }

        Edge reversed() {
            return new Edge(to, from, distanceMeters, seconds, hasStairs, hasRamp, accessible, blocked,
                    surface, pathwayId);
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, List<Edge>> adjacency = new HashMap<>();
    private final Map<String, Edge> edgeIndex = new HashMap<>();
    private double maxSpeed = GeoUtils.WALKING_SPEED_MPS;

    public void addNode(Node node) {
        nodes.put(node.key(), node);
        adjacency.computeIfAbsent(node.key(), k -> new ArrayList<>());
    }

    public void addEdge(Edge edge, boolean bidirectional) {
        addDirected(edge);
        if (bidirectional) {
            addDirected(edge.reversed());
        }
    }

    private void addDirected(Edge edge) {
        adjacency.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
        edgeIndex.put(edgeKey(edge.from(), edge.to()), edge);
        maxSpeed = Math.max(maxSpeed, edge.speedMetersPerSecond());
    }

    public static String edgeKey(String from, String to) {
        return from + "->" + to;
    }

    public boolean hasNode(String key) {
        return key != null && nodes.containsKey(key);
    }

    public Node node(String key) {
        return nodes.get(key);
    }

    public Collection<Node> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public List<Edge> outgoing(String key) {
        return adjacency.getOrDefault(key, List.of());
    }

    public Optional<Edge> edge(String from, String to) {
        return Optional.ofNullable(edgeIndex.get(edgeKey(from, to)));
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edgeIndex.size();
    }

    /**
     * Fastest walking speed found on any edge. Dividing straight-line distance by this
     * gives a lower bound on travel time, which keeps the A* time heuristic admissible.
     */
    public double maxSpeed() {
        return maxSpeed;
    }

    /** Straight-line distance between two nodes in meters. */
    public double straightLineDistance(String a, String b) {
        Node na = nodes.get(a);
        Node nb = nodes.get(b);
        return GeoUtils.haversine(na.latitude(), na.longitude(), nb.latitude(), nb.longitude());
    }

    /** Closest node to an arbitrary coordinate (e.g. the user's GPS position). */
    public Optional<Node> nearestNode(double latitude, double longitude, boolean accessibleOnly) {
        Node best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Node n : nodes.values()) {
            if (accessibleOnly && !n.accessible()) {
                continue;
            }
            if (outgoing(n.key()).isEmpty()) {
                continue; // isolated points can't start a route
            }
            double d = GeoUtils.haversine(latitude, longitude, n.latitude(), n.longitude());
            if (d < bestDistance) {
                bestDistance = d;
                best = n;
            }
        }
        return Optional.ofNullable(best);
    }
}
