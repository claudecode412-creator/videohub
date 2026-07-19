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
 * A successful subscription-pass payment. We keep one row per Stripe checkout
 * session so a webhook that fires twice can't grant access twice.
 */
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payment_session", columnList = "stripeSessionId", unique = true))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** Amount paid in the smallest currency unit (paise for INR). */
    private Long amountMinor;

    private String currency;

    /** How many days of access this payment granted. */
    private int daysGranted;

    @Column(nullable = false, unique = true)
    private String stripeSessionId;

    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public int getDaysGranted() { return daysGranted; }
    public void setDaysGranted(int daysGranted) { this.daysGranted = daysGranted; }

    public String getStripeSessionId() { return stripeSessionId; }
    public void setStripeSessionId(String stripeSessionId) { this.stripeSessionId = stripeSessionId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
