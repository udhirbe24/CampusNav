package edu.thapar.campusnav.web;

import edu.thapar.campusnav.model.EmergencyFacility;
import edu.thapar.campusnav.repository.EmergencyFacilityRepository;
import edu.thapar.campusnav.service.NavigationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Route calculation. Available under both /api/navigation and /api/routes.
 * The client only says where to go and how (walk / fastest / step-free);
 * the engine chooses the algorithm itself.
 */
@RestController
@RequestMapping({"/api/navigation", "/api/routes"})
public class NavigationController {

    private final NavigationService navigation;
    private final EmergencyFacilityRepository facilities;
    private final SessionAuth sessionAuth;

    public NavigationController(NavigationService navigation, EmergencyFacilityRepository facilities,
                                SessionAuth sessionAuth) {
        this.navigation = navigation;
        this.facilities = facilities;
        this.sessionAuth = sessionAuth;
    }

    @PostMapping({"/calculate", "/route"})
    public ResponseEntity<Map<String, Object>> calculate(@RequestBody RequestBodies.RouteRequest body,
                                                         HttpServletRequest request) {
        Long userId = sessionAuth.currentUser(request).map(u -> u.getId()).orElse(null);
        return ApiResponse.ok(navigation.calculateRoute(body.fromNode(), body.toNode(), body.mode(),
                body.stepFree(), userId));
    }

    @PostMapping("/alternative")
    public ResponseEntity<Map<String, Object>> alternative(@RequestBody RequestBodies.RouteRequest body) {
        return ApiResponse.ok(navigation.alternativeRoute(body.fromNode(), body.toNode(), body.primaryPathNodes(),
                body.mode(), body.stepFree()));
    }

    @GetMapping("/emergency")
    public ResponseEntity<Map<String, Object>> emergencyFacilities() {
        return ApiResponse.ok(facilities.findByActiveTrue().stream().map(EmergencyFacility::toMap).toList());
    }

    @PostMapping("/emergency")
    public ResponseEntity<Map<String, Object>> emergency(@RequestBody RequestBodies.RouteRequest body) {
        return ApiResponse.ok(navigation.emergencyRoute(body.fromNode(), body.facilityType()));
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(HttpServletRequest request) {
        return ApiResponse.ok(navigation.history(sessionAuth.requireUser(request).getId()));
    }

    @GetMapping("/nearest-node")
    public ResponseEntity<Map<String, Object>> nearestGet(@RequestParam(required = false) Double lat,
                                                          @RequestParam(required = false) Double lon) {
        return nearest(lat, lon);
    }

    @PostMapping("/nearest-node")
    public ResponseEntity<Map<String, Object>> nearestPost(@RequestBody RequestBodies.LocationRequest body) {
        return nearest(body.latitude(), body.longitude());
    }

    private ResponseEntity<Map<String, Object>> nearest(Double lat, Double lon) {
        if (lat == null || lon == null) {
            throw ApiException.badRequest("latitude and longitude are required.");
        }
        return navigation.nearestNode(lat, lon).map(node -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("node_key", node.getNodeKey());
            data.put("node", node.toMap(null));
            return ApiResponse.ok(data);
        }).orElseThrow(() -> ApiException.notFound("Could not locate a nearby walkway."));
    }
}
