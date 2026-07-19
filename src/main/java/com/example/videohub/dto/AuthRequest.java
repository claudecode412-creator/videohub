package com.example.videohub.dto;

/** The email + password a viewer sends when signing up or logging in. */
public record AuthRequest(String email, String password) {
}
