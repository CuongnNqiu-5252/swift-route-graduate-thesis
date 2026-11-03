package com.swiftroute.orderservice.repository; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import com.swiftroute.orderservice.entity.DeliveryIncident;
public interface DeliveryIncidentRepository extends JpaRepository<DeliveryIncident,UUID>{ List<DeliveryIncident> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId,String status); }
