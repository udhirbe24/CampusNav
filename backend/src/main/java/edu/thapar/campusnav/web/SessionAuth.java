package edu.thapar.campusnav.web;

import edu.thapar.campusnav.model.User;
import edu.thapar.campusnav.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cookie-session login. After a successful login the user's id is stored in the HTTP session;
 * controllers call {@link #requireUser} / {@link #requireAdmin} to protect endpoints.
 */
@Component
public class SessionAuth {

    private static final String USER_ID = "campusnav.userId";

    private final UserRepository users;

    public SessionAuth(UserRepository users) {
        this.users = users;
    }

    public void login(HttpServletRequest request, User user) {
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate(); // new session id on login prevents session fixation
        }
        request.getSession(true).setAttribute(USER_ID, user.getId());
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public Optional<User> currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(USER_ID) instanceof Long id)) {
            return Optional.empty();
        }
        return users.findById(id).filter(User::isActive);
    }

    public User requireUser(HttpServletRequest request) {
        return currentUser(request).orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required. Please log in."));
    }

    public User requireAdmin(HttpServletRequest request) {
        User user = requireUser(request);
        if (!user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Administrator privileges required.");
        }
        return user;
    }
}
