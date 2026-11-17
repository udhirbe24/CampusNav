package edu.thapar.campusnav.web;

import edu.thapar.campusnav.repository.BuildingRepository;
import edu.thapar.campusnav.repository.NavigationNodeRepository;
import edu.thapar.campusnav.service.CampusDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Buildings, navigation points and search. */
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final CampusDataService data;
    private final NavigationNodeRepository nodes;
    private final BuildingRepository buildings;

    public LocationController(CampusDataService data, NavigationNodeRepository nodes, BuildingRepository buildings) {
        this.data = data;
        this.nodes = nodes;
        this.buildings = buildings;
    }

    @GetMapping({"", "/", "/buildings"})
    public ResponseEntity<Map<String, Object>> buildings(@RequestParam(required = false) String category,
                                                         @RequestParam(required = false) String q) {
        return ApiResponse.ok(data.buildings(category, q));
    }

    @GetMapping({"/{id:\\d+}", "/buildings/{id:\\d+}"})
    public ResponseEntity<Map<String, Object>> building(@PathVariable Long id) {
        return ApiResponse.ok(data.building(id)
                .orElseThrow(() -> ApiException.notFound("Building #" + id + " not found.")));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) String category) {
        if (q == null || q.isBlank()) {
            throw ApiException.badRequest("Query parameter \"q\" is required.");
        }
        return ApiResponse.ok(data.buildings(category, q));
    }

    @GetMapping("/nodes")
    public ResponseEntity<Map<String, Object>> nodes() {
        return ApiResponse.ok(data.allNodes());
    }

    @GetMapping("/nodes/{key}")
    public ResponseEntity<Map<String, Object>> node(@PathVariable String key) {
        return nodes.findByNodeKey(key)
                .map(n -> ApiResponse.ok(n.toMap(n.getBuildingId() == null ? null
                        : buildings.findById(n.getBuildingId()).map(b -> b.getName()).orElse(null))))
                .orElseThrow(() -> ApiException.notFound("Node \"" + key + "\" not found."));
    }

    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> categories() {
        return ApiResponse.ok(List.of(
                category("ACADEMIC", "Academic & Classrooms", "book-open"),
                category("ADMIN", "Administration & Offices", "briefcase"),
                category("HOSTEL", "Hostels & Residences", "home"),
                category("LIBRARY", "Libraries", "bookmark"),
                category("CAFETERIA", "Cafeterias & Food", "coffee"),
                category("SPORTS", "Sports & Recreation", "activity"),
                category("MEDICAL", "Medical & Health", "plus-circle"),
                category("PARKING", "Parking Facilities", "truck"),
                category("OTHER", "Other Campus Points", "map-pin")));
    }

    private static Map<String, String> category(String key, String label, String icon) {
        return Map.of("key", key, "label", label, "icon", icon);
    }
}
