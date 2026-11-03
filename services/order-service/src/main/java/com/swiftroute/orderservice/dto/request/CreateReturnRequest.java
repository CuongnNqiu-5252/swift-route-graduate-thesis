package com.swiftroute.orderservice.dto.request; import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Pattern;
public record CreateReturnRequest(@Pattern(regexp="REDELIVERY|RETURN_TO_ORIGIN") String requestType,@NotBlank String reason) {}
