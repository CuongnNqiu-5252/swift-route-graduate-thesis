package com.swiftroute.driverservice.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class SettlementAndEarningSecurityTest {

    @Test
    @DisplayName("SettlementController class has VIEWER in PreAuthorize")
    void settlementController_ClassLevelPreAuthorize_ContainsViewer() {
        PreAuthorize classAuth = SettlementController.class.getAnnotation(PreAuthorize.class);
        assertThat(classAuth).isNotNull();
        assertThat(classAuth.value()).contains("VIEWER");
    }

    @Test
    @DisplayName("SettlementController read endpoints allow VIEWER")
    void settlementController_ReadEndpoints_AllowViewer() throws NoSuchMethodException {
        Method pageMethod = SettlementController.class.getMethod("settlementPage", int.class, int.class, java.util.UUID.class);
        PreAuthorize pageAuth = pageMethod.getAnnotation(PreAuthorize.class);
        assertThat(pageAuth).isNotNull();
        assertThat(pageAuth.value()).contains("VIEWER");

        Method listMethod = SettlementController.class.getMethod("list", java.util.UUID.class);
        PreAuthorize listAuth = listMethod.getAnnotation(PreAuthorize.class);
        assertThat(listAuth).isNotNull();
        assertThat(listAuth.value()).contains("VIEWER");
    }

    @Test
    @DisplayName("SettlementController approve and markPaid restrict to SYSTEM_ADMIN, TENANT_ADMIN, OWNER (disallowing OPERATOR and VIEWER)")
    void settlementController_ApproveAndMarkPaid_DisallowOperatorAndViewer() throws NoSuchMethodException {
        Method approveMethod = SettlementController.class.getMethod(
                "approve", java.util.UUID.class, org.springframework.security.core.Authentication.class, java.util.UUID.class);
        PreAuthorize approveAuth = approveMethod.getAnnotation(PreAuthorize.class);
        assertThat(approveAuth).isNotNull();
        assertThat(approveAuth.value()).isEqualTo("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OWNER')");
        assertThat(approveAuth.value()).doesNotContain("OPERATOR");
        assertThat(approveAuth.value()).doesNotContain("VIEWER");

        Method markPaidMethod = SettlementController.class.getMethod(
                "markPaid", java.util.UUID.class, org.springframework.security.core.Authentication.class, java.util.UUID.class,
                com.swiftroute.driverservice.dto.request.MarkSettlementPaidRequest.class);
        PreAuthorize markPaidAuth = markPaidMethod.getAnnotation(PreAuthorize.class);
        assertThat(markPaidAuth).isNotNull();
        assertThat(markPaidAuth.value()).isEqualTo("hasAnyRole('SYSTEM_ADMIN','TENANT_ADMIN','OWNER')");
        assertThat(markPaidAuth.value()).doesNotContain("OPERATOR");
        assertThat(markPaidAuth.value()).doesNotContain("VIEWER");
    }

    @Test
    @DisplayName("EarningController read endpoints allow VIEWER")
    void earningController_ReadEndpoints_AllowViewer() throws NoSuchMethodException {
        Method tenantEarningsMethod = EarningController.class.getMethod(
                "tenantEarnings", java.util.UUID.class, java.time.Instant.class, java.time.Instant.class);
        PreAuthorize earningsAuth = tenantEarningsMethod.getAnnotation(PreAuthorize.class);
        assertThat(earningsAuth).isNotNull();
        assertThat(earningsAuth.value()).contains("VIEWER");

        Method tenantEarningPageMethod = EarningController.class.getMethod(
                "tenantEarningPage", int.class, int.class, java.util.UUID.class, java.time.Instant.class, java.time.Instant.class);
        PreAuthorize earningPageAuth = tenantEarningPageMethod.getAnnotation(PreAuthorize.class);
        assertThat(earningPageAuth).isNotNull();
        assertThat(earningPageAuth.value()).contains("VIEWER");

        Method policyMethod = EarningController.class.getMethod("policy", java.util.UUID.class);
        PreAuthorize policyAuth = policyMethod.getAnnotation(PreAuthorize.class);
        assertThat(policyAuth).isNotNull();
        assertThat(policyAuth.value()).contains("VIEWER");
    }
}
