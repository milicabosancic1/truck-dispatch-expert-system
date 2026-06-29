package com.truckdispatch.truck_dispatch.entity;

import com.truckdispatch.truck_dispatch.model.Truck;
import com.truckdispatch.truck_dispatch.model.TruckStatus;
import com.truckdispatch.truck_dispatch.model.TruckType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trucks")
@Data
@NoArgsConstructor
public class TruckEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private TruckType type;

    private double maxCapacityKg;

    @Enumerated(EnumType.STRING)
    private TruckStatus status;

    private String location;
    private double fuelPercent;
    private boolean hasRefrigerationUnit;
    private boolean hasAdrEquipment;
    private int daysSinceRefrigerationService;
    private double distanceToOriginKm;

    public static TruckEntity fromModel(Truck t) {
        TruckEntity e = new TruckEntity();
        e.id = t.getId();
        e.type = t.getType();
        e.maxCapacityKg = t.getMaxCapacityKg();
        e.status = t.getStatus();
        e.location = t.getLocation();
        e.fuelPercent = t.getFuelPercent();
        e.hasRefrigerationUnit = t.isHasRefrigerationUnit();
        e.hasAdrEquipment = t.isHasAdrEquipment();
        e.daysSinceRefrigerationService = t.getDaysSinceRefrigerationService();
        e.distanceToOriginKm = t.getDistanceToOriginKm();
        return e;
    }

    public Truck toModel() {
        return new Truck(id, type, maxCapacityKg, status, location, fuelPercent,
                hasRefrigerationUnit, hasAdrEquipment, daysSinceRefrigerationService, distanceToOriginKm);
    }
}
