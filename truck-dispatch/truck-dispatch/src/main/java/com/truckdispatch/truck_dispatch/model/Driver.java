package com.truckdispatch.truck_dispatch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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
    private List<String> recentRouteIds = new ArrayList<>();

    public boolean isLicensedFor(TruckType truckType) {
        return switch (truckType) {
            case SMALL -> true;
            case MEDIUM -> license.equals("C") || license.equals("CE");
            case LARGE -> license.equals("CE");
        };
    }
}
