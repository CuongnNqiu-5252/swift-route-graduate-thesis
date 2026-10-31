package com.swiftroute.driverservice.entity.enums;

public enum DriverType {
    DEDICATED("dedicated"),
    POOLED("pooled");

    private final String name;

    DriverType(String name) {
        this.name = name;
    }
}
