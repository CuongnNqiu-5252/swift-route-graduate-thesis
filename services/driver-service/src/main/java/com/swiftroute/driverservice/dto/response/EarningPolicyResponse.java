package com.swiftroute.driverservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import com.swiftroute.driverservice.entity.EarningPolicy;

public record EarningPolicyResponse(UUID tenantId, BigDecimal baseAmount, BigDecimal perKmAmount,
        BigDecimal minimumAmount, BigDecimal heavyWeightThresholdKg, BigDecimal heavyBonusAmount, String currency) {
    public static EarningPolicyResponse from(EarningPolicy policy) {
        return new EarningPolicyResponse(policy.getTenantId(), policy.getBaseAmount(), policy.getPerKmAmount(),
                policy.getMinimumAmount(), policy.getHeavyWeightThresholdKg(), policy.getHeavyBonusAmount(), policy.getCurrency());
    }
}
