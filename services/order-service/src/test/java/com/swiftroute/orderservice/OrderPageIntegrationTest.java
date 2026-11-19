package com.swiftroute.orderservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.swiftroute.orderservice.service.OrderService;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@EnabledIfEnvironmentVariable(named = "RUN_ORDER_PAGE_INTEGRATION_TESTS", matches = "true")
class OrderPageIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OrderService orders;

    @Test
    void pagesAndSearchesOnlyOrdersFromTheRequestedTenant() {
        UUID tenantId = jdbc.queryForObject(
                "select tenant_id from orders order by created_at desc, order_id desc limit 1", UUID.class);
        UUID latestId = jdbc.queryForObject(
                "select order_id from orders where tenant_id = ? order by created_at desc, order_id desc limit 1",
                UUID.class, tenantId);
        long total = jdbc.queryForObject("select count(*) from orders where tenant_id = ?", Long.class, tenantId);

        var first = orders.getOrderPage(tenantId, null, null, null, null, 0, 1);
        assertThat(first.orders().content()).singleElement().satisfies(order -> {
            assertThat(order.orderId()).isEqualTo(latestId);
            assertThat(order.tenantId()).isEqualTo(tenantId);
        });
        assertThat(first.orders().totalElements()).isEqualTo(total);
        assertThat(first.statusCounts().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(total);

        var search = orders.getOrderPage(tenantId, null, null, "#" + latestId, null, 0, 15);
        assertThat(search.orders().content()).singleElement()
                .satisfies(order -> assertThat(order.orderId()).isEqualTo(latestId));
        assertThat(search.orders().totalElements()).isEqualTo(1);
        assertThat(orders.getOrderPage(tenantId, null, null, "%_", null, 0, 15).orders().content())
                .allSatisfy(order -> assertThat(order.tenantId()).isEqualTo(tenantId));
    }
}
