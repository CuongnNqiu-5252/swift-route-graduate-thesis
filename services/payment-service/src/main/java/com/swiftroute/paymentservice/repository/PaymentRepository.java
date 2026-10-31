package com.swiftroute.paymentservice.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.swiftroute.paymentservice.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByTxnRef(String txnRef);
    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
