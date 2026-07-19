package com.example.videohub.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.videohub.model.Video;

public interface VideoRepository extends JpaRepository<Video, Long> {

    /** One page of videos, newest first — used for infinite scroll. */
    Page<Video> findAllByOrderByUploadedAtDesc(Pageable pageable);

    /** One page of videos whose title matches the search term, newest first. */
    Page<Video> findByTitleContainingIgnoreCaseOrderByUploadedAtDesc(String title, Pageable pageable);

    /** Sum of all view counts, for the stats banner. */
    @Query("select coalesce(sum(v.views), 0) from Video v")
    long totalViews();
}
