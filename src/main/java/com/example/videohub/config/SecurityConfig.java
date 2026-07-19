package com.example.videohub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Splits the app into two zones:
 *
 *   - PUBLIC (anyone, no login): watching, listing, search, stats, and
 *     recording a view or a like.
 *   - ADMIN (login required): the /admin pages and anything that creates,
 *     edits, or deletes a video.
 *
 * Visitors never see the admin area — it lives at /admin behind a password.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // --- Public: the site itself + its assets + viewer auth pages ---
                .requestMatchers(HttpMethod.GET,
                        "/", "/index.html", "/css/**", "/js/**", "/favicon.ico", "/error",
                        "/login", "/signup", "/subscribe").permitAll()
                // --- Public: read-only video API ---
                .requestMatchers(HttpMethod.GET,
                        "/api/videos", "/api/videos/stats", "/api/videos/*", "/api/videos/*/stream").permitAll()
                // --- Public: viewer accounts (sign up / log in / log out / who am I) ---
                .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                // --- Public: subscription status + checkout, and the Stripe webhook.
                //     (checkout itself checks the viewer is logged in.) ---
                .requestMatchers(HttpMethod.GET, "/api/subscription/me").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/subscription/checkout", "/api/payments/webhook").permitAll()
                // --- Public: viewers can record a view or toggle a like ---
                .requestMatchers(HttpMethod.POST, "/api/videos/*/view", "/api/videos/*/like").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/videos/*/like").permitAll()
                // --- Dev tool ---
                .requestMatchers("/h2-console/**").permitAll()
                // --- Everything else (upload, edit, delete, /admin) needs login ---
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .defaultSuccessUrl("/admin", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            )
            // CSRF is disabled so the admin JS can POST/DELETE with plain fetch.
            // Fine for this single-admin app; revisit before a public deployment.
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /** The one admin account, read from application.properties and hashed at startup. */
    @Bean
    public UserDetailsService adminUser(
            @Value("${app.admin.username}") String username,
            @Value("${app.admin.password}") String password,
            PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(encoder.encode(password))
                        .roles("ADMIN")
                        .build());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
