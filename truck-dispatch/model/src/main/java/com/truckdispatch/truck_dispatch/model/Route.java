package com.truckdispatch.truck_dispatch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Route {
    private String id;
    private RoadType roadType;
    private double distanceKm;
    private double estimatedTimeHours;
    private boolean hasTunnel;
    private double maxCapacityKg;
    private double maxSpeedKmh;
}