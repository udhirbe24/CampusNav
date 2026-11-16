package edu.thapar.campusnav.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/** An edge of the walkway graph between two navigation nodes. */
@Entity
@Table(name = "pathways")
public class Pathway {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long fromNodeId;
    @Column(nullable = false)
    private Long toNodeId;
    private double distanceMeters;
    private double estimatedSeconds;
    @Column(nullable = false)
    private boolean bidirectional = true;
    @Column(nullable = false)
    private boolean hasStairs;
    @Column(nullable = false)
    private boolean hasRamp;
    @Column(nullable = false)
    private boolean accessible = true;
    @Column(nullable = false)
    private String surfaceType = "concrete";
    private Double widthMeters;
    /** True while an admin has closed this walkway; routing avoids it. */
    @Column(nullable = false)
    private boolean blocked;
    private String closureReason;
    @Column(nullable = false)
    private String campusId = "thapar";

    public Map<String, Object> toMap(NavigationNode from, NavigationNode to) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("from_node_id", fromNodeId);
        m.put("from_node_key", from != null ? from.getNodeKey() : null);
        m.put("to_node_id", toNodeId);
        m.put("to_node_key", to != null ? to.getNodeKey() : null);
        m.put("distance_meters", distanceMeters);
        m.put("estimated_seconds", estimatedSeconds);
        m.put("is_bidirectional", bidirectional);
        m.put("has_stairs", hasStairs);
        m.put("has_ramp", hasRamp);
        m.put("is_accessible", accessible);
        m.put("surface_type", surfaceType);
        m.put("width_meters", widthMeters);
        m.put("is_blocked", blocked);
        m.put("closure_reason", closureReason);
        m.put("campus_id", campusId);
        if (from != null && to != null) {
            m.put("coordinates", java.util.List.of(
                    java.util.List.of(from.getLongitude(), from.getLatitude()),
                    java.util.List.of(to.getLongitude(), to.getLatitude())));
        }
        return m;
    }

    public Long getId() { return id; }
    public Long getFromNodeId() { return fromNodeId; }
    public void setFromNodeId(Long fromNodeId) { this.fromNodeId = fromNodeId; }
    public Long getToNodeId() { return toNodeId; }
    public void setToNodeId(Long toNodeId) { this.toNodeId = toNodeId; }
    public double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(double distanceMeters) { this.distanceMeters = distanceMeters; }
    public double getEstimatedSeconds() { return estimatedSeconds; }
    public void setEstimatedSeconds(double estimatedSeconds) { this.estimatedSeconds = estimatedSeconds; }
    public boolean isBidirectional() { return bidirectional; }
    public void setBidirectional(boolean bidirectional) { this.bidirectional = bidirectional; }
    public boolean isHasStairs() { return hasStairs; }
    public void setHasStairs(boolean hasStairs) { this.hasStairs = hasStairs; }
    public boolean isHasRamp() { return hasRamp; }
    public void setHasRamp(boolean hasRamp) { this.hasRamp = hasRamp; }
    public boolean isAccessible() { return accessible; }
    public void setAccessible(boolean accessible) { this.accessible = accessible; }
    public String getSurfaceType() { return surfaceType; }
    public void setSurfaceType(String surfaceType) { this.surfaceType = surfaceType; }
    public Double getWidthMeters() { return widthMeters; }
    public void setWidthMeters(Double widthMeters) { this.widthMeters = widthMeters; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
    public String getClosureReason() { return closureReason; }
    public void setClosureReason(String closureReason) { this.closureReason = closureReason; }
    public String getCampusId() { return campusId; }
    public void setCampusId(String campusId) { this.campusId = campusId; }
}
