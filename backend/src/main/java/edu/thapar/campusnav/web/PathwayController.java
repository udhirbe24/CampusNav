package edu.thapar.campusnav.web;

import edu.thapar.campusnav.model.User;
import edu.thapar.campusnav.model.UserReport;
import edu.thapar.campusnav.repository.UserReportRepository;
import edu.thapar.campusnav.service.CampusDataService;
import edu.thapar.campusnav.service.PathwayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/** Walkways, closures and user hazard reports. */
@RestController
@RequestMapping("/api/pathways")
public class PathwayController {

    private static final Set<String> REPORT_TYPES = Set.of("OBSTRUCTION", "DAMAGE", "CLOSURE", "CONSTRUCTION", "HAZARD", "OTHER");

    private final CampusDataService data;
    private final PathwayService pathwayService;
    private final UserReportRepository reports;
    private final SessionAuth sessionAuth;

    public PathwayController(CampusDataService data, PathwayService pathwayService, UserReportRepository reports,
                             SessionAuth sessionAuth) {
        this.data = data;
        this.pathwayService = pathwayService;
        this.reports = reports;
        this.sessionAuth = sessionAuth;
    }

    @GetMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> all() {
        return ApiResponse.ok(data.allPathways());
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> one(@PathVariable Long id) {
        return ApiResponse.ok(data.pathway(pathwayService.get(id)));
    }

    @GetMapping("/closures")
    public ResponseEntity<Map<String, Object>> closures() {
        return ApiResponse.ok(data.activeClosures());
    }

    /** Anyone (logged in or not) can report a problem on campus. */
    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> report(@RequestBody RequestBodies.ReportRequest body,
                                                      HttpServletRequest request) {
        if (body.description() == null || body.description().isBlank()) {
            throw ApiException.badRequest("Please describe the problem.");
        }
        String type = body.reportType() == null ? "OBSTRUCTION" : body.reportType().trim().toUpperCase();
        UserReport report = new UserReport();
        report.setUserId(sessionAuth.currentUser(request).map(User::getId).orElse(null));
        report.setPathwayId(body.pathwayId());
        report.setReportType(REPORT_TYPES.contains(type) ? type : "OTHER");
        String description = body.description().trim();
        if (body.locationDetails() != null && !body.locationDetails().isBlank()) {
            description += " (Location: " + body.locationDetails().trim() + ")";
        }
        report.setDescription(description);
        return ApiResponse.created(reports.save(report).toMap(),
                "Report submitted. Thank you for keeping campus pathways safe!");
    }

    @PostMapping("/{id:\\d+}/close")
    public ResponseEntity<Map<String, Object>> close(@PathVariable Long id,
                                                     @RequestBody(required = false) RequestBodies.CloseRequest body,
                                                     HttpServletRequest request) {
        User admin = sessionAuth.requireAdmin(request);
        RequestBodies.CloseRequest b = body != null ? body : new RequestBodies.CloseRequest(null, null);
        return ApiResponse.ok(pathwayService.close(id, b.reason(), b.description(), admin).toMap(),
                "Pathway closed. New routes will avoid it.");
    }

    @PostMapping("/{id:\\d+}/reopen")
    public ResponseEntity<Map<String, Object>> reopen(@PathVariable Long id, HttpServletRequest request) {
        sessionAuth.requireAdmin(request);
        return ApiResponse.ok(data.pathway(pathwayService.reopen(id)), "Pathway reopened. Routing updated.");
    }
}
