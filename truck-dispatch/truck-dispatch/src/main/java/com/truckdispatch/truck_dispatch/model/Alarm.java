package com.truckdispatch.truck_dispatch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Alarm {
    private AlarmType type;
    private String entityId;
    private String message;
    private int affectedCount;
}
