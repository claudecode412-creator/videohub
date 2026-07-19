package com.example.videohub.controller;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.videohub.dto.StatsResponse;
import com.example.videohub.dto.UpdateVideoRequest;
import com.example.videohub.dto.VideoPage;
import com.example.videohub.dto.VideoResponse;
import com.example.videohub.model.Video;
import com.example.videohub.repository.VideoRepository;
import com.example.videohub.service.AccessService;
import com.example.videohub.service.VideoStorageService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    /** How many bytes to serve per range request. Keeps memory use bounded. */
    private static final long CHUNK_SIZE = 1024L * 1024L; // 1 MB

    /** Cap the page size so a client can't ask for everything at once. */
    private static final int MAX_PAGE_SIZE = 100;

    private final VideoRepository repository;
    private final VideoStorageService storage;
    private final AccessService access;

    public VideoController(VideoRepository repository, VideoStorageService storage, AccessService access) {
        this.repository = repository;
        this.storage = storage;
        this.access = access;
    }

    /** Upload a new video (multipart form: file + optional title/description/duration). */
    @PostMapping
    public ResponseEntity<VideoResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "durationSeconds", required = false) Double durationSeconds) {

        String stored = storage.store(file);

        Video video = new Video();
        video.setTitle((title == null || title.isBlank()) ? file.getOriginalFilename() : title.trim());
        video.setDescription(description);
        video.setOriginalFilename(file.getOriginalFilename());
        video.setStoredFilename(stored);
        video.setContentType(file.getContentType() != null ? file.getContentType() : "video/mp4");
        video.setSizeBytes(file.getSize());
        video.setDurationSeconds(durationSeconds);
        video.setViews(0);
        video.setUploadedAt(Instant.now());

        Video saved = repository.save(video);
        return ResponseEntity.status(HttpStatus.CREATED).body(VideoResponse.from(saved));
    }

    /**
     * One page of videos, newest first. Supports an optional {@code search}
     * term. This is what powers infinite scroll and search on the home page.
     */
    @GetMapping
    public VideoPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) String search,
            HttpSession session) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);

        Page<Video> result = (search == null || search.isBlank())
                ? repository.findAllByOrderByUploadedAtDesc(pageable)
                : repository.findByTitleContainingIgnoreCaseOrderByUploadedAtDesc(search.trim(), pageable);

        // A premium video is "locked" unless this viewer can watch it.
        boolean canWatchPremium = access.isAdmin() || access.viewerHasActiveAccess(session);
        List<VideoResponse> content = result.getContent().stream()
                .map(v -> VideoResponse.from(v, v.isPremium() && !canWatchPremium))
                .toList();

        return new VideoPage(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    /** Site-wide totals for the hero banner. */
    @GetMapping("/stats")
    public StatsResponse stats() {
        return new StatsResponse(repository.count(), repository.totalViews());
    }

    /** Metadata for a single video. */
    @GetMapping("/{id}")
    public VideoResponse get(@PathVariable Long id, HttpSession session) {
        Video video = find(id);
        return VideoResponse.from(video, video.isPremium() && !access.canWatch(video, session));
    }

    /**
     * Edit a video's title and/or description (admin only). A blank title is
     * ignored so a video can never end up nameless; the description can be
     * cleared by sending an empty value.
     */
    @PutMapping("/{id}")
    public VideoResponse update(@PathVariable Long id, @RequestBody UpdateVideoRequest req) {
        Video video = find(id);
        if (req.title() != null && !req.title().isBlank()) {
            video.setTitle(req.title().trim());
        }
        video.setDescription(req.description() != null ? req.description().trim() : null);
        if (req.premium() != null) {
            video.setPremium(req.premium());
        }
        return VideoResponse.from(repository.save(video));
    }

    /** Record a view (called when the player opens) and return the new count. */
    @PostMapping("/{id}/view")
    public VideoResponse recordView(@PathVariable Long id) {
        Video video = find(id);
        video.setViews(video.getViews() + 1);
        return VideoResponse.from(repository.save(video));
    }

    /** Add a like (viewers, no login required). Returns the updated video. */
    @PostMapping("/{id}/like")
    public VideoResponse like(@PathVariable Long id) {
        Video video = find(id);
        video.setLikes(video.getLikes() + 1);
        return VideoResponse.from(repository.save(video));
    }

    /** Remove a like (when a viewer un-likes). Never goes below zero. */
    @DeleteMapping("/{id}/like")
    public VideoResponse unlike(@PathVariable Long id) {
        Video video = find(id);
        video.setLikes(Math.max(0, video.getLikes() - 1));
        return VideoResponse.from(repository.save(video));
    }

    /**
     * Streams the video bytes with HTTP Range support, which is what lets the
     * browser's &lt;video&gt; player seek and buffer instead of downloading the
     * whole file up front.
     */
    @GetMapping("/{id}/stream")
    public ResponseEntity<ResourceRegion> stream(
            @PathVariable Long id,
            @RequestHeader HttpHeaders headers,
            HttpSession session) throws IOException {

        Video video = find(id);

        // Premium videos are only streamable by the admin or an active subscriber.
        if (!access.canWatch(video, session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This video requires a subscription.");
        }

        // When videos live in R2, hand the browser a redirect to the public R2
        // URL so R2 streams the bytes directly (byte-range support, no egress cost).
        Optional<String> redirect = storage.redirectUrl(video.getStoredFilename());
        if (redirect.isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirect.get()))
                    .build();
        }

        UrlResource resource = storage.loadAsResource(video.getStoredFilename());
        long contentLength = resource.contentLength();

        List<HttpRange> ranges = headers.getRange();
        ResourceRegion region;
        HttpStatus status;

        if (ranges.isEmpty()) {
            // No Range header: hand back the whole file (e.g. a direct download).
            region = new ResourceRegion(resource, 0, contentLength);
            status = HttpStatus.OK;
        } else {
            // Serve one bounded chunk starting at the requested offset.
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long length = Math.min(CHUNK_SIZE, end - start + 1);
            region = new ResourceRegion(resource, start, length);
            status = HttpStatus.PARTIAL_CONTENT;
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElseGet(() -> safeMediaType(video.getContentType()));

        return ResponseEntity.status(status)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    /** Delete a video's metadata and its file on disk. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Video video = find(id);
        storage.delete(video.getStoredFilename());
        repository.delete(video);
        return ResponseEntity.noContent().build();
    }

    private Video find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found: " + id));
    }

    private static MediaType safeMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
