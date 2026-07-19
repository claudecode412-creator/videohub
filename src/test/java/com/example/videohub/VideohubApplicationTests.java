package com.example.videohub;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.videohub.controller.VideoController;

@SpringBootTest
class VideohubApplicationTests {

    @Autowired
    private VideoController videoController;

    @Test
    void contextLoads() {
        // Fails the build if the Spring context (JPA, web, storage) can't start.
        assertThat(videoController).isNotNull();
    }
}
