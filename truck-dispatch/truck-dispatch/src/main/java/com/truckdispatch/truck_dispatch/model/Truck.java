package com.truckdispatch.truck_dispatch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Truck {
    private String id;
    private TruckType type;
    private double maxCapacityKg;
    private TruckStatus status;
    private String location;
    private double fuelPercent;
    private boolean hasRefrigerationUnit;
    private boolean hasAdrEquipment;
    private int daysSinceRefrigerationService;
}