package com.swiftroute.orderservice.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;

import jakarta.validation.Validator;

@Service
public class OrderImportService {
    private static final String PENDING = "NEEDS_ADDRESS";
    private static final String CREATED = "CREATED";
    private static final String DUPLICATE = "DUPLICATE";

    public record Row(int line, String externalOrderCode, String recipientName, String recipientPhone,
            String deliveryAddress, Double deliveryLat, Double deliveryLng,
            OffsetDateTime timeWindowStart, OffsetDateTime timeWindowEnd, Double weightKg,
            BigDecimal codAmount, String notes) {
        CreateOrderRequest withAddress(String address, Double lat, Double lng) {
            return new CreateOrderRequest(externalOrderCode.trim(), null, null, recipientName, recipientPhone,
                    null, null, null, null, lat, lng, address, timeWindowStart, timeWindowEnd,
                    weightKg, null, null, notes, null, codAmount, null, null, null);
        }
    }

    public record Result(int line, String externalOrderCode, String status, String errorCode, String message) {
    }

    public record Pending(String externalOrderCode, String recipientName, String deliveryAddress) {
    }

    public record Address(String deliveryAddress, Double deliveryLat, Double deliveryLng) {
    }

    private final JdbcTemplate jdbc;
    private final Validator validator;
    private final OrderService orders;
    private final TransactionTemplate transactions;

