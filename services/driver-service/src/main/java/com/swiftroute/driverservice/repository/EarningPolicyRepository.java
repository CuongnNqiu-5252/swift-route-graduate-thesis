package com.swiftroute.driverservice.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.swiftroute.driverservice.entity.EarningPolicy;

public interface EarningPolicyRepository extends JpaRepository<EarningPolicy, UUID> {}
