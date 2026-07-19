package com.example.videohub.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.videohub.model.Payment;
import com.example.videohub.model.User;
import com.example.videohub.repository.PaymentRepository;
import com.example.videohub.repository.UserRepository;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

/**
 * Receives Stripe webhook events. When a checkout completes, we verify the
 * signature, then extend the buyer's subscription pass by {@code app.pass.days}.
 * Recorded per Stripe session id so a retried webhook can't double-credit.
 */
@RestController
public class PaymentWebhookController {

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.pass.days:30}")
    private int days;

    private final UserRepository users;
    private final PaymentRepository payments;

    public PaymentWebhookController(UserRepository users, PaymentRepository payments) {
        this.users = users;
        this.payments = payments;
    }

    @PostMapping("/api/payments/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload,
                                          @RequestHeader("Stripe-Signature") String signature) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
            if (session != null && "paid".equals(session.getPaymentStatus())) {
                grantAccess(session);
            }
        }
        return ResponseEntity.ok("ok");
    }

    private void grantAccess(Session session) {
        String sessionId = session.getId();
        if (payments.existsByStripeSessionId(sessionId)) {
            return; // already processed this payment
        }
        String userIdStr = session.getMetadata() != null ? session.getMetadata().get("userId") : null;
        if (userIdStr == null) {
            return;
        }
        User user;
        try {
            user = users.findById(Long.valueOf(userIdStr)).orElse(null);
        } catch (NumberFormatException e) {
            return;
        }
        if (user == null) {
            return;
        }

        // Extend from whatever's later: now, or an existing unexpired pass.
        Instant start = user.hasActiveAccess() ? user.getAccessUntil() : Instant.now();
        user.setAccessUntil(start.plus(days, ChronoUnit.DAYS));
        users.save(user);

        Payment payment = new Payment();
        payment.setUserId(user.getId());
        payment.setStripeSessionId(sessionId);
        payment.setAmountMinor(session.getAmountTotal());
        payment.setCurrency(session.getCurrency());
        payment.setDaysGranted(days);
        payment.setCreatedAt(Instant.now());
        payments.save(payment);
    }
}
