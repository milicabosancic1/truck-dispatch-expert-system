package com.truckdispatch.truck_dispatch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FleetEvent {
    private FleetEventType type;
    private String         entityId;   // truck ID
    private double         value;       // delay min / fuel % / lat / lon
    private String         location;   // GPS location string (lat,lon)
    private long           timestamp;  // epoch millis
}
