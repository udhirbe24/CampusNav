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

/** One calculated route, kept for the user's history and the admin analytics. */
@Entity
@Table(name = "navigation_history")
public class NavigationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Null when the route was requested without logging in. */
    private Long userId;
    @Column(nullable = false)
    private String fromNodeKey;
    @Column(nullable = false)
    private String toNodeKey;
    @Column(nullable = false)
    private String algorithmUsed;
    @Column(nullable = false)
    private String routeMode;
    private double distanceMeters;
    private double durationSeconds;
    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("user_id", userId);
        m.put("from_node_key", fromNodeKey);
        m.put("to_node_key", toNodeKey);
        m.put("algorithm_used", algorithmUsed);
        m.put("route_mode", routeMode);
        m.put("distance_meters", distanceMeters);
        m.put("duration_seconds", durationSeconds);
        m.put("timestamp", timestamp != null ? timestamp.toString() : null);
        return m;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFromNodeKey() { return fromNodeKey; }
    public void setFromNodeKey(String fromNodeKey) { this.fromNodeKey = fromNodeKey; }
    public String getToNodeKey() { return toNodeKey; }
    public void setToNodeKey(String toNodeKey) { this.toNodeKey = toNodeKey; }
    public String getAlgorithmUsed() { return algorithmUsed; }
    public void setAlgorithmUsed(String algorithmUsed) { this.algorithmUsed = algorithmUsed; }
    public String getRouteMode() { return routeMode; }
    public void setRouteMode(String routeMode) { this.routeMode = routeMode; }
    public double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(double distanceMeters) { this.distanceMeters = distanceMeters; }
    public double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(double durationSeconds) { this.durationSeconds = durationSeconds; }
    public Instant getTimestamp() { return timestamp; }
}
