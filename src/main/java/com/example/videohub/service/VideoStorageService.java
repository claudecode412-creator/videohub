package com.example.videohub.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

/**
 * Stores uploaded video files on the local filesystem and reads them back.
 *
 * Files are spread across 256 sub-directories (named by the first two hex
 * characters of their random name), so no single folder ever holds all the
 * videos. That keeps the filesystem fast even with tens of thousands of files.
 */
@Service
public class VideoStorageService {

    private final Path root;

    public VideoStorageService(@Value("${app.storage.location:uploads}") String location) {
        this.root = Paths.get(location).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create storage directory: " + root, e);
        }
    }

    /**
     * Saves the uploaded file and returns its path relative to the storage
     * root (e.g. {@code "a1/a1b2c3....mp4"}).
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
        String relative = shard + "/" + name;

        Path target = root.resolve(relative).normalize();
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
        return relative;
    }

    /**
     * Loads a stored file as a readable resource for streaming.
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
