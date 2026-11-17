package edu.thapar.campusnav.service;

import edu.thapar.campusnav.model.User;
import edu.thapar.campusnav.repository.UserRepository;
import edu.thapar.campusnav.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

/** Registration, login and password hashing (BCrypt). */
@Service
public class AuthService {

    public static final Set<String> ROLES = Set.of("student", "faculty", "visitor", "admin");
    /** Roles anyone can sign up as; admin must be granted by another admin. */
    private static final Set<String> SELF_SERVICE_ROLES = Set.of("student", "faculty", "visitor");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private final UserRepository users;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository users) {
        this.users = users;
    }

    public record Registration(String email, String password, String firstName, String lastName, String role,
                               String studentId, String department) {
    }

    public User register(Registration r) {
        String email = r.email() == null ? "" : r.email().trim().toLowerCase();
        if (!EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("Please enter a valid email address.");
        }
        String passwordProblem = passwordProblem(r.password());
        if (passwordProblem != null) {
            throw ApiException.badRequest(passwordProblem);
        }
        if (isBlank(r.firstName()) || isBlank(r.lastName())) {
            throw ApiException.badRequest("First name and last name are required.");
        }
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.badRequest("An account with this email already exists.");
        }
        String role = r.role() == null ? "visitor" : r.role().trim().toLowerCase();
        if (!SELF_SERVICE_ROLES.contains(role)) {
            role = "visitor";
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(r.password()));
        user.setFirstName(r.firstName().trim());
        user.setLastName(r.lastName().trim());
        user.setRole(role);
        user.setStudentId(blankToNull(r.studentId()));
        user.setDepartment(blankToNull(r.department()));
        return users.save(user);
    }

    public User authenticate(String email, String password) {
        String normalised = email == null ? "" : email.trim().toLowerCase();
        User user = users.findByEmailIgnoreCase(normalised)
                .filter(u -> password != null && encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is deactivated. Contact an administrator.");
        }
        user.setLastLogin(Instant.now());
        return users.save(user);
    }

    public String hash(String password) {
        return encoder.encode(password);
    }

    /** Returns why a password is too weak, or null if it is fine. */
    public static String passwordProblem(String password) {
        if (password == null || password.length() < 8) {
            return "Password must be at least 8 characters long.";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter.";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter.";
        }
        if (!password.matches(".*\\d.*")) {
            return "Password must contain at least one digit.";
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
