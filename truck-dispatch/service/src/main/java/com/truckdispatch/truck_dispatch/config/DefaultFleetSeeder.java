package com.truckdispatch.truck_dispatch.config;

import com.truckdispatch.truck_dispatch.model.*;
import com.truckdispatch.truck_dispatch.service.FleetStateService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the in-memory FleetStateService with default trucks, drivers, and routes on startup.
 * The Drools engine uses this stored fleet automatically when a dispatch request
 * does not include trucks/drivers/routes (i.e. the user only submits new orders).
 */
@Component
public class DefaultFleetSeeder implements ApplicationRunner {

    private final FleetStateService fleetState;

    public DefaultFleetSeeder(FleetStateService fleetState) {
        this.fleetState = fleetState;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Seje podrazumevanu flotu samo ako baza jos nema kamiona
        if (!fleetState.hasFleet()) {
            fleetState.replaceTrucks(defaultTrucks());
            fleetState.replaceDrivers(defaultDrivers());
            fleetState.replaceRoutes(defaultRoutes());
        }
    }

    public static List<Truck> defaultTrucks() {
        return List.of(
            // K-1: frigo kamion za rashladni teret
            new Truck("K-1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE,
                      "Novi Sad", 75, true, false, 5, 10),
            // K-2: ADR kamion za opasnu robu
            new Truck("K-2", TruckType.MEDIUM, 6000, TruckStatus.AVAILABLE,
                      "Novi Sad", 80, false, true, 0, 8),
            // K-3: veliki kamion za teške pošiljke
            new Truck("K-3", TruckType.LARGE, 18000, TruckStatus.AVAILABLE,
                      "Novi Sad", 90, false, false, 0, 15)
        );
    }

    public static List<Driver> defaultDrivers() {
        return List.of(
            // V-1: iskusan CE vozač
            new Driver("V-1", true, 4.0, "CE", false, 2, 5, List.of()),
            // V-2: CE vozač sa ADR dozvolom
            new Driver("V-2", true, 3.0, "CE", true, 1, 8, List.of()),
            // V-3: C vozač, juniorno iskustvo
            new Driver("V-3", true, 6.0, "C", false, 3, 3, List.of())
        );
    }

    public static List<Route> defaultRoutes() {
        return List.of(
            // R-1: regionalna ruta za standardne dostave
            new Route("R-1", RoadType.REGIONAL, 80, 2.0, false, 24000, 90),
            // R-2: magistrala za brze dugačke dostave
            new Route("R-2", RoadType.HIGHWAY, 90, 1.0, false, 24000, 120),
            // R-3: lokalna planinska ruta sa tunelom — ograničenje mase 5 t (ADR + weight osetljivo)
            new Route("R-3", RoadType.REGIONAL, 105, 1.5, true, 5000, 80)
        );
    }
}
