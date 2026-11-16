package edu.thapar.campusnav.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/** A vertex of the walkway graph: a building entrance, landmark or path junction. */
@Entity
@Table(name = "navigation_nodes")
public class NavigationNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String nodeKey;
    private double latitude;
    private double longitude;
    @Column(nullable = false)
    private boolean entrance;
    /** The building this point belongs to, or null for a plain junction. */
    private Long buildingId;
    @Column(nullable = false)
    private boolean accessible = true;
    /** entrance, intersection, landmark or emergency. */
    @Column(nullable = false)
    private String nodeType = "intersection";
    private String description;
    @Column(nullable = false)
    private String campusId = "thapar";

    public Map<String, Object> toMap(String buildingName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("node_key", nodeKey);
        m.put("latitude", latitude);
        m.put("longitude", longitude);
        m.put("is_entrance", entrance);
        m.put("building_id", buildingId);
        m.put("building_name", buildingName);
        m.put("is_accessible", accessible);
        m.put("node_type", nodeType);
        m.put("description", description);
        m.put("campus_id", campusId);
        return m;
    }

    public Long getId() { return id; }
    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public boolean isEntrance() { return entrance; }
    public void setEntrance(boolean entrance) { this.entrance = entrance; }
    public Long getBuildingId() { return buildingId; }
    public void setBuildingId(Long buildingId) { this.buildingId = buildingId; }
    public boolean isAccessible() { return accessible; }
    public void setAccessible(boolean accessible) { this.accessible = accessible; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCampusId() { return campusId; }
    public void setCampusId(String campusId) { this.campusId = campusId; }
}
