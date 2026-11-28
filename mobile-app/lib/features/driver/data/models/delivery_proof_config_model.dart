// features/driver/data/models/delivery_proof_config_model.dart

enum ProofPolicy {
  disabled,
  optional,
  required;

  bool get isRequired => this == ProofPolicy.required;
  bool get isDisabled => this == ProofPolicy.disabled;
  bool get isEnabled => this != ProofPolicy.disabled;

  String get serverValue => switch (this) {
    ProofPolicy.disabled => 'DISABLED',
    ProofPolicy.optional => 'OPTIONAL',
    ProofPolicy.required => 'REQUIRED',
  };

  static ProofPolicy fromString(
    String? raw, {
    ProofPolicy fallback = ProofPolicy.optional,
  }) {
    if (raw == null || raw.trim().isEmpty) return fallback;
    return switch (raw.trim().toUpperCase()) {
      'DISABLED' => ProofPolicy.disabled,
      'OPTIONAL' => ProofPolicy.optional,
      'REQUIRED' => ProofPolicy.required,
      _ => fallback,
    };
  }
}

class DeliveryProofConfigModel {
  const DeliveryProofConfigModel({
    this.successNotePolicy = ProofPolicy.optional,
    this.successSignaturePolicy = ProofPolicy.disabled,
    this.successPhotoPolicy = ProofPolicy.required,
    this.failedNotePolicy = ProofPolicy.required,
    this.failedSignaturePolicy = ProofPolicy.disabled,
    this.failedPhotoPolicy = ProofPolicy.optional,
  });

  final ProofPolicy successNotePolicy;
  final ProofPolicy successSignaturePolicy;
  final ProofPolicy successPhotoPolicy;
  final ProofPolicy failedNotePolicy;
  final ProofPolicy failedSignaturePolicy;
  final ProofPolicy failedPhotoPolicy;

  factory DeliveryProofConfigModel.fromJson(Map<String, dynamic> json) {
    return DeliveryProofConfigModel(
      successNotePolicy: ProofPolicy.fromString(
        (json['success_note_policy'] ?? json['successNotePolicy'])?.toString(),
        fallback: ProofPolicy.optional,
      ),
      successSignaturePolicy: ProofPolicy.fromString(
        (json['success_signature_policy'] ?? json['successSignaturePolicy'])
            ?.toString(),
        fallback: ProofPolicy.disabled,
      ),
      successPhotoPolicy: ProofPolicy.fromString(
        (json['success_photo_policy'] ?? json['successPhotoPolicy'])
            ?.toString(),
        fallback: ProofPolicy.required,
      ),
      failedNotePolicy: ProofPolicy.fromString(
        (json['failed_note_policy'] ?? json['failedNotePolicy'])?.toString(),
        fallback: ProofPolicy.required,
      ),
      failedSignaturePolicy: ProofPolicy.fromString(
        (json['failed_signature_policy'] ?? json['failedSignaturePolicy'])
            ?.toString(),
        fallback: ProofPolicy.disabled,
      ),
      failedPhotoPolicy: ProofPolicy.fromString(
        (json['failed_photo_policy'] ?? json['failedPhotoPolicy'])?.toString(),
        fallback: ProofPolicy.optional,
      ),
    );
  }

  Map<String, dynamic> toJson() => {
    'success_note_policy': successNotePolicy.serverValue,
    'success_signature_policy': successSignaturePolicy.serverValue,
    'success_photo_policy': successPhotoPolicy.serverValue,
    'failed_note_policy': failedNotePolicy.serverValue,
    'failed_signature_policy': failedSignaturePolicy.serverValue,
    'failed_photo_policy': failedPhotoPolicy.serverValue,
  };
}
