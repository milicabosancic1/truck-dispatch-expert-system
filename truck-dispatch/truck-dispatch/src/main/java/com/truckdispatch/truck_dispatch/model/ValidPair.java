package com.truckdispatch.truck_dispatch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidPair {
    private DeliveryOrder order;
    private Truck truck;
    private Driver driver;
    private int score;
}
