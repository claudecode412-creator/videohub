package com.example.videohub.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.videohub.controller.AuthController;
import com.example.videohub.model.User;
import com.example.videohub.model.Video;
import com.example.videohub.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

/**
 * Decides who is allowed to watch a video. Free videos are open to everyone;
 * premium videos need either the admin (logged in via Spring Security) or a
 * viewer with an active subscription pass.
 */
@Service
public class AccessService {

    private final UserRepository users;

    public AccessService(UserRepository users) {
        this.users = users;
    }

    /** The logged-in viewer's id from the session, or null if not logged in. */
    public Long currentUserId(HttpSession session) {
        Object uid = session.getAttribute(AuthController.SESSION_USER);
        return (uid instanceof Long id) ? id : null;
    }

    /** True if the session's viewer currently has an active subscription pass. */
    public boolean viewerHasActiveAccess(HttpSession session) {
        Long uid = currentUserId(session);
        if (uid == null) {
            return false;
        }
        return users.findById(uid).map(User::hasActiveAccess).orElse(false);
    }

    /** True if the current request is the authenticated admin (Spring Security). */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    /** Whoever is behind this request — can they watch this specific video? */
    public boolean canWatch(Video video, HttpSession session) {
        if (!video.isPremium()) {
            return true;
        }
        return isAdmin() || viewerHasActiveAccess(session);
    }
}
