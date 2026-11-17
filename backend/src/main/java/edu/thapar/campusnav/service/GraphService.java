package edu.thapar.campusnav.service;

import edu.thapar.campusnav.algorithm.CampusGraph;
import edu.thapar.campusnav.model.Building;
import edu.thapar.campusnav.model.NavigationNode;
import edu.thapar.campusnav.model.Pathway;
import edu.thapar.campusnav.repository.BuildingRepository;
import edu.thapar.campusnav.repository.NavigationNodeRepository;
import edu.thapar.campusnav.repository.PathwayRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the in-memory walkway graph from the database and caches it.
 * Anything that changes walkways (closing / reopening a path, adding a building) calls
 * {@link #invalidate()} so the next route is calculated on fresh data.
 */
@Service
public class GraphService {

    public static final String CAMPUS_ID = "thapar";

    private final NavigationNodeRepository nodeRepository;
    private final PathwayRepository pathwayRepository;
    private final BuildingRepository buildingRepository;

    private volatile CampusGraph cached;

    public GraphService(NavigationNodeRepository nodeRepository, PathwayRepository pathwayRepository,
                        BuildingRepository buildingRepository) {
        this.nodeRepository = nodeRepository;
        this.pathwayRepository = pathwayRepository;
        this.buildingRepository = buildingRepository;
    }

    public CampusGraph graph() {
        CampusGraph graph = cached;
        if (graph == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = build();
                }
                graph = cached;
            }
        }
        return graph;
    }

    public void invalidate() {
        cached = null;
    }

    private CampusGraph build() {
        Map<Long, Building> buildings = buildingRepository.findAll().stream()
                .collect(Collectors.toMap(Building::getId, Function.identity()));

        CampusGraph graph = new CampusGraph();
        Map<Long, NavigationNode> nodesById = new HashMap<>();
        for (NavigationNode n : nodeRepository.findByCampusId(CAMPUS_ID)) {
            nodesById.put(n.getId(), n);
            graph.addNode(new CampusGraph.Node(n.getNodeKey(), n.getLatitude(), n.getLongitude(), n.isAccessible(),
                    n.getNodeType(), label(n, buildings), n.getBuildingId()));
        }

        for (Pathway p : pathwayRepository.findByCampusId(CAMPUS_ID)) {
            NavigationNode from = nodesById.get(p.getFromNodeId());
            NavigationNode to = nodesById.get(p.getToNodeId());
            if (from == null || to == null) {
                continue;
            }
            graph.addEdge(new CampusGraph.Edge(from.getNodeKey(), to.getNodeKey(), p.getDistanceMeters(),
                    p.getEstimatedSeconds(), p.isHasStairs(), p.isHasRamp(), p.isAccessible(), p.isBlocked(),
                    p.getSurfaceType(), p.getId()), p.isBidirectional());
        }
        return graph;
    }

    /** Name used in directions: the building name, else the junction description. */
    private static String label(NavigationNode node, Map<Long, Building> buildings) {
        Building b = node.getBuildingId() != null ? buildings.get(node.getBuildingId()) : null;
        if (b != null) {
            return b.displayName();
        }
        if (node.getDescription() != null && !node.getDescription().isBlank()) {
            return node.getDescription();
        }
        return node.getNodeKey();
    }
}
