package com.swiftroute.orderservice.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Join entity linking a dispatch plan to its constituent orders,
 * preserving the optimised stop sequence index assigned by OR-Tools.
 */
@Entity
@Table(name = "dispatch_plan_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispatchPlanOrder {

    @EmbeddedId
    private DispatchPlanOrderId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("planId")
    @JoinColumn(name = "plan_id", nullable = false)
    private DispatchPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("orderId")
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** 0-based position in the optimised route returned by routing engine. */
    @Column(name = "stop_index", nullable = false)
    private Integer stopIndex;

    // -------------------------------------------------------------------------

    /** Composite primary key for dispatch_plan_orders. */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class DispatchPlanOrderId implements Serializable {

        @Column(name = "plan_id")
        private UUID planId;

        @Column(name = "order_id")
        private UUID orderId;
    }
}
