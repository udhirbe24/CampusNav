package edu.thapar.campusnav.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/** A medical, security, fire or assembly point that emergency routing can target. */
@Entity
@Table(name = "emergency_facilities")
public class EmergencyFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    /** MEDICAL, SECURITY, FIRE or ASSEMBLY. */
    @Column(nullable = false)
    private String facilityType;
    private double latitude;
    private double longitude;
    private String contactNumber;
    @Column(length = 1000)
    private String description;
    /** The navigation node routes should end at. */
    private Long nodeId;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private String campusId = "thapar";

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("facility_type", facilityType);
        m.put("latitude", latitude);
        m.put("longitude", longitude);
        m.put("contact_number", contactNumber);
        m.put("description", description);
        m.put("node_id", nodeId);
        m.put("is_active", active);
        m.put("campus_id", campusId);
        return m;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFacilityType() { return facilityType; }
    public void setFacilityType(String facilityType) { this.facilityType = facilityType; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCampusId() { return campusId; }
    public void setCampusId(String campusId) { this.campusId = campusId; }
}
