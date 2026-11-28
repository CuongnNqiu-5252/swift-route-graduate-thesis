
package com.swiftroute.orderservice.dto.request;

import com.swiftroute.orderservice.entity.enums.ProofPolicy;

import jakarta.validation.constraints.NotNull;

public record DeliveryProofConfigRequest(
        @NotNull ProofPolicy successNotePolicy,
        @NotNull ProofPolicy successSignaturePolicy,
        @NotNull ProofPolicy successPhotoPolicy,

        @NotNull ProofPolicy failedNotePolicy,
        @NotNull ProofPolicy failedSignaturePolicy,
        @NotNull ProofPolicy failedPhotoPolicy) {
}
