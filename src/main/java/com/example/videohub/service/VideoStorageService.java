package com.example.videohub.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores uploaded video files and reads them back.
 *
 * <p>Two backends, chosen automatically at startup:
 * <ul>
 *   <li><b>Cloudflare R2</b> (S3-compatible object storage) when the R2
 *       settings are provided — used in the cloud so videos survive restarts.</li>
 *   <li><b>Local filesystem</b> otherwise — used for local development, exactly
 *       as before. Files are spread across 256 sub-directories so no single
 *       folder ever holds all the videos.</li>
 * </ul>
 *
 * Either way, {@link #store} returns a key like {@code "a1/a1b2c3....mp4"} that
 * is saved on the video row; the rest of the app never cares which backend is used.
 */
@Service
public class VideoStorageService {

    // --- Local backend ---
    private final Path root;

    // --- R2 backend (all blank locally → local backend is used) ---
    private final String r2Bucket;
    private final String r2Endpoint;
    private final String r2AccessKey;
    private final String r2SecretKey;
    private final String r2PublicBaseUrl;

    private boolean useR2;
    private S3Client s3;

    public VideoStorageService(
            @Value("${app.storage.location:uploads}") String location,
            @Value("${app.storage.r2.bucket:}") String r2Bucket,
            @Value("${app.storage.r2.endpoint:}") String r2Endpoint,
            @Value("${app.storage.r2.access-key:}") String r2AccessKey,
            @Value("${app.storage.r2.secret-key:}") String r2SecretKey,
            @Value("${app.storage.r2.public-base-url:}") String r2PublicBaseUrl) {
        this.root = Paths.get(location).toAbsolutePath().normalize();
        this.r2Bucket = r2Bucket;
        this.r2Endpoint = r2Endpoint;
        this.r2AccessKey = r2AccessKey;
        this.r2SecretKey = r2SecretKey;
        // Trim any trailing slash so we can join with "/" + key cleanly.
        this.r2PublicBaseUrl = r2PublicBaseUrl != null ? r2PublicBaseUrl.replaceAll("/+$", "") : "";
    }

    @PostConstruct
    public void init() {
        this.useR2 = StringUtils.hasText(r2Bucket)
                && StringUtils.hasText(r2Endpoint)
                && StringUtils.hasText(r2AccessKey)
                && StringUtils.hasText(r2SecretKey)
                && StringUtils.hasText(r2PublicBaseUrl);

        if (useR2) {
            this.s3 = S3Client.builder()
                    .endpointOverride(URI.create(r2Endpoint))
                    .region(Region.of("auto")) // R2 ignores the region but the SDK requires one
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(r2AccessKey, r2SecretKey)))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
        } else {
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not create storage directory: " + root, e);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        if (s3 != null) {
            s3.close();
        }
    }

    /** True when videos are being served straight from R2 (see {@link #redirectUrl}). */
    public boolean isRemote() {
        return useR2;
    }

    /**
     * Saves the uploaded file and returns its storage key (e.g. {@code "a1/a1b2....mp4"}),
     * which is the same shape whether the backend is R2 or the local disk.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        String original = StringUtils.cleanPath(
                Objects.requireNonNullElse(file.getOriginalFilename(), "video"));

        String extension = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0 && dot < original.length() - 1) {
            extension = original.substring(dot).toLowerCase();
        }

        String name = UUID.randomUUID().toString().replace("-", "") + extension;
        String shard = name.substring(0, 2);
        String key = shard + "/" + name;

        return useR2 ? storeR2(file, key) : storeLocal(file, original, key);
    }

    private String storeR2(MultipartFile file, String key) {
        String contentType = file.getContentType() != null ? file.getContentType() : "video/mp4";
        try (InputStream in = file.getInputStream()) {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(r2Bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upload file to R2: " + key, e);
        }
        return key;
    }

    private String storeLocal(MultipartFile file, String original, String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            // Defends against path traversal via a crafted filename/extension.
            throw new IllegalArgumentException("Cannot store file outside storage directory");
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file " + original, e);
        }
        return key;
    }

    /**
     * When videos live in R2, returns the public URL to redirect the browser to
     * (so R2 streams the bytes directly, with byte-range support, at no egress
     * cost). Returns empty when using local disk — the app streams those itself.
     */
    public Optional<String> redirectUrl(String storedFilename) {
        if (!useR2) {
            return Optional.empty();
        }
        return Optional.of(r2PublicBaseUrl + "/" + storedFilename);
    }

    /**
     * Loads a locally-stored file as a readable resource for streaming.
     * Only used when running on the local-disk backend.
     */
    public UrlResource loadAsResource(String storedFilename) {
        try {
            Path file = root.resolve(storedFilename).normalize();
            if (!file.startsWith(root)) {
                throw new IllegalArgumentException("Invalid stored path: " + storedFilename);
            }
            UrlResource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException("File not found or unreadable: " + storedFilename);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new IllegalStateException("File not found: " + storedFilename, e);
        }
    }

    public void delete(String storedFilename) {
        if (useR2) {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(r2Bucket)
                    .key(storedFilename)
                    .build());
            return;
        }
        try {
            Path file = root.resolve(storedFilename).normalize();
            if (file.startsWith(root)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file " + storedFilename, e);
        }
    }
}
