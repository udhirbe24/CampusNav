package edu.thapar.campusnav.web;

import edu.thapar.campusnav.model.User;
import edu.thapar.campusnav.repository.UserRepository;
import edu.thapar.campusnav.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Registration, login, logout and profile. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionAuth sessionAuth;
    private final UserRepository users;

    public AuthController(AuthService authService, SessionAuth sessionAuth, UserRepository users) {
        this.authService = authService;
        this.sessionAuth = sessionAuth;
        this.users = users;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RequestBodies.RegisterRequest body) {
        User user = authService.register(new AuthService.Registration(body.email(), body.password(),
                body.firstName(), body.lastName(), body.role(), body.studentId(), body.department()));
        return ApiResponse.created(user.toMap(), "Account created successfully.");
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody RequestBodies.LoginRequest body,
                                                     HttpServletRequest request) {
        User user = authService.authenticate(body.email(), body.password());
        sessionAuth.login(request, user);
        return ApiResponse.ok(user.toMap(), "Logged in successfully.");
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        sessionAuth.logout(request);
        return ApiResponse.ok(null, "Logged out successfully.");
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        return ApiResponse.ok(sessionAuth.requireUser(request).toMap());
    }

    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body,
                                                             HttpServletRequest request) {
        User user = sessionAuth.requireUser(request);
        String first = RequestBodies.str(body, "first_name");
        String last = RequestBodies.str(body, "last_name");
        if (first != null && !first.isEmpty()) {
            user.setFirstName(first);
        }
        if (last != null && !last.isEmpty()) {
            user.setLastName(last);
        }
        if (body.containsKey("department")) {
            user.setDepartment(RequestBodies.str(body, "department"));
        }
        return ApiResponse.ok(users.save(user).toMap(), "Profile updated.");
    }
}
