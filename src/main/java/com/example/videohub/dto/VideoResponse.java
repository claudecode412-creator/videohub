package com.example.videohub.dto;

import java.time.Instant;

import com.example.videohub.model.Video;

/**
 * What the API sends back to clients. We never expose the on-disk stored
 * filename; clients only get a stream URL to play the video.
 *
 * <p>{@code premium} says the video needs a subscription; {@code locked} says
 * <em>this</em> viewer can't watch it yet (premium and not subscribed).
 */
public record VideoResponse(
        Long id,
        String title,
        String description,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Double durationSeconds,
        long views,
        long likes,
        Instant uploadedAt,
        boolean premium,
        boolean locked,
        String streamUrl) {

    public static VideoResponse from(Video v) {
        return from(v, false);
    }

    public static VideoResponse from(Video v, boolean locked) {
        return new VideoResponse(
                v.getId(),
                v.getTitle(),
                v.getDescription(),
                v.getOriginalFilename(),
                v.getContentType(),
                v.getSizeBytes(),
                v.getDurationSeconds(),
                v.getViews(),
                v.getLikes(),
                v.getUploadedAt(),
                v.isPremium(),
                locked,
                "/api/videos/" + v.getId() + "/stream");
    }
}
