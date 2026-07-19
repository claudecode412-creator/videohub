package com.example.videohub.dto;

/**
 * The editable fields of a video, sent by the admin "Edit" form as JSON.
 * A blank title is ignored so a video can't lose its name. {@code premium}
 * marks the video as subscribers-only.
 */
public record UpdateVideoRequest(String title, String description, Boolean premium) {
}
