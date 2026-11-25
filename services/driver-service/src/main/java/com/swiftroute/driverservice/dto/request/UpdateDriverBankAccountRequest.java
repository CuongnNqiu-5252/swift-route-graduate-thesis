package com.swiftroute.driverservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Bank account used to generate VietQR payroll payout codes. */
public record UpdateDriverBankAccountRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{2,20}$") String bankCode,
        @NotBlank @Pattern(regexp = "^[0-9A-Za-z]{4,40}$") String bankAccountNumber,
        @Size(max = 120) String bankAccountName) {
}
