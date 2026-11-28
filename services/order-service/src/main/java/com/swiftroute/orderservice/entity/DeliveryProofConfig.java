package com.swiftroute.orderservice.entity;

import java.util.UUID;

import com.swiftroute.orderservice.entity.enums.ProofPolicy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "delivery_proof_configs")
@Getter
@Setter
public class DeliveryProofConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "config_id", updatable = false, nullable = false)
    private UUID configId;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    // Giao thành công
    @Enumerated(EnumType.STRING)
    @Column(name = "success_note_policy", nullable = false, length = 20)
    private ProofPolicy successNotePolicy = ProofPolicy.OPTIONAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "success_signature_policy", nullable = false, length = 20)
    private ProofPolicy successSignaturePolicy = ProofPolicy.DISABLED;

    @Enumerated(EnumType.STRING)
    @Column(name = "success_photo_policy", nullable = false, length = 20)
    private ProofPolicy successPhotoPolicy = ProofPolicy.REQUIRED;

    // Giao thất bại
    @Enumerated(EnumType.STRING)
    @Column(name = "failed_note_policy", nullable = false, length = 20)
    private ProofPolicy failedNotePolicy = ProofPolicy.REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "failed_signature_policy", nullable = false, length = 20)
    private ProofPolicy failedSignaturePolicy = ProofPolicy.DISABLED;

    @Enumerated(EnumType.STRING)
    @Column(name = "failed_photo_policy", nullable = false, length = 20)
    private ProofPolicy failedPhotoPolicy = ProofPolicy.OPTIONAL;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;
}
