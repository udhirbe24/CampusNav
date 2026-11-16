package edu.thapar.campusnav.web;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;
import java.util.Map;

/** JSON request bodies. Field names arrive in snake_case (from_node, accessible_only, ...). */
public final class RequestBodies {

    private RequestBodies() {
    }

    public record RouteRequest(@JsonAlias("from_node_key") String fromNode,
                               @JsonAlias("to_node_key") String toNode,
                               String mode,
                               Boolean accessibleOnly,
                               List<String> primaryPathNodes,
                               String facilityType) {
        public boolean stepFree() {
            return Boolean.TRUE.equals(accessibleOnly) || "accessible".equalsIgnoreCase(mode);
        }
    }

    public record LoginRequest(String email, String password) {
    }

    public record RegisterRequest(String email, String password, String firstName, String lastName, String role,
                                  String studentId, String department) {
    }

    public record LocationRequest(Double latitude, Double longitude) {
    }

    public record ReportRequest(Long pathwayId, String reportType, String description, String locationDetails) {
    }

    public record CloseRequest(String reason, String description) {
    }

    /** Reads an optional string from a loosely-typed JSON object. */
    public static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString().trim();
    }

    public static Double num(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("'" + key + "' must be a number.");
            }
        }
        return null;
    }

    public static Boolean bool(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return v == null ? null : Boolean.parseBoolean(v.toString());
    }
}
