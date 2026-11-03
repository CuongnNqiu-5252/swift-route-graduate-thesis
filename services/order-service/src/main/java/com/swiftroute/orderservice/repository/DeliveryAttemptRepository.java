package com.swiftroute.orderservice.repository;
import java.util.UUID; import org.springframework.data.jpa.repository.*; import com.swiftroute.orderservice.entity.DeliveryAttempt;
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt,UUID> { @Query("select coalesce(max(a.attemptNumber),0) from DeliveryAttempt a where a.order.id=:orderId") int maxAttemptNumber(UUID orderId); }
