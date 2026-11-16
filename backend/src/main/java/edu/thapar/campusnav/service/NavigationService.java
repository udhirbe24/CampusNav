package edu.thapar.campusnav.service;

import edu.thapar.campusnav.algorithm.CampusGraph;
import edu.thapar.campusnav.algorithm.RouteOptimizer;
import edu.thapar.campusnav.algorithm.RouteResult;
import edu.thapar.campusnav.model.EmergencyFacility;
import edu.thapar.campusnav.model.NavigationHistory;
import edu.thapar.campusnav.model.NavigationNode;
import edu.thapar.campusnav.repository.EmergencyFacilityRepository;
import edu.thapar.campusnav.repository.NavigationHistoryRepository;
import edu.thapar.campusnav.repository.NavigationNodeRepository;
import edu.thapar.campusnav.web.ApiException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Route calculation, alternatives, emergency routing and history logging. */
@Service
public class NavigationService {

    private final GraphService graphService;
    private final NavigationNodeRepository nodeRepository;
    private final EmergencyFacilityRepository facilityRepository;
    private final NavigationHistoryRepository historyRepository;
    private final RouteOptimizer optimizer = new RouteOptimizer();

    public NavigationService(GraphService graphService, NavigationNodeRepository nodeRepository,
                             EmergencyFacilityRepository facilityRepository,
                             NavigationHistoryRepository historyRepository) {
        this.graphService = graphService;
        this.nodeRepository = nodeRepository;
        this.facilityRepository = facilityRepository;
        this.historyRepository = historyRepository;
    }

    public RouteResult calculateRoute(String from, String to, String modeName, boolean accessibleOnly, Long userId) {
        CampusGraph graph = graphService.graph();
        requireNode(graph, from, "starting point");
        requireNode(graph, to, "destination");
        RouteOptimizer.Mode mode = RouteOptimizer.Mode.from(modeName);
        if (mode == RouteOptimizer.Mode.EMERGENCY) {
            mode = RouteOptimizer.Mode.FASTEST;
        }
        boolean stepFree = accessibleOnly || mode == RouteOptimizer.Mode.ACCESSIBLE;

        RouteResult result = optimizer.route(graph, from, to, mode, accessibleOnly)
                .orElseThrow(() -> ApiException.notFound(stepFree
                        ? "No step-free route connects these two places. Try the Walk mode instead."
                        : "No open walkway connects these two places right now (a path may be closed)."));
        logHistory(userId, from, to, result);
        return result;
    }

    public RouteResult alternativeRoute(String from, String to, List<String> primaryPath, String modeName,
                                        boolean accessibleOnly) {
        CampusGraph graph = graphService.graph();
        requireNode(graph, from, "starting point");
        requireNode(graph, to, "destination");
        return optimizer.alternative(graph, from, to, primaryPath, RouteOptimizer.Mode.from(modeName), accessibleOnly)
                .orElseThrow(() -> ApiException.notFound("No different route exists between these two places."));
    }

    /** Fastest route to the nearest active emergency facility (optionally of one type). */
    public RouteResult emergencyRoute(String from, String facilityType) {
        CampusGraph graph = graphService.graph();
        requireNode(graph, from, "starting point");

        List<EmergencyFacility> facilities = (facilityType == null || facilityType.isBlank())
                ? facilityRepository.findByActiveTrue()
                : facilityRepository.findByActiveTrueAndFacilityType(facilityType.trim().toUpperCase());

        Map<String, EmergencyFacility> byNodeKey = new HashMap<>();
        for (EmergencyFacility f : facilities) {
            if (f.getNodeId() == null) {
                continue;
            }
            nodeRepository.findById(f.getNodeId())
                    .ifPresent(n -> byNodeKey.putIfAbsent(n.getNodeKey(), f));
        }
        if (byNodeKey.isEmpty()) {
            throw ApiException.notFound("No active emergency facility is mapped on campus.");
        }

        RouteResult result = optimizer.nearestOf(graph, from, byNodeKey.keySet())
                .orElseThrow(() -> ApiException.notFound("Could not reach any emergency facility from here."));
        String reached = result.pathNodes().get(result.pathNodes().size() - 1);
        return result.withEmergencyFacility(byNodeKey.get(reached).toMap());
    }

    public Optional<NavigationNode> nearestNode(double latitude, double longitude) {
        return graphService.graph().nearestNode(latitude, longitude, false)
                .flatMap(n -> nodeRepository.findByNodeKey(n.key()));
    }

    public List<Map<String, Object>> history(Long userId) {
        return historyRepository.findTop50ByUserIdOrderByTimestampDesc(userId).stream()
                .map(NavigationHistory::toMap).toList();
    }

    private static void requireNode(CampusGraph graph, String key, String what) {
        if (key == null || key.isBlank()) {
            throw ApiException.badRequest("Please choose a " + what + ".");
        }
        if (!graph.hasNode(key)) {
            throw ApiException.notFound("Unknown " + what + " (" + key + ").");
        }
    }

    private void logHistory(Long userId, String from, String to, RouteResult result) {
        NavigationHistory h = new NavigationHistory();
        h.setUserId(userId);
        h.setFromNodeKey(from);
        h.setToNodeKey(to);
        h.setAlgorithmUsed(result.summary().algorithm());
        h.setRouteMode(result.summary().mode());
        h.setDistanceMeters(result.summary().totalDistanceMeters());
        h.setDurationSeconds(result.summary().totalTimeSeconds());
        historyRepository.save(h);
    }
}
