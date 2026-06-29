package com.truckdispatch.truck_dispatch.service;

import com.truckdispatch.truck_dispatch.model.DeliveryOrder;
import com.truckdispatch.truck_dispatch.model.Driver;
import com.truckdispatch.truck_dispatch.model.OrderStatus;
import com.truckdispatch.truck_dispatch.model.Route;
import com.truckdispatch.truck_dispatch.model.Truck;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * In-memory source of truth for fleet state.
 * Written by both DispatchService (after FC dispatch) and CepService (after CEP lifecycle events).
 * Read by DispatchService when building the next FC session without a full fleet in the request.
 */
@Service
public class FleetStateService {

    private final Map<String, Truck>         trucks  = new ConcurrentHashMap<>();
    private final Map<String, Driver>        drivers = new ConcurrentHashMap<>();
    private final Map<String, Route>         routes  = new ConcurrentHashMap<>();
    private final Map<String, DeliveryOrder> orders  = new ConcurrentHashMap<>();

    /** Replace the entire truck/driver/route store with the provided list (full fleet config). */
    public void replaceTrucks(List<Truck> list)   { trucks.clear();  list.forEach(t -> trucks.put(t.getId(), t)); }
    public void replaceDrivers(List<Driver> list)  { drivers.clear(); list.forEach(d -> drivers.put(d.getId(), d)); }
    public void replaceRoutes(List<Route> list)    { routes.clear();  list.forEach(r -> routes.put(r.getId(), r)); }

    public void upsertTruck(Truck t)           { trucks.put(t.getId(), t); }
    public void upsertDriver(Driver d)         { drivers.put(d.getId(), d); }
    public void upsertOrder(DeliveryOrder o)   { orders.put(o.getId(), o); }

    public List<Truck>         getTrucks()  { return new ArrayList<>(trucks.values()); }
    public List<Driver>        getDrivers() { return new ArrayList<>(drivers.values()); }
    public List<Route>         getRoutes()  { return new ArrayList<>(routes.values()); }
    public List<DeliveryOrder> getOrders()  { return new ArrayList<>(orders.values()); }

    /** Orders CEP lifecycle rules can still act on (not yet completed or failed). */
    public List<DeliveryOrder> getActiveOrders() {
        return orders.values().stream()
                .filter(o -> o.getStatus() == OrderStatus.ASSIGNED
                          || o.getStatus() == OrderStatus.IN_PROGRESS
                          || o.getStatus() == OrderStatus.WAITING_UNLOADING)
                .toList();
    }

    public boolean hasFleet() { return !trucks.isEmpty(); }
}
