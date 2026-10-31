package com.swiftroute.driverservice.entity.enums;

public enum VehicleType {
    CAR("car"),
    MOTORCYCLE("motorcycle"),
    VAN("van");

    private final String name;

    VehicleType(String name) {
        this.name = name;
    }
}
