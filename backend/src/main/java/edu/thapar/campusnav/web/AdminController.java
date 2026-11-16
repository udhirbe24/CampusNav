package edu.thapar.campusnav.web;

import edu.thapar.campusnav.algorithm.GeoUtils;
import edu.thapar.campusnav.model.Building;
import edu.thapar.campusnav.model.EmergencyFacility;
import edu.thapar.campusnav.model.NavigationNode;
import edu.thapar.campusnav.model.Pathway;
import edu.thapar.campusnav.model.User;
import edu.thapar.campusnav.model.UserReport;
import edu.thapar.campusnav.repository.BuildingRepository;
import edu.thapar.campusnav.repository.EmergencyFacilityRepository;
import edu.thapar.campusnav.repository.NavigationNodeRepository;
import edu.thapar.campusnav.repository.PathwayRepository;
import edu.thapar.campusnav.repository.UserReportRepository;
import edu.thapar.campusnav.repository.UserRepository;
import edu.thapar.campusnav.service.AuthService;
import edu.thapar.campusnav.service.CampusDataService;
import edu.thapar.campusnav.service.GraphService;
import edu.thapar.campusnav.service.PathwayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Admin-only endpoints. Every method first checks the logged-in user is an admin. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Set<String> REPORT_STATUSES = Set.of("PENDING", "REVIEWED", "RESOLVED");

    private final SessionAuth sessionAuth;
    private final CampusDataService data;
    private final PathwayService pathwayService;
    private final GraphService graphService;
    private final BuildingRepository buildings;
    private final NavigationNodeRepository nodes;
    private final PathwayRepository pathways;
    private final UserReportRepository reports;
    private final EmergencyFacilityRepository facilities;
    private final UserRepository users;

    public AdminController(SessionAuth sessionAuth, CampusDataService data, PathwayService pathwayService,
                           GraphService graphService, BuildingRepository buildings, NavigationNodeRepository nodes,
                           PathwayRepository pathways, UserReportRepository reports,
                           EmergencyFacilityRepository facilities, UserRepository users) {
        this.sessionAuth = sessionAuth;
        this.data = data;
        this.pathwayService = pathwayService;
        this.graphService = graphService;
        this.buildings = buildings;
        this.nodes = nodes;
        this.pathways = pathways;
        this.reports = reports;
        this.facilities = facilities;
        this.users = users;
    }

    // ---------------------------------------------------------------- analytics

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> analytics(HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        return ApiResponse.ok(data.analytics());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        return ApiResponse.ok(data.analytics().get("overview"));
    }

    // ---------------------------------------------------------------- buildings

    @PostMapping("/locations")
    @Transactional
    public ResponseEntity<Map<String, Object>> createLocation(@RequestBody Map<String, Object> body,
                                                              HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        String name = RequestBodies.str(body, "name");
        String code = RequestBodies.str(body, "short_code");
        Double lat = RequestBodies.num(body, "latitude");
        Double lon = RequestBodies.num(body, "longitude");
        if (name == null || name.isEmpty() || code == null || code.isEmpty() || lat == null || lon == null) {
            throw ApiException.badRequest("name, short_code, latitude and longitude are required.");
        }
        if (buildings.findByShortCode(code.toUpperCase()).isPresent()) {
            throw ApiException.badRequest("A building with short code " + code.toUpperCase() + " already exists.");
        }
        Building b = new Building();
        b.setName(name);
        b.setShortCode(code.toUpperCase());
        String category = RequestBodies.str(body, "category");
        b.setCategory(category == null || category.isEmpty() ? "OTHER" : category.toUpperCase());
        b.setDescription(RequestBodies.str(body, "description"));
        b.setLatitude(lat);
        b.setLongitude(lon);
        b = buildings.save(b);

        // Give the new building an entrance and connect it to the nearest walkway point.
        var nearest = graphService.graph().nearestNode(lat, lon, false);
        NavigationNode entrance = new NavigationNode();
        entrance.setNodeKey("n_bldg_" + b.getId());
        entrance.setLatitude(lat);
        entrance.setLongitude(lon);
        entrance.setEntrance(true);
        entrance.setNodeType("entrance");
        entrance.setBuildingId(b.getId());
        entrance.setDescription("Entrance to " + b.getName());
        entrance = nodes.save(entrance);
        if (nearest.isPresent()) {
            NavigationNode near = nodes.findByNodeKey(nearest.get().key()).orElseThrow();
            pathways.save(newPathway(entrance, near));
        }
        graphService.invalidate();
        return ApiResponse.created(b.toMap(), "Building created and connected to the nearest walkway.");
    }

    @PutMapping("/locations/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> updateLocation(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                              HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        Building b = buildings.findById(id).orElseThrow(() -> ApiException.notFound("Building #" + id + " not found."));
        if (body.containsKey("name")) b.setName(RequestBodies.str(body, "name"));
        if (body.containsKey("short_code")) b.setShortCode(RequestBodies.str(body, "short_code").toUpperCase());
        if (body.containsKey("category")) b.setCategory(RequestBodies.str(body, "category").toUpperCase());
        if (body.containsKey("description")) b.setDescription(RequestBodies.str(body, "description"));
        if (body.containsKey("latitude")) b.setLatitude(RequestBodies.num(body, "latitude"));
        if (body.containsKey("longitude")) b.setLongitude(RequestBodies.num(body, "longitude"));
        if (body.containsKey("is_active")) b.setActive(RequestBodies.bool(body, "is_active"));
        b = buildings.save(b);
        graphService.invalidate();
        return ApiResponse.ok(b.toMap(), "Building updated.");
    }

    @DeleteMapping("/locations/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> deleteLocation(@PathVariable Long id, HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        Building b = buildings.findById(id).orElseThrow(() -> ApiException.notFound("Building #" + id + " not found."));
        b.setActive(false);
        buildings.save(b);
        graphService.invalidate();
        return ApiResponse.ok(null, "Building hidden from the map.");
    }

    // ---------------------------------------------------------------- pathways

    @PostMapping("/pathways")
    public ResponseEntity<Map<String, Object>> createPathway(@RequestBody Map<String, Object> body,
                                                             HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        String fromKey = RequestBodies.str(body, "from_node_key");
        String toKey = RequestBodies.str(body, "to_node_key");
        if (fromKey == null || toKey == null) {
            throw ApiException.badRequest("from_node_key and to_node_key are required.");
        }
        NavigationNode from = nodes.findByNodeKey(fromKey).orElseThrow(() -> ApiException.notFound("Unknown node " + fromKey));
        NavigationNode to = nodes.findByNodeKey(toKey).orElseThrow(() -> ApiException.notFound("Unknown node " + toKey));
        Pathway p = newPathway(from, to);
        Double distance = RequestBodies.num(body, "distance_meters");
        if (distance != null && distance > p.getDistanceMeters()) {
            p.setDistanceMeters(distance);
            p.setEstimatedSeconds(Math.round(distance / GeoUtils.WALKING_SPEED_MPS * 10) / 10.0);
        }
        if (body.containsKey("has_stairs")) p.setHasStairs(RequestBodies.bool(body, "has_stairs"));
        if (body.containsKey("has_ramp")) p.setHasRamp(RequestBodies.bool(body, "has_ramp"));
        if (body.containsKey("is_accessible")) p.setAccessible(RequestBodies.bool(body, "is_accessible"));
        if (body.containsKey("is_bidirectional")) p.setBidirectional(RequestBodies.bool(body, "is_bidirectional"));
        if (body.containsKey("surface_type")) p.setSurfaceType(RequestBodies.str(body, "surface_type"));
        p = pathways.save(p);
        graphService.invalidate();
        return ApiResponse.created(data.pathway(p), "Pathway created.");
    }

    @PutMapping("/pathways/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> updatePathway(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                             HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        Pathway p = pathwayService.get(id);
        if (body.containsKey("is_accessible")) p.setAccessible(RequestBodies.bool(body, "is_accessible"));
        if (body.containsKey("has_stairs")) p.setHasStairs(RequestBodies.bool(body, "has_stairs"));
        if (body.containsKey("has_ramp")) p.setHasRamp(RequestBodies.bool(body, "has_ramp"));
        if (body.containsKey("surface_type")) p.setSurfaceType(RequestBodies.str(body, "surface_type"));
        if (body.containsKey("width_meters")) p.setWidthMeters(RequestBodies.num(body, "width_meters"));
        if (body.containsKey("is_bidirectional")) p.setBidirectional(RequestBodies.bool(body, "is_bidirectional"));
        p = pathways.save(p);
        graphService.invalidate();
        return ApiResponse.ok(data.pathway(p), "Pathway updated.");
    }

    @PostMapping("/pathways/{id:\\d+}/close")
    public ResponseEntity<Map<String, Object>> closePathway(@PathVariable Long id,
                                                            @RequestBody(required = false) RequestBodies.CloseRequest body,
                                                            HttpServletRequest request) {
        User admin = sessionAuth.requireAdmin(request);
        RequestBodies.CloseRequest b = body != null ? body : new RequestBodies.CloseRequest(null, null);
        return ApiResponse.ok(pathwayService.close(id, b.reason(), b.description(), admin).toMap(),
                "Pathway closed. New routes will avoid it.");
    }

    @PostMapping("/pathways/{id:\\d+}/reopen")
    public ResponseEntity<Map<String, Object>> reopenPathway(@PathVariable Long id, HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        return ApiResponse.ok(data.pathway(pathwayService.reopen(id)), "Pathway reopened. Routing updated.");
    }

    private static Pathway newPathway(NavigationNode from, NavigationNode to) {
        double meters = GeoUtils.haversine(from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude());
        Pathway p = new Pathway();
        p.setFromNodeId(from.getId());
        p.setToNodeId(to.getId());
        p.setDistanceMeters(Math.round(meters * 10) / 10.0);
        p.setEstimatedSeconds(Math.round(meters / GeoUtils.WALKING_SPEED_MPS * 10) / 10.0);
        p.setSurfaceType("paved");
        return p;
    }

    // ---------------------------------------------------------------- reports

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> reports(@RequestParam(required = false) String status,
                                                       HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        List<UserReport> list = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? reports.findAllByOrderBySubmittedAtDesc()
                : reports.findByStatusOrderBySubmittedAtDesc(status.toUpperCase());
        Map<Long, User> userCache = new HashMap<>();
        return ApiResponse.ok(list.stream().map(r -> {
            Map<String, Object> m = r.toMap();
            User u = r.getUserId() == null ? null
                    : userCache.computeIfAbsent(r.getUserId(), id -> users.findById(id).orElse(null));
            m.put("user_name", u != null ? u.fullName() : "Anonymous visitor");
            m.put("user_email", u != null ? u.getEmail() : null);
            return m;
        }).toList());
    }

    @PutMapping("/reports/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> updateReport(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                            HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        UserReport r = reports.findById(id).orElseThrow(() -> ApiException.notFound("Report #" + id + " not found."));
        String status = RequestBodies.str(body, "status");
        if (status != null) {
            if (!REPORT_STATUSES.contains(status.toUpperCase())) {
                throw ApiException.badRequest("Status must be PENDING, REVIEWED or RESOLVED.");
            }
            r.setStatus(status.toUpperCase());
        }
        if (body.containsKey("admin_notes")) {
            r.setAdminNotes(RequestBodies.str(body, "admin_notes"));
        }
        return ApiResponse.ok(reports.save(r).toMap(), "Report updated.");
    }

    // ---------------------------------------------------------------- emergency facilities

    @GetMapping("/emergency")
    public ResponseEntity<Map<String, Object>> emergency(HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        return ApiResponse.ok(facilities.findAll().stream().map(EmergencyFacility::toMap).toList());
    }

    @PostMapping("/emergency")
    public ResponseEntity<Map<String, Object>> createEmergency(@RequestBody Map<String, Object> body,
                                                               HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        String name = RequestBodies.str(body, "name");
        Double lat = RequestBodies.num(body, "latitude");
        Double lon = RequestBodies.num(body, "longitude");
        if (name == null || name.isEmpty() || lat == null || lon == null) {
            throw ApiException.badRequest("name, latitude and longitude are required.");
        }
        EmergencyFacility f = new EmergencyFacility();
        f.setName(name);
        String type = RequestBodies.str(body, "facility_type");
        f.setFacilityType(type == null || type.isEmpty() ? "MEDICAL" : type.toUpperCase());
        f.setLatitude(lat);
        f.setLongitude(lon);
        f.setContactNumber(RequestBodies.str(body, "contact_number"));
        f.setDescription(RequestBodies.str(body, "description"));
        Double nodeId = RequestBodies.num(body, "node_id");
        if (nodeId != null) {
            f.setNodeId(nodeId.longValue());
        } else {
            graphService.graph().nearestNode(lat, lon, false)
                    .flatMap(n -> nodes.findByNodeKey(n.key()))
                    .ifPresent(n -> f.setNodeId(n.getId()));
        }
        return ApiResponse.created(facilities.save(f).toMap(), "Emergency facility added.");
    }

    // ---------------------------------------------------------------- users

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> users(@RequestParam(required = false) String role,
                                                     HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        List<User> list = (role == null || role.isBlank() || "all".equalsIgnoreCase(role))
                ? users.findAllByOrderByCreatedAtDesc()
                : users.findByRoleOrderByCreatedAtDesc(role.toLowerCase());
        return ApiResponse.ok(list.stream().map(User::toMap).toList());
    }

    @PutMapping("/users/{id:\\d+}/role")
    public ResponseEntity<Map<String, Object>> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        User admin = sessionAuth.requireAdmin(request);
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User #" + id + " not found."));
        String role = RequestBodies.str(body, "role");
        if (role == null || !AuthService.ROLES.contains(role.toLowerCase())) {
            throw ApiException.badRequest("Role must be student, faculty, visitor or admin.");
        }
        if (user.getId().equals(admin.getId()) && !User.ROLE_ADMIN.equalsIgnoreCase(role)) {
            throw ApiException.badRequest("You cannot remove your own admin role.");
        }
        user.setRole(role.toLowerCase());
        return ApiResponse.ok(users.save(user).toMap(), "Role updated to " + role.toLowerCase() + ".");
    }
}
