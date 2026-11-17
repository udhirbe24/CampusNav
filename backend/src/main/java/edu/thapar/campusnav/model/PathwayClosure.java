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

/** A record of an admin closing a walkway (construction, event, flooding ...). */
@Entity
@Table(name = "pathway_closures")
public class PathwayClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long pathwayId;
    @Column(nullable = false)
    private String reason;
    @Column(length = 1000)
    private String description;
    @Column(nullable = false)
    private Long closedById;
    @Column(nullable = false)
    private Instant closedAt = Instant.now();
    private Instant actualReopen;
    @Column(nullable = false)
    private boolean active = true;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("pathway_id", pathwayId);
        m.put("reason", reason);
        m.put("description", description);
        m.put("closed_by_id", closedById);
        m.put("closed_at", closedAt != null ? closedAt.toString() : null);
        m.put("actual_reopen", actualReopen != null ? actualReopen.toString() : null);
        m.put("is_active", active);
        return m;
    }

    public Long getId() { return id; }
    public Long getPathwayId() { return pathwayId; }
    public void setPathwayId(Long pathwayId) { this.pathwayId = pathwayId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getClosedById() { return closedById; }
    public void setClosedById(Long closedById) { this.closedById = closedById; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getActualReopen() { return actualReopen; }
    public void setActualReopen(Instant actualReopen) { this.actualReopen = actualReopen; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
