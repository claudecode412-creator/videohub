package com.example.videohub.dto;

import com.example.videohub.model.User;

/** What we send back about a logged-in viewer — never the password hash. */
public record UserResponse(Long id, String email, String displayName) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName());
    }
}
