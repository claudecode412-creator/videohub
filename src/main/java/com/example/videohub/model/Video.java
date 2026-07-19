package com.example.videohub.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Metadata for one uploaded video. The actual video bytes live on disk;
 * this row only records where the file is and how to describe it.
 *
 * The index on {@code uploaded_at} keeps the "newest first" listing fast even
 * with thousands of rows.
 */
@Entity
@Table(name = "videos", indexes = @Index(name = "idx_videos_uploaded_at", columnList = "uploadedAt"))
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 2000)
    private String description;

    /** The name the file had on the uploader's machine. */
    private String originalFilename;

    /** Where the file is saved, relative to the storage root (e.g. "a1/a1b2....mp4"). */
    private String storedFilename;

    private String contentType;

    private long sizeBytes;

    /** Length of the video in seconds, captured in the browser at upload time (may be null). */
    private Double durationSeconds;

    /**
     * When true, only viewers with an active subscription pass can watch this
     * video. Nullable on purpose: existing rows predate this column, and a
     * missing value simply means "not premium".
     */
    private Boolean premium;

    /** How many times the video has been opened in the player. */
    private long views;

    /** How many likes the video has received from viewers. */
    private long likes;

    private Instant uploadedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Double getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    /** Null-safe: a missing value means the video is free. */
    public boolean isPremium() {
        return premium != null && premium;
    }

    public void setPremium(Boolean premium) {
        this.premium = premium;
    }

    public long getViews() {
        return views;
    }

    public void setViews(long views) {
        this.views = views;
    }

    public long getLikes() {
        return likes;
    }

    public void setLikes(long likes) {
        this.likes = likes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
