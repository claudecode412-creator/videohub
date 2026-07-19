package com.example.videohub.controller;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.videohub.dto.AuthRequest;
import com.example.videohub.dto.UserResponse;
import com.example.videohub.model.User;
import com.example.videohub.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

/**
 * Viewer accounts: sign up, log in, log out, and "who am I".
 *
 * <p>This is separate from the single admin login (which is handled by Spring
 * Security). A logged-in viewer is remembered with a {@code userId} attribute on
 * the HTTP session — it does NOT grant any admin access.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Session key holding the id of the logged-in viewer. */
    public static final String SESSION_USER = "userId";

    /** Simple sanity check for an email shape: something@something.something */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final int MIN_PASSWORD = 8;

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody AuthRequest req, HttpSession session) {
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        String password = req.password() == null ? "" : req.password();

        if (!EMAIL.matcher(email).matches()) {
            return error(HttpStatus.BAD_REQUEST, "Please enter a valid email address.");
        }
        // Reject "plus-addressing" aliases like name+123@gmail.com — the part
        // before the '@' may not contain a '+'.
        String localPart = email.substring(0, email.indexOf('@'));
        if (localPart.contains("+")) {
            return error(HttpStatus.BAD_REQUEST,
                    "Emails with a '+' alias (like name+123@…) aren't allowed. Please use your real email address.");
        }
        if (password.length() < MIN_PASSWORD) {
            return error(HttpStatus.BAD_REQUEST, "Password must be at least " + MIN_PASSWORD + " characters.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            return error(HttpStatus.CONFLICT, "That email is already registered. Try logging in instead.");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        user.setDisplayName(localPart);
        user.setCreatedAt(Instant.now());
        users.save(user);

        session.setAttribute(SESSION_USER, user.getId()); // auto-login after signup
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req, HttpSession session) {
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        String password = req.password() == null ? "" : req.password();

        Optional<User> found = users.findByEmailIgnoreCase(email);
        if (found.isEmpty() || !encoder.matches(password, found.get().getPasswordHash())) {
            // Same message either way, so we don't reveal which emails exist.
            return error(HttpStatus.UNAUTHORIZED, "Wrong email or password.");
        }
        session.setAttribute(SESSION_USER, found.get().getId());
        return ResponseEntity.ok(UserResponse.from(found.get()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.removeAttribute(SESSION_USER);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        Object uid = session.getAttribute(SESSION_USER);
        if (uid instanceof Long id) {
            Optional<User> user = users.findById(id);
            if (user.isPresent()) {
                return ResponseEntity.ok(UserResponse.from(user.get()));
            }
        }
        return error(HttpStatus.UNAUTHORIZED, "Not logged in.");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
