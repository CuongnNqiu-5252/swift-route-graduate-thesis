package com.swiftroute.authservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.swiftroute.authservice.entity.enums.TenantMembershipRole;
import com.swiftroute.authservice.entity.enums.TenantMembershipStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Auth Service projection of a user's logical membership in an Order Service tenant. */
@Entity
@Table(name = "tenant_memberships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "membership_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_role", nullable = false, length = 20)
    private TenantMembershipRole membershipRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TenantMembershipStatus status = TenantMembershipStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
