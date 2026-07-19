package com.example.videohub.dto;

/**
 * The editable fields of a video, sent by the admin "Edit" form as JSON.
 * Both are optional; a blank title is ignored so a video can't lose its name.
 */
public record UpdateVideoRequest(String title, String description) {
}
