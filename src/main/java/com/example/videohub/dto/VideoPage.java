package com.example.videohub.dto;

import java.util.List;

/**
 * A single page of videos plus the paging info the frontend needs to know
 * whether to keep loading more (infinite scroll).
 */
public record VideoPage(
        List<VideoResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {
}
