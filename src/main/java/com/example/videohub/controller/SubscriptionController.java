package com.example.videohub.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.videohub.dto.SubscriptionStatus;
import com.example.videohub.model.User;
import com.example.videohub.repository.UserRepository;
import com.example.videohub.service.AccessService;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import jakarta.servlet.http.HttpSession;

/**
 * The subscription pass: report the viewer's status, and start a Stripe
 * checkout to buy/renew a {@code app.pass.days}-day pass.
 */
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    @Value("${app.pass.price-minor:9900}")
    private long priceMinor;

    @Value("${app.pass.currency:inr}")
    private String currency;

    @Value("${app.pass.days:30}")
    private int days;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    private final UserRepository users;
    private final AccessService access;

    public SubscriptionController(UserRepository users, AccessService access) {
        this.users = users;
        this.access = access;
    }

    /** The viewer's current pass status + the price to show in the UI. */
    @GetMapping("/me")
    public SubscriptionStatus me(HttpSession session) {
        Long uid = access.currentUserId(session);
        boolean loggedIn = uid != null;
        boolean active = false;
        java.time.Instant until = null;
        if (loggedIn) {
            User u = users.findById(uid).orElse(null);
            if (u != null) {
                active = u.hasActiveAccess();
                until = u.getAccessUntil();
            }
        }
        return new SubscriptionStatus(loggedIn, active, until, priceMinor, currency, days);
    }

    /**
     * Start a Stripe Checkout for the pass. Returns a URL the browser should be
     * sent to. Requires the viewer to be logged in.
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(HttpSession session) {
        Long uid = access.currentUserId(session);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please log in first."));
        }
        if (!StringUtils.hasText(stripeSecretKey)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Payments aren't configured yet."));
        }

        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        Stripe.apiKey = stripeSecretKey;
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(base + "/?subscribed=1")
                    .setCancelUrl(base + "/subscribe?canceled=1")
                    .putMetadata("userId", String.valueOf(uid))
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(currency)
                                    .setUnitAmount(priceMinor)
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(days + "-day VideoHub pass")
                                            .build())
                                    .build())
                            .build())
                    .build();
            Session checkout = Session.create(params);
            return ResponseEntity.ok(Map.of("url", checkout.getUrl()));
        } catch (Exception e) {
            // Log the real cause (e.g. an unsupported currency) instead of hiding it.
            log.error("Stripe checkout failed for user {} ({} {})", uid, priceMinor, currency, e);
            String reason = e.getMessage() == null ? "unknown error" : e.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Could not start checkout: " + reason));
        }
    }
}
