package com.example.videohub.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.videohub.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByStripeSessionId(String stripeSessionId);
}
