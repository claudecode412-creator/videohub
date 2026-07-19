package com.example.videohub.dto;

import java.time.Instant;

import com.example.videohub.model.Video;

/**
 * What the API sends back to clients. We never expose the on-disk stored
 * filename; clients only get a stream URL to play the video.
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
        String streamUrl) {

    public static VideoResponse from(Video v) {
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
                "/api/videos/" + v.getId() + "/stream");
    }
}
