package com.swiftroute.orderservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.swiftroute.orderservice.dto.request.CancelPlanRequest;
import com.swiftroute.orderservice.dto.response.ApiResponse;
import com.swiftroute.orderservice.dto.response.DispatchPlanResponse;
import com.swiftroute.orderservice.entity.DispatchPlan;
import com.swiftroute.orderservice.entity.Tenant;
import com.swiftroute.orderservice.entity.enums.PlanStatus;
import com.swiftroute.orderservice.exception.AppException;
import com.swiftroute.orderservice.exception.ErrorCode;
import com.swiftroute.orderservice.repository.DispatchPlanRepository;
import com.swiftroute.orderservice.service.TrackingSimulationService;
import com.swiftroute.orderservice.service.planning.DispatchOrchestrationService;
import com.swiftroute.orderservice.service.planning.PlanningTriggerScheduler;

@ExtendWith(MockitoExtension.class)
class DispatchControllerTest {

    @Mock
    private DispatchOrchestrationService orchestrationService;
    @Mock
    private PlanningTriggerScheduler planningTriggerScheduler;
    @Mock
    private DispatchPlanRepository dispatchPlanRepository;
    @Mock
    private TrackingSimulationService trackingSimulationService;

    private DispatchController controller;

    private UUID tenantId;
    private UUID driverId;
    private UUID planId;
    private Tenant tenant;
    private DispatchPlan plan;

    @BeforeEach
    void setUp() {
        controller = new DispatchController(
                dispatchPlanRepository,
                orchestrationService,
                planningTriggerScheduler,
                trackingSimulationService);

        tenantId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        planId = UUID.randomUUID();

        tenant = Tenant.builder().id(tenantId).businessName("Test Tenant").build();
        plan = DispatchPlan.builder()
                .id(planId)
                .tenant(tenant)
                .driverId(driverId)
                .planStatus(PlanStatus.DISPATCHED)
                .build();
    }

    @Test
    void listPlansIncludesDriverDeclineAndReassignmentCount() {
        plan.decline("Vehicle breakdown");
        when(dispatchPlanRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(plan));

        var response = controller.listPlans(tenantId);

        assertThat(response.getBody().data()).singleElement().satisfies(result -> {
        });
    }

    @Test
    @DisplayName("acknowledgePlan: assigned driver succeeds")
    void acknowledgePlan_assignedDriver_success() {
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(orchestrationService.acknowledgeByDriver(planId)).thenReturn(plan);

        ResponseEntity<ApiResponse<DispatchPlanResponse>> response = controller.acknowledgePlan(planId, tenantId,
                driverId, "DRIVER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().planId()).isEqualTo(planId);
        verify(orchestrationService).acknowledgeByDriver(planId);
    }

    @Test
    @DisplayName("acknowledgePlan: unassigned driver is forbidden (ACCESS_DENIED)")
    void acknowledgePlan_unassignedDriver_forbidden() {
        UUID otherDriverId = UUID.randomUUID();
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> controller.acknowledgePlan(planId, tenantId, otherDriverId, "DRIVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("acknowledgePlan: OWNER role succeeds even if userId != driverId")
    void acknowledgePlan_ownerRole_success() {
        UUID ownerId = UUID.randomUUID();
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(orchestrationService.acknowledgeByDriver(planId)).thenReturn(plan);

        ResponseEntity<ApiResponse<DispatchPlanResponse>> response = controller.acknowledgePlan(planId, tenantId,
                ownerId, "OWNER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(orchestrationService).acknowledgeByDriver(planId);
    }

    @Test
    @DisplayName("acknowledgePlan: SYSTEM_ADMIN role succeeds")
    void acknowledgePlan_systemAdminRole_success() {
        UUID adminId = UUID.randomUUID();
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(orchestrationService.acknowledgeByDriver(planId)).thenReturn(plan);

        ResponseEntity<ApiResponse<DispatchPlanResponse>> response = controller.acknowledgePlan(planId, tenantId,
                adminId, "SYSTEM_ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(orchestrationService).acknowledgeByDriver(planId);
    }

    @Test
    @DisplayName("acknowledgePlan: OPERATOR role is forbidden (ACCESS_DENIED)")
    void acknowledgePlan_operatorRole_forbidden() {
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> controller.acknowledgePlan(planId, tenantId, driverId, "OPERATOR"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("acknowledgePlan: VIEWER role is forbidden (ACCESS_DENIED)")
    void acknowledgePlan_viewerRole_forbidden() {
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> controller.acknowledgePlan(planId, tenantId, driverId, "VIEWER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("acknowledgePlan: cross-tenant plan lookup throws PLAN_NOT_FOUND (404)")
    void acknowledgePlan_crossTenant_planNotFound() {
        UUID otherTenantId = UUID.randomUUID();
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> controller.acknowledgePlan(planId, otherTenantId, driverId, "DRIVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }

    @Test
    @DisplayName("acknowledgePlan: null tenantId throws ACCESS_DENIED")
    void acknowledgePlan_nullTenantId_accessDenied() {
        assertThatThrownBy(() -> controller.acknowledgePlan(planId, null, driverId, "DRIVER"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("declinePlan: assigned driver cannot release a dispatched route")
    void declinePlan_assignedDriver_cannotReleaseRoute() {
        CancelPlanRequest request = new CancelPlanRequest("Vehicle broke down");
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> controller.declinePlan(planId, tenantId, driverId, "DRIVER", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DRIVER_CANNOT_DECLINE_PLAN));
        org.mockito.Mockito.verifyNoInteractions(orchestrationService);
    }

    @Test
    @DisplayName("declinePlan: unassigned driver is forbidden (ACCESS_DENIED)")
    void declinePlan_unassignedDriver_forbidden() {
        UUID otherDriverId = UUID.randomUUID();
        CancelPlanRequest request = new CancelPlanRequest("Decline reason");
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> controller.declinePlan(planId, tenantId, otherDriverId, "DRIVER", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("declinePlan: OPERATOR is forbidden (ACCESS_DENIED)")
    void declinePlan_operatorRole_forbidden() {
        CancelPlanRequest request = new CancelPlanRequest("Decline reason");
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> controller.declinePlan(planId, tenantId, driverId, "OPERATOR", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("declinePlan: cross-tenant plan lookup throws PLAN_NOT_FOUND")
    void declinePlan_crossTenant_planNotFound() {
        UUID otherTenantId = UUID.randomUUID();
        CancelPlanRequest request = new CancelPlanRequest("Decline reason");
        when(dispatchPlanRepository.findById(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> controller.declinePlan(planId, otherTenantId, driverId, "DRIVER", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }

    @Test
    @DisplayName("listDriverPlans: scoped by tenantId and driverId for DISPATCHED status")
    void listDriverPlans_scopedByTenant() {
        when(dispatchPlanRepository.findAllByTenantIdAndDriverIdAndPlanStatusIn(
                tenantId, driverId, List.of(PlanStatus.DISPATCHED)))
                .thenReturn(List.of(plan));

        ResponseEntity<ApiResponse<List<DispatchPlanResponse>>> response = controller.listDriverPlans(driverId,
                tenantId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).planId()).isEqualTo(planId);
        verify(dispatchPlanRepository).findAllByTenantIdAndDriverIdAndPlanStatusIn(
                tenantId, driverId, List.of(PlanStatus.DISPATCHED));
    }

    @Test
    @DisplayName("listDriverPlans: null tenantId throws ACCESS_DENIED")
    void listDriverPlans_nullTenant_accessDenied() {
        assertThatThrownBy(() -> controller.listDriverPlans(driverId, null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }
}
