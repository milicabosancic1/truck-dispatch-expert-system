package com.truckdispatch.truck_dispatch.controller;

import com.truckdispatch.truck_dispatch.config.DefaultFleetSeeder;
import com.truckdispatch.truck_dispatch.dto.DispatchRequest;
import com.truckdispatch.truck_dispatch.dto.DispatchResult;
import com.truckdispatch.truck_dispatch.dto.FleetSaveRequest;
import com.truckdispatch.truck_dispatch.model.Alarm;
import com.truckdispatch.truck_dispatch.model.FleetEvent;
import com.truckdispatch.truck_dispatch.service.CepService;
import com.truckdispatch.truck_dispatch.service.DispatchService;
import com.truckdispatch.truck_dispatch.service.FleetStateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dispatch")
public class DispatchController {

    private final DispatchService   dispatchService;
    private final CepService        cepService;
    private final FleetStateService fleetState;

    public DispatchController(DispatchService dispatchService,
                              CepService cepService,
                              FleetStateService fleetState) {
        this.dispatchService = dispatchService;
        this.cepService      = cepService;
        this.fleetState      = fleetState;
    }

    /**
     * POST /api/dispatch/process
     * Dispatches new orders using the current fleet state.
     * Trucks/drivers/routes in the request are optional after the first call —
     * omit them to reuse the stored fleet (with statuses updated by CEP events).
     */
    @PostMapping("/process")
    public ResponseEntity<DispatchResult> process(@RequestBody DispatchRequest request) {
        DispatchResult result = dispatchService.processDispatch(request);
        // Sync full current state to CEP so lifecycle rules see all active orders and trucks.
        cepService.syncFleetState(fleetState.getTrucks(), fleetState.getActiveOrders());
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/dispatch/event
     * Sends a real-time fleet event into the CEP session (Drools Fusion).
     * Lifecycle events (TRIP_STARTED, DELIVERY_CONFIRMED, …) update order/truck
     * status in FleetStateService so the next dispatch reflects current availability.
     */
    @PostMapping("/event")
    public ResponseEntity<List<String>> processEvent(@RequestBody FleetEvent event) {
        if (event.getTimestamp() <= 0) {
            event.setTimestamp(System.currentTimeMillis());
        }
        return ResponseEntity.ok(cepService.processEvent(event));
    }

    /**
     * GET /api/dispatch/alarms
     * Returns all active alarms currently in the CEP session working memory.
     */
    @GetMapping("/alarms")
    public ResponseEntity<List<Alarm>> getAlarms() {
        return ResponseEntity.ok(cepService.getActiveAlarms());
    }

    /**
     * POST /api/dispatch/reset-fleet
     * Restores the default seeded fleet and clears all accumulated orders.
     * Called from the frontend Reset button so demos always start from a clean state.
     */
    @PostMapping("/reset-fleet")
    public ResponseEntity<Map<String, Object>> resetFleet() {
        fleetState.replaceTrucks(DefaultFleetSeeder.defaultTrucks());
        fleetState.replaceDrivers(DefaultFleetSeeder.defaultDrivers());
        fleetState.replaceRoutes(DefaultFleetSeeder.defaultRoutes());
        fleetState.clearOrders();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("trucks",  fleetState.getTrucks());
        state.put("drivers", fleetState.getDrivers());
        state.put("routes",  fleetState.getRoutes());
        state.put("orders",  fleetState.getOrders());
        return ResponseEntity.ok(state);
    }

    /**
     * PUT /api/dispatch/fleet
     * Saves the full fleet (trucks, drivers, routes) from the Fleet Management tab.
     * Replaces the current fleet in memory and in the H2 database.
     */
    @PutMapping("/fleet")
    public ResponseEntity<Map<String, Object>> saveFleet(@RequestBody FleetSaveRequest req) {
        if (!req.getTrucks().isEmpty())  fleetState.replaceTrucks(req.getTrucks());
        if (!req.getDrivers().isEmpty()) fleetState.replaceDrivers(req.getDrivers());
        if (!req.getRoutes().isEmpty())  fleetState.replaceRoutes(req.getRoutes());
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("trucks",  fleetState.getTrucks());
        state.put("drivers", fleetState.getDrivers());
        state.put("routes",  fleetState.getRoutes());
        return ResponseEntity.ok(state);
    }

    /**
     * GET /api/dispatch/fleet
     * Returns the current fleet state as tracked by the system
     * (trucks with live statuses, active orders, available drivers).
     */
    @GetMapping("/fleet")
    public ResponseEntity<Map<String, Object>> getFleetState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("trucks",  fleetState.getTrucks());
        state.put("drivers", fleetState.getDrivers());
        state.put("routes",  fleetState.getRoutes());
        state.put("orders",  fleetState.getOrders());
        return ResponseEntity.ok(state);
    }
}
