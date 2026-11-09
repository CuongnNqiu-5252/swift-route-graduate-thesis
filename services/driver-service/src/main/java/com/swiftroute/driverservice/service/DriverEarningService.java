package com.swiftroute.driverservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftroute.driverservice.dto.event.DeliveryCompletedEvent;
import com.swiftroute.driverservice.dto.request.CreateSettlementRequest;
import com.swiftroute.driverservice.dto.request.UpdateEarningPolicyRequest;
import com.swiftroute.driverservice.dto.response.DriverEarningResponse;
import com.swiftroute.driverservice.dto.response.DriverSettlementResponse;
import com.swiftroute.driverservice.dto.response.EarningPolicyResponse;
import com.swiftroute.driverservice.dto.response.EarningSummaryResponse;
import com.swiftroute.driverservice.entity.Driver;
import com.swiftroute.driverservice.entity.DriverEarning;
import com.swiftroute.driverservice.entity.DriverSettlement;
import com.swiftroute.driverservice.entity.EarningPolicy;
import com.swiftroute.driverservice.entity.OutboxEvent;
import com.swiftroute.driverservice.entity.enums.EarningStatus;
import com.swiftroute.driverservice.entity.enums.SettlementStatus;
import com.swiftroute.driverservice.exception.AppException;
import com.swiftroute.driverservice.exception.ErrorCode;
import com.swiftroute.driverservice.repository.DriverEarningRepository;
import com.swiftroute.driverservice.repository.DriverRepository;
import com.swiftroute.driverservice.repository.DriverSettlementRepository;
import com.swiftroute.driverservice.repository.EarningPolicyRepository;
import com.swiftroute.driverservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DriverEarningService {
    private static final BigDecimal DEFAULT_BASE = new BigDecimal("12000");
    private static final BigDecimal DEFAULT_PER_KM = new BigDecimal("3000");
    private static final BigDecimal DEFAULT_MINIMUM = new BigDecimal("15000");
    private static final BigDecimal DEFAULT_HEAVY_THRESHOLD = new BigDecimal("5.00");
    private static final BigDecimal DEFAULT_HEAVY_BONUS = new BigDecimal("2000");

    private final DriverEarningRepository earnings;
    private final EarningPolicyRepository policies;
    private final DriverSettlementRepository settlements;
    private final DriverRepository drivers;
    private final OutboxEventRepository outboxEvents;

    @Transactional
    public DriverEarning recordCompletedDelivery(DeliveryCompletedEvent event) {
        var existing = earnings.findByOrderId(event.orderId());
        if (existing.isPresent())
            return existing.get();

        Driver driver = drivers.findByUserId(event.assignedDriverId())
                .filter(item -> item.getTenantId().equals(event.tenantId()))
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        EarningPolicy policy = policyFor(event.tenantId());
        BigDecimal distance = distanceKm(event.pickupLat(), event.pickupLng(), event.deliveryLat(),
                event.deliveryLng());
        BigDecimal distanceAmount = policy.getPerKmAmount().multiply(distance);
        BigDecimal weight = BigDecimal.valueOf(event.weightKg() == null ? 0 : event.weightKg());
        BigDecimal bonus = weight.compareTo(policy.getHeavyWeightThresholdKg()) > 0
                ? policy.getHeavyBonusAmount()
                : BigDecimal.ZERO;
        BigDecimal calculated = policy.getBaseAmount().add(distanceAmount).add(bonus);
        BigDecimal total = roundToThousand(calculated.max(policy.getMinimumAmount()));

        DriverEarning earning = DriverEarning.builder()
                .tenantId(event.tenantId())
                .orderId(event.orderId())
                .deliveryTaskId(event.deliveryTaskId())
                .driverId(driver.getId())
                .earningType("DELIVERY")
                .deliveryFee(event.deliveryFee() == null ? BigDecimal.ZERO : event.deliveryFee())
                .payableDistanceKm(distance)
                .baseAmount(policy.getBaseAmount())
                .distanceAmount(distanceAmount.setScale(0, RoundingMode.HALF_UP))
                .bonusAmount(bonus)
                .netAmount(total)
                .calculationSnapshot(snapshot(policy, distance, weight))
                .completedAt(event.completedAt() == null ? Instant.now() : event.completedAt())
                .build();
        return earnings.save(earning);
    }

    @Transactional(readOnly = true)
    public List<DriverEarningResponse> myEarnings(UUID userId, Instant from, Instant to) {
        Driver driver = driverByUser(userId);
        Period period = period(from, to);
        return earnings
                .findByDriverIdAndCompletedAtBetweenOrderByCompletedAtDesc(driver.getId(), period.from(), period.to())
                .stream().map(item -> DriverEarningResponse.from(item, driver.getFullName())).toList();
    }

    @Transactional
    public EarningSummaryResponse mySummary(UUID userId, Instant from, Instant to) {
        Driver driver = driverByUser(userId);
        Period period = period(from, to);
        List<DriverEarning> rows = earnings.findByDriverIdAndCompletedAtBetweenOrderByCompletedAtDesc(
                driver.getId(), period.from(), period.to());
        BigDecimal pending = sum(rows, EarningStatus.PENDING);
        BigDecimal approved = sum(rows, EarningStatus.APPROVED);
        BigDecimal paid = sum(rows, EarningStatus.PAID);
        return new EarningSummaryResponse(period.from(), period.to(), pending, approved, paid,
                pending.add(approved).add(paid), rows.size(), policyFor(driver.getTenantId()).getCurrency());
    }

    @Transactional(readOnly = true)
    public List<DriverEarningResponse> tenantEarnings(UUID tenantId, Instant from, Instant to) {
        Period period = period(from, to);
        return earnings.findByTenantIdAndCompletedAtBetweenOrderByCompletedAtDesc(tenantId, period.from(), period.to())
                .stream().map(item -> DriverEarningResponse.from(item, driverName(item.getDriverId()))).toList();
    }

    @Transactional
    public DriverSettlementResponse createSettlement(UUID tenantId, CreateSettlementRequest request) {
        if (!request.periodEnd().isAfter(request.periodStart()))
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        Driver driver = drivers.findById(request.driverId())
                .filter(item -> item.getTenantId().equals(tenantId))
                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
        List<DriverEarning> pending = earnings
                .findByTenantIdAndDriverIdAndStatusAndSettlementIdIsNullAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        tenantId, driver.getId(), EarningStatus.PENDING, request.periodStart(), request.periodEnd());
        if (pending.isEmpty())
            throw new AppException(ErrorCode.SETTLEMENT_EMPTY);
        BigDecimal total = pending.stream().map(DriverEarning::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        DriverSettlement settlement = settlements.save(DriverSettlement.builder()
                .tenantId(tenantId).driverId(driver.getId()).periodStart(request.periodStart())
                .periodEnd(request.periodEnd())
                .earningCount(pending.size()).totalAmount(total).build());
        pending.forEach(item -> item.setSettlementId(settlement.getId()));
        earnings.saveAll(pending);
        return DriverSettlementResponse.from(settlement, driver.getFullName());
    }

    @Transactional
    public DriverSettlementResponse approveSettlement(UUID tenantId, UUID settlementId, UUID actorId) {
        DriverSettlement settlement = settlement(tenantId, settlementId);
        if (settlement.getStatus() != SettlementStatus.DRAFT)
            throw new AppException(ErrorCode.INVALID_SETTLEMENT_STATUS);
        settlement.setStatus(SettlementStatus.APPROVED);
        settlement.setApprovedBy(actorId);
        settlement.setApprovedAt(Instant.now());
        settlement.setUpdatedAt(Instant.now());
        List<DriverEarning> rows = earnings.findBySettlementIdOrderByCompletedAtAsc(settlementId);
        rows.forEach(item -> {
            item.setStatus(EarningStatus.APPROVED);
            item.setUpdatedAt(Instant.now());
        });
        earnings.saveAll(rows);
        DriverSettlement saved = settlements.save(settlement);
        emitSettlementEvent(saved, "settlement.approved");
        return DriverSettlementResponse.from(saved, driverName(settlement.getDriverId()));
    }

    @Transactional
    public DriverSettlementResponse markPaid(UUID tenantId, UUID settlementId, UUID actorId, String reference) {
        DriverSettlement settlement = settlement(tenantId, settlementId);
        if (settlement.getStatus() != SettlementStatus.APPROVED)
            throw new AppException(ErrorCode.INVALID_SETTLEMENT_STATUS);
        settlement.setStatus(SettlementStatus.PAID);
        settlement.setPaidBy(actorId);
        settlement.setPaidAt(Instant.now());
        settlement.setPaymentReference(reference.trim());
        settlement.setUpdatedAt(Instant.now());
        List<DriverEarning> rows = earnings.findBySettlementIdOrderByCompletedAtAsc(settlementId);
        rows.forEach(item -> {
            item.setStatus(EarningStatus.PAID);
            item.setUpdatedAt(Instant.now());
        });
        earnings.saveAll(rows);
        DriverSettlement saved = settlements.save(settlement);
        emitSettlementEvent(saved, "settlement.paid");
        return DriverSettlementResponse.from(saved, driverName(settlement.getDriverId()));
    }

    private void emitSettlementEvent(DriverSettlement settlement, String eventType) {
        if (outboxEvents == null)
            return;
        drivers.findById(settlement.getDriverId()).ifPresent(driver -> {
            UUID targetDriverUserId = driver.getUserId() != null ? driver.getUserId() : driver.getId();
            outboxEvents.save(OutboxEvent.builder()
                    .eventId(UUID.randomUUID())
                    .aggregateType("SETTLEMENT")
                    .aggregateId(settlement.getId())
                    .eventType(eventType)
                    .payload(java.util.Map.of(
                            "tenant_id", settlement.getTenantId().toString(),
                            "driver_id", targetDriverUserId.toString(),
                            "driver_entity_id", driver.getId().toString(),
                            "settlement_id", settlement.getId().toString(),
                            "total_amount", settlement.getTotalAmount(),
                            "earning_count", settlement.getEarningCount(),
                            "payment_reference",
                            settlement.getPaymentReference() != null ? settlement.getPaymentReference() : ""))
                    .build());
        });
    }

    @Transactional(readOnly = true)
    public List<DriverSettlementResponse> settlements(UUID tenantId) {
        return settlements.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(item -> DriverSettlementResponse.from(item, driverName(item.getDriverId()))).toList();
    }

    @Transactional
    public EarningPolicyResponse updatePolicy(UUID tenantId, UpdateEarningPolicyRequest request) {
        EarningPolicy policy = policyFor(tenantId);
        policy.setBaseAmount(request.baseAmount());
        policy.setPerKmAmount(request.perKmAmount());
        policy.setMinimumAmount(request.minimumAmount());
        policy.setHeavyWeightThresholdKg(request.heavyWeightThresholdKg());
        policy.setHeavyBonusAmount(request.heavyBonusAmount());
        policy.setUpdatedAt(Instant.now());
        return EarningPolicyResponse.from(policies.save(policy));
    }

    @Transactional
    public EarningPolicyResponse policy(UUID tenantId) {
        return EarningPolicyResponse.from(policyFor(tenantId));
    }

    private EarningPolicy policyFor(UUID tenantId) {
        return policies.findById(tenantId).orElseGet(() -> policies.save(EarningPolicy.builder()
                .tenantId(tenantId).baseAmount(DEFAULT_BASE).perKmAmount(DEFAULT_PER_KM)
                .minimumAmount(DEFAULT_MINIMUM).heavyWeightThresholdKg(DEFAULT_HEAVY_THRESHOLD)
                .heavyBonusAmount(DEFAULT_HEAVY_BONUS).build()));
    }

    private Driver driverByUser(UUID userId) {
        return drivers.findByUserId(userId).orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
    }

    private DriverSettlement settlement(UUID tenantId, UUID id) {
        return settlements.findById(id).filter(item -> item.getTenantId().equals(tenantId))
                .orElseThrow(() -> new AppException(ErrorCode.SETTLEMENT_NOT_FOUND));
    }

    private String driverName(UUID driverId) {
        return drivers.findById(driverId).map(Driver::getFullName).orElse("Tài xế");
    }

    private BigDecimal sum(List<DriverEarning> rows, EarningStatus status) {
        return rows.stream().filter(item -> item.getStatus() == status).map(DriverEarning::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Period period(Instant from, Instant to) {
        if (from != null && to != null && to.isAfter(from))
            return new Period(from, to);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant start = today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = today.with(TemporalAdjusters.firstDayOfNextMonth()).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new Period(start, end);
    }

    private BigDecimal distanceKm(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null)
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        double earthRadiusKm = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return BigDecimal.valueOf(earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal roundToThousand(BigDecimal amount) {
        return amount.divide(new BigDecimal("1000"), 0, RoundingMode.HALF_UP).multiply(new BigDecimal("1000"));
    }

    private String snapshot(EarningPolicy policy, BigDecimal distance, BigDecimal weight) {
        return "{" +
                "\"version\":1," +
                "\"distance_source\":\"GEODESIC_PICKUP_TO_DELIVERY\"," +
                "\"base_amount\":" + policy.getBaseAmount().toPlainString() + "," +
                "\"per_km_amount\":" + policy.getPerKmAmount().toPlainString() + "," +
                "\"minimum_amount\":" + policy.getMinimumAmount().toPlainString() + "," +
                "\"payable_distance_km\":" + distance.toPlainString() + "," +
                "\"weight_kg\":" + weight.toPlainString() + "," +
                "\"heavy_weight_threshold_kg\":" + policy.getHeavyWeightThresholdKg().toPlainString() + "," +
                "\"heavy_bonus_amount\":" + policy.getHeavyBonusAmount().toPlainString() + "," +
                "\"currency\":\"" + policy.getCurrency() + "\"}";
    }

    private record Period(Instant from, Instant to) {
    }
}
