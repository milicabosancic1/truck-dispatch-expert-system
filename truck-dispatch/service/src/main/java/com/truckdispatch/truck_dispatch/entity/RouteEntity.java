package com.truckdispatch.truck_dispatch.entity;

import com.truckdispatch.truck_dispatch.model.RoadType;
import com.truckdispatch.truck_dispatch.model.Route;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "routes")
@Data
@NoArgsConstructor
public class RouteEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private RoadType roadType;

    private double distanceKm;
    private double estimatedTimeHours;
    private boolean hasTunnel;
    private double maxCapacityKg;
    private double maxSpeedKmh;

    public static RouteEntity fromModel(Route r) {
        RouteEntity e = new RouteEntity();
        e.id = r.getId();
        e.roadType = r.getRoadType();
        e.distanceKm = r.getDistanceKm();
        e.estimatedTimeHours = r.getEstimatedTimeHours();
        e.hasTunnel = r.isHasTunnel();
        e.maxCapacityKg = r.getMaxCapacityKg();
        e.maxSpeedKmh = r.getMaxSpeedKmh();
        return e;
    }

    public Route toModel() {
        return new Route(id, roadType, distanceKm, estimatedTimeHours, hasTunnel, maxCapacityKg, maxSpeedKmh);
    }
}
