package com.truckdispatch.truck_dispatch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.kie.api.definition.type.Role;
import org.kie.api.definition.type.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Role(Role.Type.EVENT)
@Timestamp("timestamp")
public class FleetEvent {
    private FleetEventType type;
    private String         entityId;   // truck ID
    private double         value;       // delay min / fuel % / lat / lon
    private String         location;   // GPS location string (lat,lon)
    private long           timestamp;  // epoch millis
}
