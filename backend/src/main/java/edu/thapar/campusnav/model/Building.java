package edu.thapar.campusnav.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/** A campus building or place people can navigate to (hostel, block, gate, ground ...). */
@Entity
@Table(name = "buildings")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true)
    private String shortCode;
    /** ACADEMIC, ADMIN, LIBRARY, HOSTEL, CAFETERIA, SPORTS, MEDICAL, PARKING or OTHER. */
    @Column(nullable = false)
    private String category;
    @Column(length = 1000)
    private String description;
    private double latitude;
    private double longitude;
    @Column(nullable = false)
    private String campusId = "thapar";
    @Column(nullable = false)
    private boolean active = true;

    /** Short display name, e.g. "CS Block" for "CS Block (Computer Science & Engineering)". */
    public String displayName() {
        int bracket = name.indexOf('(');
        return bracket > 0 ? name.substring(0, bracket).trim() : name;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("short_code", shortCode);
        m.put("category", category);
        m.put("description", description);
        m.put("latitude", latitude);
        m.put("longitude", longitude);
        m.put("campus_id", campusId);
        m.put("is_active", active);
        return m;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getCampusId() { return campusId; }
    public void setCampusId(String campusId) { this.campusId = campusId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
