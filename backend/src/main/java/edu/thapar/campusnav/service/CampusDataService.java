package edu.thapar.campusnav.service;

import edu.thapar.campusnav.algorithm.CampusGraph;
import edu.thapar.campusnav.model.Building;
import edu.thapar.campusnav.model.NavigationHistory;
import edu.thapar.campusnav.model.NavigationNode;
import edu.thapar.campusnav.model.Pathway;
import edu.thapar.campusnav.model.PathwayClosure;
import edu.thapar.campusnav.repository.BuildingRepository;
import edu.thapar.campusnav.repository.NavigationHistoryRepository;
import edu.thapar.campusnav.repository.NavigationNodeRepository;
import edu.thapar.campusnav.repository.PathwayClosureRepository;
import edu.thapar.campusnav.repository.PathwayRepository;
import edu.thapar.campusnav.repository.UserReportRepository;
import edu.thapar.campusnav.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-side queries for buildings, nodes, pathways, closures and admin analytics. */
@Service
public class CampusDataService {

    private final BuildingRepository buildings;
    private final NavigationNodeRepository nodes;
    private final PathwayRepository pathways;
    private final PathwayClosureRepository closures;
    private final UserRepository users;
    private final UserReportRepository reports;
    private final NavigationHistoryRepository history;
    private final GraphService graphService;

    public CampusDataService(BuildingRepository buildings, NavigationNodeRepository nodes, PathwayRepository pathways,
                             PathwayClosureRepository closures, UserRepository users, UserReportRepository reports,
                             NavigationHistoryRepository history, GraphService graphService) {
        this.buildings = buildings;
        this.nodes = nodes;
        this.pathways = pathways;
        this.closures = closures;
        this.users = users;
        this.reports = reports;
        this.history = history;
        this.graphService = graphService;
    }

