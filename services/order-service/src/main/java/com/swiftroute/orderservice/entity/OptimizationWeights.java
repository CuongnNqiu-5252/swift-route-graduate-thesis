package com.swiftroute.orderservice.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationWeights {
    @JsonProperty("time_weight")
    @JsonAlias({"time", "timeWeight"})
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Builder.Default
    private Double timeWeight = 0.5;
    
    @JsonProperty("cost_weight")
    @JsonAlias({"cost", "costWeight"})
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Builder.Default
    private Double costWeight = 0.5;
}
