package edu.thapar.campusnav.web;

import edu.thapar.campusnav.repository.BuildingRepository;
import edu.thapar.campusnav.repository.NavigationNodeRepository;
import edu.thapar.campusnav.repository.PathwayRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/** Friendly page URLs (/app, /admin ...) and the health check. */
@Controller
public class PageController {

    private final BuildingRepository buildings;
    private final NavigationNodeRepository nodes;
    private final PathwayRepository pathways;

    public PageController(BuildingRepository buildings, NavigationNodeRepository nodes, PathwayRepository pathways) {
        this.buildings = buildings;
        this.nodes = nodes;
        this.pathways = pathways;
    }

    @GetMapping("/")
    public String home() {
        return "forward:/index.html";
    }

    @GetMapping({"/app", "/navigate"})
    public String app() {
        return "forward:/app.html";
    }

    @GetMapping({"/auth", "/login", "/register"})
    public String auth() {
        return "forward:/auth.html";
    }

    @GetMapping("/admin")
    public String admin() {
        return "forward:/admin.html";
    }

    @GetMapping("/api/health")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "healthy");
        body.put("campus", "thapar");
        body.put("buildings", buildings.count());
        body.put("nodes", nodes.count());
        body.put("pathways", pathways.count());
        return ResponseEntity.ok(body);
    }
}
