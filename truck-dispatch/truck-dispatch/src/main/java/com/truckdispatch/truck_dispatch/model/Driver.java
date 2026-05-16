package com.truckdispatch.truck_dispatch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Driver {
    private String id;
    private boolean available;
    private double workingHoursToday;
    private String license;
    private boolean hasAdrLicense;
    private int fatigueLevel;        // 0-10
    private int yearsOfExperience;
}
