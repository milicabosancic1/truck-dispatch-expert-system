package com.truckdispatch.truck_dispatch.model;

public enum TruckType {
    SMALL, MEDIUM, LARGE;

    public boolean isAllowedOnRoadType(RoadType roadType) {
        return switch (this) {
            case SMALL -> true;
            case MEDIUM -> roadType != RoadType.CITY;
            case LARGE -> roadType == RoadType.REGIONAL || roadType == RoadType.HIGHWAY;
        };
    }
}
