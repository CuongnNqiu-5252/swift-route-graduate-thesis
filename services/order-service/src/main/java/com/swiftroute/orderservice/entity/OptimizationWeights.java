package com.swiftroute.orderservice.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationWeights {
    @JsonProperty("time_weight")
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double timeWeight = 0.5;
    
    @JsonProperty("cost_weight")
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double costWeight = 0.5;
}