    public OrderImportService(JdbcTemplate jdbc, Validator validator, OrderService orders,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.validator = validator;
        this.orders = orders;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<Result> submit(List<Row> rows, UUID tenant, UUID user, String role) {
        return rows.stream().map(row -> {
            try {
                String status = transactions.execute(transaction -> importRow(row, tenant, user, role));
                return new Result(row.line(), row.externalOrderCode(), status, null, null);
            } catch (AppException error) {
                return new Result(row.line(), row.externalOrderCode(), "INVALID",
                        error.getErrorCode().name(), error.getMessage());
            }
        }).toList();
    }

    private String importRow(Row row, UUID tenant, UUID user, String role) {
        if (row.externalOrderCode() == null || row.externalOrderCode().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "externalOrderCode: bắt buộc");
        }
        boolean pending = row.deliveryAddress() == null || row.deliveryAddress().isBlank()
                || !validCoordinate(row.deliveryLat(), 90) || !validCoordinate(row.deliveryLng(), 180);
        if (row.deliveryAddress() != null && row.deliveryAddress().length() > 500) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "deliveryAddress: tối đa 500 ký tự");
        }
        var request = row.withAddress(row.deliveryAddress(), row.deliveryLat(), row.deliveryLng());
        validate(request, pending);
        if (pending) {
            int inserted = jdbc.update("""
                    INSERT INTO order_import_rows (tenant_id, source_code, recipient_name, recipient_phone,
                        original_address, window_start, window_end, weight_kg, cod_amount, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, tenant, request.externalOrderCode(), row.recipientName(), row.recipientPhone(),
                    row.deliveryAddress(), row.timeWindowStart(), row.timeWindowEnd(), row.weightKg(),
                    request.codAmount(), row.notes());
            if (inserted == 0)
                return DUPLICATE;
            if (!orderExists(tenant, request.externalOrderCode()))
                return PENDING;
            jdbc.update("DELETE FROM order_import_rows WHERE tenant_id = ? AND source_code = ?",
                    tenant, request.externalOrderCode());
            return DUPLICATE;
        }
        int reserved = jdbc.update("""
                INSERT INTO order_import_rows (tenant_id, source_code) VALUES (?, ?) ON CONFLICT DO NOTHING
                """, tenant, request.externalOrderCode());
        if (reserved == 0)
            return DUPLICATE;
        if (orderExists(tenant, request.externalOrderCode())) {
            jdbc.update("DELETE FROM order_import_rows WHERE tenant_id = ? AND source_code = ?",
                    tenant, request.externalOrderCode());
            return DUPLICATE;
        }
        markCreated(tenant, request, user, role);
        return CREATED;
    }

    public List<Pending> pending(UUID tenant, String after) {
        return jdbc.query("""
                SELECT source_code, recipient_name, original_address FROM order_import_rows
                WHERE tenant_id = ? AND order_id IS NULL AND source_code > ? ORDER BY source_code LIMIT 51
                """, (rs, n) -> new Pending(rs.getString(1), rs.getString(2), rs.getString(3)), tenant, after);
    }

    public UUID complete(UUID tenant, UUID user, String role, String code, Address address) {
        return transactions.execute(transaction -> {
            var rows = jdbc.query("""
                    SELECT * FROM order_import_rows WHERE tenant_id = ? AND source_code = ? FOR UPDATE
                    """, (rs, n) -> new Row(0, rs.getString("source_code"), rs.getString("recipient_name"),
                    rs.getString("recipient_phone"), null, null, null,
                    rs.getObject("window_start", OffsetDateTime.class),
                    rs.getObject("window_end", OffsetDateTime.class), rs.getObject("weight_kg", Double.class),
                    rs.getBigDecimal("cod_amount"), rs.getString("notes")), tenant, code);
            if (rows.isEmpty())
                throw new AppException(ErrorCode.ORDER_NOT_FOUND);
            UUID existing = jdbc.queryForObject("""
                    SELECT order_id FROM order_import_rows WHERE tenant_id = ? AND source_code = ?
                    """, UUID.class, tenant, code);
            if (existing != null)
                return existing;
            var request = rows.getFirst().withAddress(address.deliveryAddress(), address.deliveryLat(),
                    address.deliveryLng());
            validate(request, false);
            if (orderExists(tenant, code)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Mã đơn nguồn đã có vận đơn");
            }
            return markCreated(tenant, request, user, role);
        });
    }

    private boolean orderExists(UUID tenant, String code) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM orders WHERE tenant_id = ? AND external_order_code = ?)
                """, Boolean.class, tenant, code));
    }

    public boolean cancel(UUID tenant, String code) {
        return jdbc.update("""
                DELETE FROM order_import_rows WHERE tenant_id = ? AND source_code = ? AND order_id IS NULL
                """, tenant, code) == 1;
    }

    private UUID markCreated(UUID tenant, CreateOrderRequest request, UUID user, String role) {
        UUID orderId = orders.createOrder(request, user, tenant, role).orderId();
        jdbc.update("""
                UPDATE order_import_rows SET order_id = ?, recipient_name = NULL, recipient_phone = NULL,
                    original_address = NULL, window_start = NULL, window_end = NULL, weight_kg = NULL,
                    cod_amount = NULL, notes = NULL WHERE tenant_id = ? AND source_code = ?
                """, orderId, tenant, request.externalOrderCode());
        return orderId;
    }

    private static boolean validCoordinate(Double value, int limit) {
        return value != null && Double.isFinite(value) && Math.abs(value) <= limit;
    }

    private void validate(CreateOrderRequest request, boolean allowMissingPoint) {
        validator.validate(request).stream()
                .filter(v -> !allowMissingPoint || switch (v.getPropertyPath().toString()) {
                    case "deliveryAddress", "deliveryLat", "deliveryLng" -> false;
                    default -> true;
                })
                .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().findFirst()
                .ifPresent(message -> { throw new AppException(ErrorCode.VALIDATION_ERROR, message); });
        if (!Double.isFinite(request.weightKg())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "weightKg: không hợp lệ");
        }
        if (request.codAmount().stripTrailingZeros().scale() > 0
                || request.codAmount().compareTo(new BigDecimal("1000000000000")) >= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "codAmount: yêu cầu số nguyên tối đa 12 chữ số");
        }
        if (!request.timeWindowEnd().isAfter(request.timeWindowStart())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "timeWindowEnd: phải sau giờ bắt đầu");
        }
    }
}
