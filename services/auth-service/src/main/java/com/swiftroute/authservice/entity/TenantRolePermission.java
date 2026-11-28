package com.swiftroute.authservice.entity;

import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenant_role_permissions")
@IdClass(TenantRolePermissionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantRolePermission {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "membership_role", nullable = false, length = 30)
    private TenantMembershipRole membershipRole;

    @Column(name = "permissions", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String permissions = "[]";

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (permissions == null || permissions.isBlank()) {
            permissions = "[]";
        }
        updatedAt = Instant.now();
    }
}
