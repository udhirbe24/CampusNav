package edu.thapar.campusnav.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.thapar.campusnav.algorithm.AStarPathFinder;
import edu.thapar.campusnav.algorithm.CampusGraph;
import edu.thapar.campusnav.algorithm.DijkstraPathFinder;
import edu.thapar.campusnav.algorithm.SearchOptions;
import edu.thapar.campusnav.service.GraphService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end tests against the real Thapar campus data loaded into an in-memory database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampusNavApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private GraphService graphService;

    private JsonNode call(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult postJson(String url, Object body, MockHttpSession session, int expectedStatus) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (session != null) {
            request.session(session);
        }
        return mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult r = postJson("/api/auth/login", Map.of("email", "admin@thapar.edu", "password", "AdminPassword123!"),
                session, 200);
        return (MockHttpSession) r.getRequest().getSession();
    }

    private List<String> buildingNodeKeys() throws Exception {
        JsonNode buildings = call(mvc.perform(get("/api/locations")).andExpect(status().isOk()).andReturn()).get("data");
        List<String> keys = new ArrayList<>();
        buildings.forEach(b -> keys.add(b.get("node_key").asText(null)));
        return keys;
    }

    @Test
    void campusDataIsLoaded() throws Exception {
        JsonNode health = call(mvc.perform(get("/api/health")).andExpect(status().isOk()).andReturn());
        assertThat(health.get("buildings").asInt()).isEqualTo(35);
        assertThat(health.get("pathways").asInt()).isGreaterThan(50);
    }

    /** Regression test: buildings without an "entrance" node (grounds, parks) used to be unroutable. */
    @Test
    void everyBuildingHasARoutableNode() throws Exception {
        List<String> keys = buildingNodeKeys();
        assertThat(keys).hasSize(35).doesNotContainNull();
        CampusGraph graph = graphService.graph();
        assertThat(keys).allMatch(graph::hasNode);
    }

    @Test
    void csBlockToCricketGroundFastestRouteWorks() throws Exception {
        JsonNode r = call(postJson("/api/routes/calculate",
                Map.of("from_node", "n_cs_block", "to_node", "n_cricket", "mode", "fastest"), null, 200));
        assertThat(r.get("success").asBoolean()).isTrue();
        JsonNode summary = r.get("data").get("summary");
        assertThat(summary.get("algorithm").asText()).isEqualTo("A* Search");
        assertThat(summary.get("total_distance_meters").asDouble()).isPositive();
        assertThat(r.get("data").get("steps").size()).isGreaterThan(1);
    }

    @Test
    void everyBuildingCanReachEveryOtherInEveryMode() {
        CampusGraph graph = graphService.graph();
        AStarPathFinder aStar = new AStarPathFinder();
        DijkstraPathFinder dijkstra = new DijkstraPathFinder();
        List<String> keys = graph.nodes().stream().map(CampusGraph.Node::key).toList();
        for (SearchOptions options : List.of(SearchOptions.of(SearchOptions.Weight.DISTANCE, false),
                SearchOptions.of(SearchOptions.Weight.TIME, false),
                SearchOptions.of(SearchOptions.Weight.DISTANCE, true))) {
            for (String a : keys) {
                for (String b : keys) {
                    double viaAStar = aStar.findPath(graph, a, b, options).orElseThrow().cost();
                    double viaDijkstra = dijkstra.findPath(graph, a, b, options).orElseThrow().cost();
                    // A* must always find the same optimal cost as Dijkstra
                    assertThat(viaAStar).as(a + " -> " + b).isCloseTo(viaDijkstra, org.assertj.core.data.Offset.offset(1e-6));
                }
            }
        }
    }

    @Test
    void unknownNodeGivesClearError() throws Exception {
        JsonNode r = call(postJson("/api/routes/calculate", Map.of("from_node", "n_cs_block", "to_node", "n_nowhere"),
                null, 404));
        assertThat(r.get("success").asBoolean()).isFalse();
        assertThat(r.get("message").asText()).contains("Unknown destination");
    }

    @Test
    void emergencyRouteGoesToNearestFacilityWithDijkstra() throws Exception {
        JsonNode r = call(postJson("/api/routes/emergency", Map.of("from_node", "n_hh"), null, 200));
        JsonNode data = r.get("data");
        assertThat(data.get("emergency_facility").get("name").asText()).isNotBlank();
        assertThat(data.get("summary").get("algorithm").asText()).isEqualTo("Dijkstra");
    }

    @Test
    void emergencyRouteGoesToTheDestinationTheUserChose() throws Exception {
        JsonNode dispensary = call(postJson("/api/routes/emergency",
                Map.of("from_node", "n_hh", "facility_type", "MEDICAL"), null, 200)).get("data");
        assertThat(lastNode(dispensary)).isEqualTo("n_dispensary");
        assertThat(dispensary.get("emergency_facility").get("facility_type").asText()).isEqualTo("MEDICAL");

        JsonNode gate = call(postJson("/api/routes/emergency",
                Map.of("from_node", "n_hh", "facility_type", "SECURITY"), null, 200)).get("data");
        assertThat(lastNode(gate)).isEqualTo("n_gate1");
        assertThat(gate.get("emergency_facility").get("name").asText()).startsWith("Main Gate");
    }

    private static String lastNode(JsonNode route) {
        JsonNode path = route.get("path_nodes");
        return path.get(path.size() - 1).asText();
    }

    @Test
    void alternativeRouteDiffers() throws Exception {
        JsonNode primary = call(postJson("/api/routes/calculate",
                Map.of("from_node", "n_hd", "to_node", "n_old_lib"), null, 200)).get("data");
        List<String> path = new ArrayList<>();
        primary.get("path_nodes").forEach(n -> path.add(n.asText()));
        JsonNode alt = call(postJson("/api/routes/alternative",
                Map.of("from_node", "n_hd", "to_node", "n_old_lib", "primary_path_nodes", path), null, 200)).get("data");
        List<String> altPath = new ArrayList<>();
        alt.get("path_nodes").forEach(n -> altPath.add(n.asText()));
        assertThat(altPath).isNotEqualTo(path);
    }

    @Test
    void adminEndpointsNeedAdminLogin() throws Exception {
        mvc.perform(get("/api/admin/analytics")).andExpect(status().isUnauthorized());

        MockHttpSession student = new MockHttpSession();
        postJson("/api/auth/register", Map.of("email", "student1@thapar.edu", "password", "Student123",
                "first_name", "Asha", "last_name", "Singh", "role", "student"), null, 201);
        MvcResult login = postJson("/api/auth/login", Map.of("email", "student1@thapar.edu", "password", "Student123"),
                student, 200);
        mvc.perform(get("/api/admin/analytics").session((MockHttpSession) login.getRequest().getSession()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/analytics").session(loginAsAdmin())).andExpect(status().isOk());
    }

    @Test
    void registrationRules() throws Exception {
        postJson("/api/auth/register", Map.of("email", "weak@thapar.edu", "password", "short",
                "first_name", "A", "last_name", "B"), null, 400);
        JsonNode r = call(postJson("/api/auth/register", Map.of("email", "sneaky@thapar.edu", "password", "Sneaky123",
                "first_name", "A", "last_name", "B", "role", "admin"), null, 201));
        // nobody can make themselves admin by signing up
        assertThat(r.get("data").get("role").asText()).isEqualTo("visitor");
        postJson("/api/auth/register", Map.of("email", "sneaky@thapar.edu", "password", "Sneaky123",
                "first_name", "A", "last_name", "B"), null, 400);
        postJson("/api/auth/login", Map.of("email", "sneaky@thapar.edu", "password", "wrong"), null, 401);
    }

    @Test
    void closingAPathwayReroutesAndReopeningRestores() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        JsonNode before = call(postJson("/api/routes/calculate",
                Map.of("from_node", "n_hd", "to_node", "n_old_lib"), null, 200)).get("data");
        String a = before.get("path_nodes").get(2).asText();
        String b = before.get("path_nodes").get(3).asText();

        JsonNode pathways = call(mvc.perform(get("/api/pathways")).andReturn()).get("data");
        long id = -1;
        for (JsonNode p : pathways) {
            String from = p.get("from_node_key").asText();
            String to = p.get("to_node_key").asText();
            if ((from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a))) {
                id = p.get("id").asLong();
            }
        }
        assertThat(id).isPositive();

        postJson("/api/admin/pathways/" + id + "/close", Map.of("reason", "Construction"), admin, 200);
        JsonNode during = call(postJson("/api/routes/calculate",
                Map.of("from_node", "n_hd", "to_node", "n_old_lib"), null, 200)).get("data");
        assertThat(during.get("path_nodes").toString()).isNotEqualTo(before.get("path_nodes").toString());

        postJson("/api/admin/pathways/" + id + "/reopen", Map.of(), admin, 200);
        JsonNode after = call(postJson("/api/routes/calculate",
                Map.of("from_node", "n_hd", "to_node", "n_old_lib"), null, 200)).get("data");
        assertThat(after.get("path_nodes").toString()).isEqualTo(before.get("path_nodes").toString());
    }

    @Test
    void anyoneCanReportAHazard() throws Exception {
        postJson("/api/pathways/report", Map.of("report_type", "HAZARD", "description", "Broken street light"),
                null, 201);
        postJson("/api/pathways/report", Map.of("report_type", "HAZARD", "description", " "), null, 400);
    }

    @Test
    void pagesAreServed() throws Exception {
        mvc.perform(get("/app")).andExpect(status().isOk());
        mvc.perform(get("/admin")).andExpect(status().isOk());
        mvc.perform(get("/js/routing.js")).andExpect(status().isOk());
    }
}
