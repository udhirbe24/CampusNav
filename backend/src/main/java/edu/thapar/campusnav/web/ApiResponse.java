package edu.thapar.campusnav.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the standard JSON envelope: {"success": true|false, "message": "...", "data": ...}. */
public final class ApiResponse {

    private ApiResponse() {
    }

    public static ResponseEntity<Map<String, Object>> ok(Object data) {
        return ok(data, "Success");
    }

    public static ResponseEntity<Map<String, Object>> ok(Object data, String message) {
        return build(HttpStatus.OK, true, message, data);
    }

    public static ResponseEntity<Map<String, Object>> created(Object data, String message) {
        return build(HttpStatus.CREATED, true, message, data);
    }

    public static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return build(status, false, message, null);
    }

    private static ResponseEntity<Map<String, Object>> build(HttpStatus status, boolean success, String message,
                                                             Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put("message", message);
        if (data != null) {
            body.put("data", data);
        }
        return ResponseEntity.status(status).body(body);
    }
}
