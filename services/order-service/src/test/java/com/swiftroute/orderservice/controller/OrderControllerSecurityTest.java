package com.swiftroute.orderservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.swiftroute.orderservice.dto.request.CancelOrderRequest;
import com.swiftroute.orderservice.dto.request.CreateOrderRequest;
import com.swiftroute.orderservice.dto.request.ReportIncidentRequest;
import com.swiftroute.orderservice.dto.response.OrderResponse;
import com.swiftroute.orderservice.entity.Order;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.OrderPriority;
import com.swiftroute.orderservice.entity.enums.OrderStatus;
import com.swiftroute.orderservice.entity.enums.PackageCategory;
import com.swiftroute.orderservice.exception.GlobalExceptionHandler;
import com.swiftroute.orderservice.service.OrderService;
import com.swiftroute.orderservice.service.TrackingSimulationService;
import com.swiftroute.orderservice.service.planning.DispatchOrchestrationService;

@ExtendWith(MockitoExtension.class)
class OrderControllerSecurityTest {

    @Mock private OrderService orderService;
    @Mock private TrackingSimulationService trackingSimulationService;
    @Mock private DispatchOrchestrationService orchestration;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID tenantId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        OrderController controller = new OrderController(orderService, trackingSimulationService, orchestration);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    private CreateOrderRequest createSampleRequest() {
        return new CreateOrderRequest(
                "EXT-001",
                "Sender Name", "0901234567",
                "Receiver Name", "0907654321",
                null, 10.77, 106.70, "123 Pickup St",
                10.7769, 106.7009, "456 Delivery Ave",
                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusHours(2),
                1.5, PackageCategory.PARCEL, "Electronics", "Handle with care",
                OrderPriority.NORMAL, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(25000), "SENDER");
    }

    private OrderResponse createMockOrderResponse(UUID id, OrderStatus status) {
        Tenant tenant = Tenant.builder().id(tenantId).businessName("Test Tenant").build();
        Order order = Order.builder()
                .id(id)
                .tenant(tenant)
                .externalOrderCode("ORD-123")
                .recipientName("Receiver")
                .recipientPhone("0907654321")
                .deliveryAddress("456 Delivery")
                .deliveryLat(10.77)
                .deliveryLng(106.70)
                .timeWindowStart(OffsetDateTime.now().plusHours(1))
                .timeWindowEnd(OffsetDateTime.now().plusHours(2))
                .weightKg(1.0)
                .status(status)
                .codAmount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.TEN)
                .trackingToken("TOKEN-123")
                .build();
        return OrderResponse.fromEntity(order);
    }

    @Test
    @DisplayName("POST /api/orders rejects DRIVER role with 403 FORBIDDEN")
    void createOrder_whenRoleIsDriver_returnsForbidden() throws Exception {
        CreateOrderRequest request = createSampleRequest();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("authorizedUserId", userId)
                        .requestAttr("authorizedTenantId", tenantId)
                        .requestAttr("effectiveRole", "DRIVER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(403));

        verify(orderService, never()).createOrder(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PATCH /api/orders/{id}/cancel rejects DRIVER role with 403 FORBIDDEN")
    void cancelOrder_whenRoleIsDriver_returnsForbidden() throws Exception {
        CancelOrderRequest request = new CancelOrderRequest("Driver wants to cancel");

        mockMvc.perform(patch("/api/orders/{orderId}/cancel", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("authorizedUserId", userId)
                        .requestAttr("effectiveRole", "DRIVER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(403));

        verify(orderService, never()).cancelOrder(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PATCH /api/orders/{id}/incident invokes orderService.reportIncident")
    void reportIncident_patchIncident_invokesService() throws Exception {
        ReportIncidentRequest request = new ReportIncidentRequest("ACCIDENT", "Vehicle damaged");
        OrderResponse mockResponse = createMockOrderResponse(orderId, OrderStatus.FAILED);

        when(orderService.reportIncident(eq(orderId), any(ReportIncidentRequest.class), eq(userId), eq("DRIVER")))
                .thenReturn(mockResponse);

        mockMvc.perform(patch("/api/orders/{orderId}/incident", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("authorizedUserId", userId)
                        .requestAttr("effectiveRole", "DRIVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order_id").value(orderId.toString()))
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        verify(orderService).reportIncident(eq(orderId), any(ReportIncidentRequest.class), eq(userId), eq("DRIVER"));
    }

    @Test
    @DisplayName("POST /api/orders/{id}/incidents invokes orderService.reportIncident")
    void reportIncident_postIncidents_invokesService() throws Exception {
        ReportIncidentRequest request = new ReportIncidentRequest("WEATHER_DELAY", "Stormy rain");
        OrderResponse mockResponse = createMockOrderResponse(orderId, OrderStatus.FAILED);

        when(orderService.reportIncident(eq(orderId), any(ReportIncidentRequest.class), eq(userId), eq("OPERATOR")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/orders/{orderId}/incidents", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("authorizedUserId", userId)
                        .requestAttr("effectiveRole", "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order_id").value(orderId.toString()));

        verify(orderService).reportIncident(eq(orderId), any(ReportIncidentRequest.class), eq(userId), eq("OPERATOR"));
    }

    @Test
    @DisplayName("POST /api/orders/{id}/incident invokes orderService.reportIncident")
    void reportIncident_postIncident_invokesService() throws Exception {
        ReportIncidentRequest request = new ReportIncidentRequest("PACKAGE_DAMAGED", "Broken glass");
        OrderResponse mockResponse = createMockOrderResponse(orderId, OrderStatus.FAILED);

        when(orderService.reportIncident(eq(orderId), any(ReportIncidentRequest.class), eq(userId), eq("OWNER")))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/orders/{orderId}/incident", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("authorizedUserId", userId)
                        .requestAttr("effectiveRole", "OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order_id").value(orderId.toString()));

        verify(orderService).reportIncident(eq(orderId), any(ReportIncidentRequest.class), eq(userId), eq("OWNER"));
    }
}
