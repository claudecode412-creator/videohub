package com.example.videohub.dto;

import java.time.Instant;

/**
 * The current viewer's subscription state plus the pass price, so the UI can
 * show either "you're a member until X" or a "Subscribe for ₹99" button.
 */
public record SubscriptionStatus(
        boolean loggedIn,
        boolean active,
        Instant until,
        long priceMinor,
        String currency,
        int days) {
}
