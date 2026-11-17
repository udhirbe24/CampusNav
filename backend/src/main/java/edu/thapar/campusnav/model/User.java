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

/** A registered account: student, faculty, visitor or admin. */
@Entity
@Table(name = "app_users")
public class User {

    public static final String ROLE_ADMIN = "admin";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    private String firstName;
    @Column(nullable = false)
    private String lastName;
    @Column(nullable = false)
    private String role = "visitor";
    private String studentId;
    private String department;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant lastLogin;
    @Column(nullable = false)
    private boolean active = true;

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("email", email);
        m.put("first_name", firstName);
        m.put("last_name", lastName);
        m.put("full_name", fullName());
        m.put("role", role);
        m.put("student_id", studentId);
        m.put("department", department);
        m.put("created_at", createdAt != null ? createdAt.toString() : null);
        m.put("last_login", lastLogin != null ? lastLogin.toString() : null);
        m.put("is_active", active);
        return m;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLogin() { return lastLogin; }
    public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