    /** Active buildings, optionally filtered by category and a search term. */
    public List<Map<String, Object>> buildings(String category, String query) {
        String term = query == null ? null : query.trim().toLowerCase(Locale.ROOT);
        Map<Long, List<NavigationNode>> nodesByBuilding = nodes.findByCampusId(GraphService.CAMPUS_ID).stream()
                .filter(n -> n.getBuildingId() != null)
                .collect(Collectors.groupingBy(NavigationNode::getBuildingId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Building b : buildings.findByCampusIdAndActiveTrueOrderByNameAsc(GraphService.CAMPUS_ID)) {
            if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)
                    && !category.equalsIgnoreCase(b.getCategory())) {
                continue;
            }
            if (term != null && !term.isEmpty() && !matches(b, term)) {
                continue;
            }
            result.add(withEntrances(b, nodesByBuilding.getOrDefault(b.getId(), List.of())));
        }
        if (term != null && !term.isEmpty()) {
            // best matches first: names starting with the term, then the rest alphabetically
            result.sort(Comparator.comparing((Map<String, Object> m) ->
                    !((String) m.get("name")).toLowerCase(Locale.ROOT).startsWith(term)));
        }
        return result;
    }

    public Optional<Map<String, Object>> building(Long id) {
        return buildings.findById(id).map(b -> {
            List<NavigationNode> buildingNodes = nodes.findByBuildingId(b.getId());
            Map<String, Object> m = withEntrances(b, buildingNodes);
            m.put("nodes", buildingNodes.stream().map(n -> n.toMap(b.getName())).toList());
            m.put("rooms", List.of());
            return m;
        });
    }

    /**
     * Adds the routable point(s) for a building. Entrances come first; if a building has no
     * node marked as an entrance (sports grounds, parks ...) its landmark node is used, and as a
     * last resort the nearest walkway point. {@code node_key} is always set so every building on
     * the map can be routed to.
     */
    private Map<String, Object> withEntrances(Building b, List<NavigationNode> buildingNodes) {
        List<NavigationNode> ordered = new ArrayList<>(buildingNodes);
        ordered.sort(Comparator.comparing(n -> !n.isEntrance()));
        List<Map<String, Object>> entrances = ordered.stream().map(n -> n.toMap(b.getName()))
                .collect(Collectors.toCollection(ArrayList::new));

        String nodeKey = ordered.isEmpty() ? null : ordered.get(0).getNodeKey();
        if (nodeKey == null) {
            Optional<CampusGraph.Node> nearest = graphService.graph()
                    .nearestNode(b.getLatitude(), b.getLongitude(), false);
            if (nearest.isPresent()) {
                nodeKey = nearest.get().key();
                nodes.findByNodeKey(nodeKey).ifPresent(n -> entrances.add(n.toMap(b.getName())));
            }
        }
        Map<String, Object> m = b.toMap();
        m.put("node_key", nodeKey);
        m.put("entrances", entrances);
        return m;
    }

    private static boolean matches(Building b, String term) {
        return contains(b.getName(), term) || contains(b.getShortCode(), term) || contains(b.getDescription(), term);
    }

    private static boolean contains(String text, String term) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(term);
    }

    public List<Map<String, Object>> allNodes() {
        Map<Long, Building> byId = buildingsById();
        return nodes.findByCampusId(GraphService.CAMPUS_ID).stream().map(n -> {
            Building b = n.getBuildingId() != null ? byId.get(n.getBuildingId()) : null;
            Map<String, Object> m = n.toMap(b != null ? b.getName() : null);
            if (b != null) {
                m.put("building_code", b.getShortCode());
            }
            return m;
        }).toList();
    }

    public List<Map<String, Object>> allPathways() {
        Map<Long, NavigationNode> byId = nodesById();
        CampusGraph graph = graphService.graph();
        return pathways.findByCampusId(GraphService.CAMPUS_ID).stream()
                .map(p -> {
                    NavigationNode from = byId.get(p.getFromNodeId());
                    NavigationNode to = byId.get(p.getToNodeId());
                    Map<String, Object> m = p.toMap(from, to);
                    m.put("from_name", from != null && graph.hasNode(from.getNodeKey())
                            ? graph.node(from.getNodeKey()).label() : null);
                    m.put("to_name", to != null && graph.hasNode(to.getNodeKey())
                            ? graph.node(to.getNodeKey()).label() : null);
                    return m;
                })
                .toList();
    }

    public Map<String, Object> pathway(Pathway p) {
        return p.toMap(nodes.findById(p.getFromNodeId()).orElse(null), nodes.findById(p.getToNodeId()).orElse(null));
    }

    public List<Map<String, Object>> activeClosures() {
        Map<Long, NavigationNode> nodeMap = nodesById();
        List<Map<String, Object>> result = new ArrayList<>();
        for (PathwayClosure c : closures.findByActiveTrue()) {
            pathways.findById(c.getPathwayId()).ifPresent(p -> {
                Map<String, Object> m = c.toMap();
                NavigationNode from = nodeMap.get(p.getFromNodeId());
                NavigationNode to = nodeMap.get(p.getToNodeId());
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", p.getId());
                summary.put("from_node_key", from != null ? from.getNodeKey() : null);
                summary.put("to_node_key", to != null ? to.getNodeKey() : null);
                summary.put("distance_meters", p.getDistanceMeters());
                m.put("pathway", summary);
                result.add(m);
            });
        }
        return result;
    }

    /** Real numbers from the database for the admin dashboard. */
    public Map<String, Object> analytics() {
        Map<String, Object> usersByRole = new LinkedHashMap<>();
        for (String role : AuthService.ROLES) {
            long count = users.countByRole(role);
            if (count > 0) {
                usersByRole.put(role, count);
            }
        }

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("total_users", users.count());
        overview.put("users_by_role", usersByRole);
        overview.put("total_buildings", buildings.countByCampusId(GraphService.CAMPUS_ID));
        overview.put("total_nodes", nodes.countByCampusId(GraphService.CAMPUS_ID));
        overview.put("total_pathways", pathways.countByCampusId(GraphService.CAMPUS_ID));
        overview.put("blocked_pathways", pathways.countByCampusIdAndBlockedTrue(GraphService.CAMPUS_ID));
        overview.put("active_closures", closures.countByActiveTrue());
        overview.put("pending_reports", reports.countByStatus("PENDING"));
        overview.put("total_reports", reports.count());
        overview.put("total_navigation_queries", history.count());

        Map<String, NavigationNode> nodesByKey = nodes.findByCampusId(GraphService.CAMPUS_ID).stream()
                .collect(Collectors.toMap(NavigationNode::getNodeKey, Function.identity()));
        Map<Long, Building> buildingMap = buildingsById();
        List<Map<String, Object>> popular = new ArrayList<>();
        for (Object[] row : history.countByDestination()) {
            if (popular.size() == 5) {
                break;
            }
            String key = (String) row[0];
            NavigationNode node = nodesByKey.get(key);
            Building b = node != null && node.getBuildingId() != null ? buildingMap.get(node.getBuildingId()) : null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("node_key", key);
            m.put("name", b != null ? b.getName() + " (" + b.getShortCode() + ")" : key);
            m.put("count", row[1]);
            popular.add(m);
        }

        Map<String, Object> modes = new LinkedHashMap<>();
        for (Object[] row : history.countByMode()) {
            modes.put((String) row[0], row[1]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", overview);
        result.put("popular_destinations", popular);
        result.put("recent_routes", history.findTop10ByOrderByTimestampDesc().stream()
                .map(NavigationHistory::toMap).toList());
        result.put("mode_breakdown", modes);
        return result;
    }

    private Map<Long, NavigationNode> nodesById() {
        return nodes.findAll().stream().collect(Collectors.toMap(NavigationNode::getId, Function.identity()));
    }

    private Map<Long, Building> buildingsById() {
        return buildings.findAll().stream().collect(Collectors.toMap(Building::getId, Function.identity()));
    }
}
