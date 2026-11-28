package com.swiftroute.orderservice.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.swiftroute.orderservice.entity.OrderTrackingLink;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OrderTrackingLinkRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderTrackingLinkService {

    private final OrderTrackingLinkRepository orderTrackingLinkRepository;
    private final OrderRepository orderRepository;
    private final TrackingTokenService tokenService;

    @Value("${tracking.base-url}")
    private String trackingBaseUrl;

    @Value("${tracking.expiration-days}")
    private long expirationDays;

    @Transactional
    public OrderTrackingLink getOrCreateLink(
            UUID tenantId,
            UUID orderId) {
        orderRepository
                .findByIdAndTenantIdForUpdate(orderId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        return orderTrackingLinkRepository.findByOrderId(orderId)
                .map(link -> {
                    if (!isActive(link)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Tracking link is no longer active");
                    }

                    return link;
                })
                .orElseGet(() -> createLink(orderId));
    }

    private OrderTrackingLink createLink(UUID orderId) {
        OffsetDateTime now = OffsetDateTime.now();

        OrderTrackingLink link = OrderTrackingLink.builder()
                .orderId(orderId)
                .createdAt(now)
                .expiresAt(now.plusDays(expirationDays))
                .build();

        return orderTrackingLinkRepository.save(link);
    }

    public String generateTrackingUrl(OrderTrackingLink link) {
        String token = tokenService.generateToken(
                link.getTrackingId());

        return trackingBaseUrl + "/" + token;
    }

    @Transactional(readOnly = true)
    public OrderTrackingLink validateToken(String token) {
        UUID trackingId = tokenService.validateToken(token);

        return orderTrackingLinkRepository.findById(trackingId)
                .filter(this::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Invalid tracking link"));
    }

    public boolean isActive(OrderTrackingLink link) {
        return link.getRevokedAt() == null
                && link.getExpiresAt().isAfter(
                        OffsetDateTime.now());
    }

    @Transactional
    public void revokeLink(UUID tenantId, UUID orderId) {
        orderRepository
                .findByIdAndTenantIdForUpdate(orderId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        orderTrackingLinkRepository.findByOrderId(orderId)
                .ifPresent(link -> {
                    if (link.getRevokedAt() == null) {
                        link.setRevokedAt(OffsetDateTime.now());
                    }
                });
    }
}
