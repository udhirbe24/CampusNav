package edu.thapar.campusnav.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.thapar.campusnav.model.Building;
import edu.thapar.campusnav.model.EmergencyFacility;
import edu.thapar.campusnav.model.NavigationNode;
import edu.thapar.campusnav.model.Pathway;
import edu.thapar.campusnav.model.User;
import edu.thapar.campusnav.repository.BuildingRepository;
import edu.thapar.campusnav.repository.EmergencyFacilityRepository;
import edu.thapar.campusnav.repository.NavigationNodeRepository;
import edu.thapar.campusnav.repository.PathwayRepository;
import edu.thapar.campusnav.repository.UserRepository;
import edu.thapar.campusnav.algorithm.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the Thapar campus map (buildings, walkway nodes, pathways, emergency points) from the
 * JSON/GeoJSON files in {@code resources/data} the first time the app starts, and makes sure
 * the administrator account exists. Replaces the old Python {@code init_db.py} step.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final BuildingRepository buildingRepository;
    private final NavigationNodeRepository nodeRepository;
    private final PathwayRepository pathwayRepository;
    private final EmergencyFacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ObjectMapper mapper;
    private final String adminEmail;
    private final String adminPassword;

    public DataSeeder(BuildingRepository buildingRepository, NavigationNodeRepository nodeRepository,
                      PathwayRepository pathwayRepository, EmergencyFacilityRepository facilityRepository,
                      UserRepository userRepository, AuthService authService, ObjectMapper mapper,
                      @Value("${campusnav.admin.email}") String adminEmail,
                      @Value("${campusnav.admin.password}") String adminPassword) {
        this.buildingRepository = buildingRepository;
        this.nodeRepository = nodeRepository;
        this.pathwayRepository = pathwayRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.mapper = mapper;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        seedAdmin();
        if (buildingRepository.count() > 0) {
            log.info("Campus data already loaded ({} buildings, {} nodes, {} pathways).",
                    buildingRepository.count(), nodeRepository.count(), pathwayRepository.count());
            return;
        }
        Map<String, Long> buildingIds = seedBuildings();
        Map<String, NavigationNode> nodes = seedNodes(buildingIds);
        int pathways = seedPathways(nodes);
        seedEmergencyFacilities(nodes);
        log.info("Loaded Thapar campus: {} buildings, {} nodes, {} pathways, {} emergency facilities.",
                buildingIds.size(), nodes.size(), pathways, facilityRepository.count());
    }

    private void seedAdmin() {
        String email = adminEmail.trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setFirstName("Campus");
        admin.setLastName("Admin");
        admin.setRole(User.ROLE_ADMIN);
        admin.setPasswordHash(authService.hash(adminPassword));
        userRepository.save(admin);
        log.info("Created administrator account {}", email);
    }

    private Map<String, Long> seedBuildings() throws IOException {
        JsonNode root = read("data/thapar_locations.json");
        Map<String, Long> ids = new HashMap<>();
        for (JsonNode b : root.path("buildings")) {
            Building building = new Building();
            building.setName(b.path("name").asText());
            building.setShortCode(b.path("short_code").asText());
            building.setCategory(b.path("category").asText("OTHER"));
            building.setDescription(b.path("description").asText(""));
            building.setLatitude(b.path("latitude").asDouble());
            building.setLongitude(b.path("longitude").asDouble());
            building.setCampusId(root.path("campus_id").asText(GraphService.CAMPUS_ID));
            ids.put(b.path("id").asText(), buildingRepository.save(building).getId());
        }
        return ids;
    }

    private Map<String, NavigationNode> seedNodes(Map<String, Long> buildingIds) throws IOException {
        Map<String, NavigationNode> nodes = new HashMap<>();
        for (JsonNode f : read("data/thapar_nodes.geojson").path("features")) {
            JsonNode p = f.path("properties");
            JsonNode coords = f.path("geometry").path("coordinates");
            NavigationNode node = new NavigationNode();
            node.setNodeKey(p.path("node_key").asText());
            node.setLongitude(coords.get(0).asDouble());
            node.setLatitude(coords.get(1).asDouble());
            node.setNodeType(p.path("node_type").asText("intersection"));
            node.setEntrance("entrance".equals(node.getNodeType()));
            node.setAccessible(p.path("is_accessible").asBoolean(true));
            node.setDescription(p.path("description").asText(""));
            String building = p.path("building_id").asText(null);
            node.setBuildingId(building != null ? buildingIds.get(building) : null);
            nodes.put(node.getNodeKey(), nodeRepository.save(node));
        }
        return nodes;
    }

    private int seedPathways(Map<String, NavigationNode> nodes) throws IOException {
        int count = 0;
        for (JsonNode f : read("data/thapar_pathways.geojson").path("features")) {
            JsonNode p = f.path("properties");
            NavigationNode from = nodes.get(p.path("from_node").asText());
            NavigationNode to = nodes.get(p.path("to_node").asText());
            if (from == null || to == null) {
                log.warn("Skipping pathway {}: unknown node", p.path("pathway_key").asText());
                continue;
            }
            // A walkway can't be shorter than the straight line between its ends.
            double straight = GeoUtils.haversine(from.getLatitude(), from.getLongitude(), to.getLatitude(),
                    to.getLongitude());
            double distance = Math.max(p.path("distance_meters").asDouble(straight), straight);
            double seconds = p.has("estimated_seconds")
                    ? Math.max(p.path("estimated_seconds").asDouble(), distance / 2.0)
                    : distance / GeoUtils.WALKING_SPEED_MPS;

            Pathway pathway = new Pathway();
            pathway.setFromNodeId(from.getId());
            pathway.setToNodeId(to.getId());
            pathway.setDistanceMeters(Math.round(distance * 10) / 10.0);
            pathway.setEstimatedSeconds(Math.round(seconds * 10) / 10.0);
            pathway.setBidirectional(p.path("is_bidirectional").asBoolean(true));
            pathway.setHasStairs(p.path("has_stairs").asBoolean(false));
            pathway.setHasRamp(p.path("has_ramp").asBoolean(false));
            pathway.setAccessible(p.path("is_accessible").asBoolean(true));
            pathway.setSurfaceType(p.path("surface_type").asText("concrete"));
            pathwayRepository.save(pathway);
            count++;
        }
        return count;
    }

    private void seedEmergencyFacilities(Map<String, NavigationNode> nodes) {
        addFacility(nodes, "n_dispensary", "Dispensary / Campus Health Centre", "MEDICAL", "+91-175-2393021",
                "Campus health centre with first aid and a doctor on duty.");
        addFacility(nodes, "n_gate1", "Security Office (Gate 1)", "SECURITY", "+91-175-2393000",
                "Main security post and visitor desk at the Bhadson Road gate.");
        addFacility(nodes, "n_nirvana", "Assembly Point - Nirvana Park", "ASSEMBLY", null,
                "Open-ground emergency assembly point in the central park.");
        addFacility(nodes, "n_tan", "Fire Point - TAN Building", "FIRE", "101",
                "Fire extinguishers and hose reel near the TAN auditorium.");
    }

    private void addFacility(Map<String, NavigationNode> nodes, String nodeKey, String name, String type,
                             String phone, String description) {
        NavigationNode node = nodes.get(nodeKey);
        if (node == null) {
            return;
        }
        EmergencyFacility f = new EmergencyFacility();
        f.setName(name);
        f.setFacilityType(type);
        f.setLatitude(node.getLatitude());
        f.setLongitude(node.getLongitude());
        f.setContactNumber(phone);
        f.setDescription(description);
        f.setNodeId(node.getId());
        facilityRepository.save(f);
    }

    private JsonNode read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readTree(in);
        }
    }
}
