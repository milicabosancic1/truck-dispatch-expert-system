package com.truckdispatch.truck_dispatch.model;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrder {
    private String id;
    private String destination;
    private double weightKg;
    private CargoType cargoType;
    private int deliveryDeadlineMin;
    private OrderPriority priority;
    private OrderStatus status;
    private String assignedTruckId;
    private String assignedDriverId;
    private String routeId;
    private int delayMin;
}