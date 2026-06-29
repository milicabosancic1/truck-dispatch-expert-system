package com.truckdispatch.truck_dispatch.entity;

import com.truckdispatch.truck_dispatch.model.Driver;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "drivers")
@Data
@NoArgsConstructor
public class DriverEntity {

    @Id
    private String id;

    private boolean available;
    private double workingHoursToday;
    private String license;
    private boolean hasAdrLicense;
    private int fatigueLevel;
    private int yearsOfExperience;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "driver_recent_routes", joinColumns = @JoinColumn(name = "driver_id"))
    @Column(name = "route_id")
    private List<String> recentRouteIds = new ArrayList<>();

    public static DriverEntity fromModel(Driver d) {
        DriverEntity e = new DriverEntity();
        e.id = d.getId();
        e.available = d.isAvailable();
        e.workingHoursToday = d.getWorkingHoursToday();
        e.license = d.getLicense();
        e.hasAdrLicense = d.isHasAdrLicense();
        e.fatigueLevel = d.getFatigueLevel();
        e.yearsOfExperience = d.getYearsOfExperience();
        e.recentRouteIds = new ArrayList<>(d.getRecentRouteIds());
        return e;
    }

    public Driver toModel() {
        return new Driver(id, available, workingHoursToday, license,
                hasAdrLicense, fatigueLevel, yearsOfExperience, new ArrayList<>(recentRouteIds));
    }
}
