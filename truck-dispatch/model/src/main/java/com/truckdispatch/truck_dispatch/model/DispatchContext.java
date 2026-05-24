package com.truckdispatch.truck_dispatch.model;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispatchContext {
    private OperationalContext context;
    private double capacityFactor;   // 1.0 normalno, 0.85 zima
    private double fleetFactor;      // 1.0 normalno, 0.6 vikend
    private int delayThresholdMin;   // prag kašnjenja za eskalaciju
}