package com.swiftroute.orderservice.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.PublicOrderTrackingResponse;
import com.swiftroute.orderservice.service.PublicOrderTrackingService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/**
 * Unauthenticated public endpoint for recipients tracking orders via tracking_token.
 */
@RestController
@RequestMapping("/api/public/tracking")
@RequiredArgsConstructor
public class PublicTrackingController {

    private final PublicOrderTrackingService trackingService;

    @Operation(summary = "Tra cứu công khai tiến trình đơn hàng theo mã tracking_token (đã ẩn danh PII)")
    @GetMapping("/{trackingToken}")
    public ResponseEntity<ApiResponse<PublicOrderTrackingResponse>> trackOrder(
            @PathVariable String trackingToken) {
        PublicOrderTrackingResponse payload = trackingService.getByTrackingToken(trackingToken);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(ApiResponse.success(payload));
    }
}
