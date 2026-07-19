package com.example.videohub.dto;

/** Site-wide totals shown in the hero banner. */
public record StatsResponse(long totalVideos, long totalViews) {
}
