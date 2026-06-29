package com.truckdispatch.truck_dispatch.service;

import com.truckdispatch.truck_dispatch.entity.DriverEntity;
import com.truckdispatch.truck_dispatch.entity.RouteEntity;
import com.truckdispatch.truck_dispatch.entity.TruckEntity;
import com.truckdispatch.truck_dispatch.model.DeliveryOrder;
import com.truckdispatch.truck_dispatch.model.Driver;
import com.truckdispatch.truck_dispatch.model.OrderStatus;
import com.truckdispatch.truck_dispatch.model.Route;
import com.truckdispatch.truck_dispatch.model.Truck;
import com.truckdispatch.truck_dispatch.repository.DriverRepository;
import com.truckdispatch.truck_dispatch.repository.RouteRepository;
import com.truckdispatch.truck_dispatch.repository.TruckRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FleetStateService {

    private final Map<String, Truck>         trucks  = new ConcurrentHashMap<>();
    private final Map<String, Driver>        drivers = new ConcurrentHashMap<>();
    private final Map<String, Route>         routes  = new ConcurrentHashMap<>();
    private final Map<String, DeliveryOrder> orders  = new ConcurrentHashMap<>();

    private final TruckRepository  truckRepo;
    private final DriverRepository driverRepo;
    private final RouteRepository  routeRepo;

    public FleetStateService(TruckRepository truckRepo,
                             DriverRepository driverRepo,
                             RouteRepository routeRepo) {
        this.truckRepo  = truckRepo;
        this.driverRepo = driverRepo;
        this.routeRepo  = routeRepo;
    }

    /** Ucitava flotu iz baze u memoriju pri startu. */
    @PostConstruct
    public void loadFromDatabase() {
        truckRepo.findAll().forEach(e  -> trucks.put(e.getId(),  e.toModel()));
        driverRepo.findAll().forEach(e -> drivers.put(e.getId(), e.toModel()));
        routeRepo.findAll().forEach(e  -> routes.put(e.getId(),  e.toModel()));
    }

    // ---- replace (cuva i u bazu) ----

    public void replaceTrucks(List<Truck> list) {
        trucks.clear();
        list.forEach(t -> trucks.put(t.getId(), t));
        truckRepo.deleteAll();
        truckRepo.saveAll(list.stream().map(TruckEntity::fromModel).toList());
    }

    public void replaceDrivers(List<Driver> list) {
        drivers.clear();
        list.forEach(d -> drivers.put(d.getId(), d));
        driverRepo.deleteAll();
        driverRepo.saveAll(list.stream().map(DriverEntity::fromModel).toList());
    }

    public void replaceRoutes(List<Route> list) {
        routes.clear();
        list.forEach(r -> routes.put(r.getId(), r));
        routeRepo.deleteAll();
        routeRepo.saveAll(list.stream().map(RouteEntity::fromModel).toList());
    }

    // ---- upsert (azurira i bazu) ----

    public void upsertTruck(Truck t) {
        trucks.put(t.getId(), t);
        truckRepo.save(TruckEntity.fromModel(t));
    }

    public void upsertDriver(Driver d) {
        drivers.put(d.getId(), d);
        driverRepo.save(DriverEntity.fromModel(d));
    }

    public void upsertOrder(DeliveryOrder o) {
        orders.put(o.getId(), o);
    }

    // ---- getters ----

    public List<Truck>         getTrucks()  { return new ArrayList<>(trucks.values()); }
    public List<Driver>        getDrivers() { return new ArrayList<>(drivers.values()); }
    public List<Route>         getRoutes()  { return new ArrayList<>(routes.values()); }
    public List<DeliveryOrder> getOrders()  { return new ArrayList<>(orders.values()); }

    public List<DeliveryOrder> getActiveOrders() {
        return orders.values().stream()
                .filter(o -> o.getStatus() == OrderStatus.ASSIGNED
                          || o.getStatus() == OrderStatus.IN_PROGRESS
                          || o.getStatus() == OrderStatus.WAITING_UNLOADING)
                .toList();
    }

    public void clearOrders() { orders.clear(); }

    public boolean hasFleet() { return !trucks.isEmpty(); }
}
