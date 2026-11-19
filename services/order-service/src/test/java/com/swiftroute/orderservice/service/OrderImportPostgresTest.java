package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftroute.orderservice.dto.response.OrderResponse;
import com.swiftroute.orderservice.service.OrderImportService.Address;
import com.swiftroute.orderservice.service.OrderImportService.Row;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "ORDER_IMPORT_TEST_URL", matches = ".+")
class OrderImportPostgresTest {
    @Test
    void storesUnresolvedRowsOnceAndCompletesThemOnceUnderConcurrentRetries() throws Exception {
        var dataSource = new DriverManagerDataSource(System.getenv("ORDER_IMPORT_TEST_URL"),
                System.getenv("ORDER_IMPORT_TEST_USER"), System.getenv("ORDER_IMPORT_TEST_PASSWORD"));
        var jdbc = new JdbcTemplate(dataSource);
        var orders = mock(OrderService.class);
        var response = mock(OrderResponse.class);
        UUID orderId = jdbc.queryForObject("SELECT order_id FROM orders LIMIT 1", UUID.class);
        UUID tenant = jdbc.queryForObject("SELECT tenant_id FROM orders WHERE order_id = ?", UUID.class, orderId);
        UUID user = UUID.randomUUID();
        when(orders.createOrder(any(), eq(user), eq(tenant), eq("OWNER"))).thenReturn(response);
        when(response.orderId()).thenReturn(orderId);
        String code = "CSV-TEST-" + UUID.randomUUID();
        String directCode = code + "-direct";
        var start = OffsetDateTime.parse("2026-11-16T08:00:00+07:00");
        var pending = new Row(2, code, "Nguyễn Văn A", "0900000000", "225 đường 3/2", null, null,
                start, start.plusHours(2), 1.5, BigDecimal.ZERO, "");
        var invalid = new Row(3, code + "-invalid", "", "0900000000", null, null, null,
                start, start.plusHours(2), 1.5, BigDecimal.ZERO, "");
        var direct = new Row(4, directCode, "Nguyễn Văn A", "0900000000", "225 đường 3/2", 10.77, 106.67,
                start, start.plusHours(2), 1.5, BigDecimal.ZERO, "");
        try (var validator = Validation.buildDefaultValidatorFactory();
             var pool = Executors.newFixedThreadPool(2)) {
            var service = new OrderImportService(jdbc, validator.getValidator(), orders,
                    new DataSourceTransactionManager(dataSource));
            assertThat(service.submit(List.of(pending, invalid, direct), tenant, user, "OWNER"))
                    .extracting(OrderImportService.Result::status).containsExactly("NEEDS_ADDRESS", "INVALID", "CREATED");
            assertThat(service.submit(List.of(pending, direct), tenant, user, "OWNER"))
                    .extracting(OrderImportService.Result::status).containsExactly("DUPLICATE", "DUPLICATE");
            assertThat(service.pending(tenant, code.substring(0, code.length() - 1)))
                    .anyMatch(row -> row.externalOrderCode().equals(code));
            assertThat(service.pending(UUID.randomUUID(), "")).isEmpty();
            assertThat(service.cancel(UUID.randomUUID(), code)).isFalse();
            assertThat(service.cancel(tenant, code)).isTrue();
            assertThat(service.cancel(tenant, code)).isFalse();
            assertThat(service.submit(List.of(pending), tenant, user, "OWNER"))
                    .extracting(OrderImportService.Result::status).containsExactly("NEEDS_ADDRESS");

            var corrected = new Address("225 đường 3/2, TP. Hồ Chí Minh", 10.77, 106.67);
            var gate = new CountDownLatch(1);
            var complete = (java.util.concurrent.Callable<UUID>) () -> {
                gate.await();
                return service.complete(tenant, user, "OWNER", code, corrected);
            };
            var first = pool.submit(complete);
            var second = pool.submit(complete);
            gate.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(orderId);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(orderId);
            assertThat(service.cancel(tenant, code)).isFalse();
            verify(orders, times(2)).createOrder(any(), eq(user), eq(tenant), eq("OWNER"));
            assertThat(jdbc.queryForObject(
                    "SELECT recipient_phone FROM order_import_rows WHERE tenant_id = ? AND source_code = ?",
                    String.class, tenant, code)).isNull();
        } finally {
            jdbc.update("DELETE FROM order_import_rows WHERE tenant_id = ? AND source_code IN (?, ?)",
                    tenant, code, directCode);
        }
    }
}
