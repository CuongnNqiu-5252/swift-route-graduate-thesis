package com.swiftroute.authservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftroute.authservice.dto.request.InviteMemberRequest;
import com.swiftroute.authservice.dto.request.UpdateMemberStatusRequest;
import com.swiftroute.authservice.dto.response.ApiResponse;
import com.swiftroute.authservice.dto.response.InviteMemberResponse;
import com.swiftroute.authservice.dto.response.TenantInvitationResponse;
import com.swiftroute.authservice.dto.response.TenantMemberResponse;
import com.swiftroute.authservice.service.TenantEmployeeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tenants/{tenantId}/members")
@RequiredArgsConstructor
@Tag(name = "Tenant Employees & Memberships", description = "Tenant employee invitation and membership management")
public class TenantMemberController {

    private final TenantEmployeeService tenantEmployeeService;

    @PostMapping("/invitations/{invitationId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeInvitation(@PathVariable UUID tenantId,
            @PathVariable UUID invitationId,
            Authentication authentication) {
        tenantEmployeeService.revokeInvitation(tenantId, invitationId, resolveUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Mời nhân viên vận hành / thành viên mới vào doanh nghiệp")
    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<InviteMemberResponse>> inviteMember(
            @PathVariable UUID tenantId,
            @Valid @RequestBody InviteMemberRequest request,
            Authentication authentication) {

        UUID inviterUserId = resolveUserId(authentication);
        InviteMemberResponse response = tenantEmployeeService.inviteMember(tenantId, inviterUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy danh sách thành viên / nhân sự của doanh nghiệp")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TenantMemberResponse>>> getTenantMembers(
            @PathVariable UUID tenantId,
            Authentication authentication) {

        UUID requesterId = resolveUserId(authentication);
        List<TenantMemberResponse> response = tenantEmployeeService.getTenantMembers(tenantId, requesterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy danh sách các lời mời tham gia doanh nghiệp")
    @GetMapping("/invitations")
    public ResponseEntity<ApiResponse<List<TenantInvitationResponse>>> getTenantInvitations(
            @PathVariable UUID tenantId,
            Authentication authentication) {

        UUID requesterId = resolveUserId(authentication);
        List<TenantInvitationResponse> response = tenantEmployeeService.getTenantInvitations(tenantId, requesterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Khóa hoặc kích hoạt lại tài khoản thành viên trong doanh nghiệp")
    @PatchMapping("/{membershipId}/status")
    public ResponseEntity<ApiResponse<TenantMemberResponse>> updateMemberStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateMemberStatusRequest request,
            Authentication authentication) {

        UUID requesterId = resolveUserId(authentication);
        TenantMemberResponse response = tenantEmployeeService.updateMemberStatus(tenantId, membershipId, request, requesterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private UUID resolveUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}

