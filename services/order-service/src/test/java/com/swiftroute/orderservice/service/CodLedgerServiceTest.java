package com.swiftroute.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swiftroute.orderservice.dto.request.VerifyCodHandoverRequest;
import com.swiftroute.orderservice.entity.CodHandoverBatch;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.CodHandoverBatchRepository;
import com.swiftroute.orderservice.repository.OrderRepository;
import com.swiftroute.orderservice.repository.OutboxEventRepository;

class CodLedgerServiceTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final CodHandoverBatchRepository handovers = mock(CodHandoverBatchRepository.class);
    private final OutboxEventRepository outboxEvents = mock(OutboxEventRepository.class);
    private final CodLedgerService service = new CodLedgerService(orders, handovers, outboxEvents);

    private UUID tenantId;
    private UUID cashierId;
    private CodHandoverBatch batch;
    private Order order;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        cashierId = UUID.randomUUID();
        batch = CodHandoverBatch.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .driverId(UUID.randomUUID())
                .operationId(UUID.randomUUID())
                .status("SUBMITTED")
                .ledgerTotal(new BigDecimal("500000"))
                .declaredTotal(new BigDecimal("500000"))
                .build();
        order = Order.builder()
                .id(UUID.randomUUID())
                .externalOrderCode("ORD-COD-001")
                .codAmount(new BigDecimal("500000"))
                .codStatus("IN_CUSTODY")
                .codHandoverBatchId(batch.getId())
                .build();

        when(handovers.findByIdAndTenantId(batch.getId(), tenantId)).thenReturn(Optional.of(batch));
        when(orders.findByCodHandoverBatchIdOrderByUpdatedAtDesc(batch.getId())).thenReturn(List.of(order));
    }

    @Test
    void matchingPhysicalCountSettlesReceiptAndMarksOrdersHandedOver() {
        UUID operationId = UUID.randomUUID();
        when(handovers.findByTenantIdAndVerificationOperationId(tenantId, operationId))
                .thenReturn(Optional.empty());

        var response = service.verifyHandover(
                batch.getId(),
                tenantId,
                cashierId,
                "CASHIER",
                new VerifyCodHandoverRequest(operationId, new BigDecimal("500000"), null, null));

        assertThat(response.status()).isEqualTo("SETTLED");
        assertThat(response.discrepancyAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.varianceResolution()).isNull();
        assertThat(order.getCodStatus()).isEqualTo("HANDED_OVER");
        verify(handovers).save(batch);
        verify(orders).save(order);
        verify(outboxEvents).save(any());
    }

    @Test
    void mismatchIsSettledImmediatelyWithExplicitBusinessDecision() {
        UUID operationId = UUID.randomUUID();
        when(handovers.findByTenantIdAndVerificationOperationId(tenantId, operationId))
                .thenReturn(Optional.empty());

        var response = service.verifyHandover(
                batch.getId(),
                tenantId,
                cashierId,
                "MANAGER",
                new VerifyCodHandoverRequest(
                        operationId,
                        new BigDecimal("490000"),
                        "BUSINESS_ADJUSTMENT",
                        "Quản lý xác nhận bù 10.000đ"));

        assertThat(response.status()).isEqualTo("SETTLED_WITH_VARIANCE");
        assertThat(response.discrepancyAmount()).isEqualByComparingTo(new BigDecimal("-10000"));
        assertThat(response.varianceResolution()).isEqualTo("BUSINESS_ADJUSTMENT");
        assertThat(response.notes()).isEqualTo("Quản lý xác nhận bù 10.000đ");
        assertThat(order.getCodStatus()).isEqualTo("HANDED_OVER");
    }

    @Test
    void mismatchWithoutResolutionAndNotesIsRejected() {
        UUID operationId = UUID.randomUUID();
        when(handovers.findByTenantIdAndVerificationOperationId(tenantId, operationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyHandover(
                batch.getId(),
                tenantId,
                cashierId,
                "CASHIER",
                new VerifyCodHandoverRequest(operationId, new BigDecimal("490000"), null, null)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.COD_REASON_REQUIRED);
    }
}
