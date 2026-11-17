package edu.thapar.campusnav.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** A hazard or obstruction reported by a user, reviewed by admins. */
@Entity
@Table(name = "user_reports")
public class UserReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Null for anonymous visitors. */
    private Long userId;
    private Long pathwayId;
    /** OBSTRUCTION, DAMAGE, CLOSURE, HAZARD or OTHER. */
    @Column(nullable = false)
    private String reportType;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(nullable = false)
    private Instant submittedAt = Instant.now();
    /** PENDING, REVIEWED or RESOLVED. */
    @Column(nullable = false)
    private String status = "PENDING";
    @Column(length = 2000)
    private String adminNotes;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("user_id", userId);
        m.put("pathway_id", pathwayId);
        m.put("report_type", reportType);
        m.put("description", description);
        m.put("submitted_at", submittedAt != null ? submittedAt.toString() : null);
        m.put("status", status);
        m.put("admin_notes", adminNotes);
        return m;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPathwayId() { return pathwayId; }
    public void setPathwayId(Long pathwayId) { this.pathwayId = pathwayId; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
}
