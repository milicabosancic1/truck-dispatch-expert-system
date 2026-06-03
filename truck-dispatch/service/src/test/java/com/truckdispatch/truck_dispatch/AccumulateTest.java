package com.truckdispatch.truck_dispatch;

import com.truckdispatch.truck_dispatch.dto.DispatchRequest;
import com.truckdispatch.truck_dispatch.dto.DispatchResult;
import com.truckdispatch.truck_dispatch.model.*;
import com.truckdispatch.truck_dispatch.service.DispatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Accumulate — aggregate rules (sum, count, average, collectList)")
class AccumulateTest {

    @Autowired
    private DispatchService dispatchService;

    // ---- builders ----

    private Truck truck(String id, TruckStatus status) {
        Truck t = new Truck();
        t.setId(id); t.setType(TruckType.MEDIUM); t.setMaxCapacityKg(10000);
        t.setStatus(status); t.setHasRefrigerationUnit(false); t.setHasAdrEquipment(false);
        t.setFuelPercent(80); t.setDistanceToOriginKm(5);
        t.setDaysSinceRefrigerationService(5); t.setLocation("NS");
        return t;
    }

    private Driver driver(String id, boolean available, double hours) {
        Driver d = new Driver();
        d.setId(id); d.setAvailable(available); d.setWorkingHoursToday(hours);
        d.setLicense("CE"); d.setHasAdrLicense(false);
        d.setFatigueLevel(1); d.setYearsOfExperience(5);
        return d;
    }

    // Pre-ASSIGNED order — bypasses pipeline (only NEW orders are processed)
    private DeliveryOrder assignedOrder(String id, String routeId, double kg,
                                        String truckId, String driverId) {
        DeliveryOrder o = new DeliveryOrder();
        o.setId(id); o.setRouteId(routeId); o.setWeightKg(kg);
        o.setCargoType(CargoType.STANDARD); o.setDeliveryDeadlineMin(300);
        o.setPriority(OrderPriority.NORMAL); o.setStatus(OrderStatus.ASSIGNED);
        o.setDestination("Beograd");
        o.setAssignedTruckId(truckId); o.setAssignedDriverId(driverId);
        return o;
    }

    // IN_PROGRESS order with delay (for average-delay rule)
    private DeliveryOrder inProgressOrder(String id, String routeId, double kg,
                                          int delayMin, String truckId, String driverId) {
        DeliveryOrder o = new DeliveryOrder();
        o.setId(id); o.setRouteId(routeId); o.setWeightKg(kg);
        o.setCargoType(CargoType.STANDARD); o.setDeliveryDeadlineMin(300);
        o.setPriority(OrderPriority.NORMAL); o.setStatus(OrderStatus.IN_PROGRESS);
        o.setDestination("Beograd"); o.setDelayMin(delayMin);
        o.setAssignedTruckId(truckId); o.setAssignedDriverId(driverId);
        return o;
    }

    private DispatchRequest req(List<Truck> trucks, List<Driver> drivers,
                                List<DeliveryOrder> orders) {
        DispatchRequest r = new DispatchRequest();
        r.setTemperature(15.0); r.setHour(10); r.setDayOfWeek(3);
        r.setTrucks(trucks); r.setDrivers(drivers);
        r.setRoutes(List.of()); r.setOrders(orders);
        return r;
    }

    // ---- SUM: overloaded driver ----

    @Nested
    @DisplayName("SUM — overloaded driver (> 20000 kg)")
    class OverloadedDriver {

        @Test
        @DisplayName("OVERLOADED_DRIVER alarm fires when driver's total assigned weight exceeds 20000 kg")
        void alarmFiresOver20000kg() {
            // 3 × 7001 kg = 21003 kg > 20000
            Driver d = driver("D1", true, 1.0);
            List<DeliveryOrder> orders = List.of(
                    assignedOrder("O1", "R1", 7001, "T1", "D1"),
                    assignedOrder("O2", "R1", 7001, "T1", "D1"),
                    assignedOrder("O3", "R1", 7001, "T1", "D1")
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(truck("T1", TruckStatus.BUSY)), List.of(d), orders));

            assertThat(res.getAlarms())
                    .anyMatch(a -> a.getType() == AlarmType.OVERLOADED_DRIVER
                               && "D1".equals(a.getEntityId()));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("D1") && m.contains("21003"));
        }

        @Test
        @DisplayName("No alarm when driver's total weight stays under 20000 kg")
        void noAlarmUnder20000kg() {
            Driver d = driver("D1", true, 1.0);
            List<DeliveryOrder> orders = List.of(
                    assignedOrder("O1", "R1", 2000, "T1", "D1"),
                    assignedOrder("O2", "R1", 2000, "T1", "D1")
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(truck("T1", TruckStatus.BUSY)), List.of(d), orders));

            assertThat(res.getAlarms())
                    .noneMatch(a -> a.getType() == AlarmType.OVERLOADED_DRIVER);
        }
    }

    // ---- COUNT: overloaded truck ----

    @Nested
    @DisplayName("COUNT — overloaded truck (> 3 active orders)")
    class OverloadedTruck {

        @Test
        @DisplayName("OVERLOADED_TRUCK alarm fires when truck has more than 3 active orders")
        void alarmFiresOver3Orders() {
            Truck t = truck("T1", TruckStatus.BUSY);
            Driver d = driver("D1", false, 8.0);
            List<DeliveryOrder> orders = List.of(
                    assignedOrder("O1", "R1", 100, "T1", "D1"),
                    assignedOrder("O2", "R1", 100, "T1", "D1"),
                    assignedOrder("O3", "R1", 100, "T1", "D1"),
                    assignedOrder("O4", "R1", 100, "T1", "D1")
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(t), List.of(d), orders));

            assertThat(res.getAlarms())
                    .anyMatch(a -> a.getType() == AlarmType.OVERLOADED_TRUCK
                               && "T1".equals(a.getEntityId()));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("T1") && m.contains("4 active orders"));
        }

        @Test
        @DisplayName("No alarm when truck has exactly 3 orders — boundary: rule fires only when count > 3")
        void noAlarmAt3Orders() {
            Truck t = truck("T1", TruckStatus.BUSY);
            Driver d = driver("D1", false, 8.0);
            List<DeliveryOrder> orders = List.of(
                    assignedOrder("O1", "R1", 100, "T1", "D1"),
                    assignedOrder("O2", "R1", 100, "T1", "D1"),
                    assignedOrder("O3", "R1", 100, "T1", "D1")
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(t), List.of(d), orders));

            assertThat(res.getAlarms())
                    .noneMatch(a -> a.getType() == AlarmType.OVERLOADED_TRUCK);
        }
    }

    // ---- AVERAGE: route delay ----

    @Nested
    @DisplayName("AVERAGE — problematic route (avg delay > 30 min)")
    class AvgDelayByRoute {

        @Test
        @DisplayName("PROBLEMATIC_ROUTE alarm fires when average delay on a route exceeds 30 min")
        void alarmFiresAvgOver30min() {
            // 2 in-progress orders on R1, each delayed 40 min → avg = 40 > 30
            List<DeliveryOrder> orders = List.of(
                    inProgressOrder("O1", "R1", 1000, 40, "T1", "D1"),
                    inProgressOrder("O2", "R1", 1000, 40, "T2", "D2")
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(truck("T1", TruckStatus.BUSY), truck("T2", TruckStatus.BUSY)),
                        List.of(driver("D1", false, 8.0), driver("D2", false, 8.0)),
                        orders));

            assertThat(res.getAlarms())
                    .anyMatch(a -> a.getType() == AlarmType.PROBLEMATIC_ROUTE
                               && "R1".equals(a.getEntityId()));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("R1") && m.contains("average delay"));
        }

        @Test
        @DisplayName("No alarm when average route delay is 30 min or less")
        void noAlarmAvgUnder30min() {
            List<DeliveryOrder> orders = List.of(
                    inProgressOrder("O1", "R1", 1000, 10, "T1", "D1"),
                    inProgressOrder("O2", "R1", 1000, 10, "T2", "D2")
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(truck("T1", TruckStatus.BUSY), truck("T2", TruckStatus.BUSY)),
                        List.of(driver("D1", false, 8.0), driver("D2", false, 8.0)),
                        orders));

            assertThat(res.getAlarms())
                    .noneMatch(a -> a.getType() == AlarmType.PROBLEMATIC_ROUTE);
        }
    }

    // ---- SUM/COUNT: team working hours ----

    @Nested
    @DisplayName("SUM/COUNT — team working hours (avg > 7.5 h)")
    class TeamHoursWarning {

        @Test
        @DisplayName("SHIFT_WARNING alarm fires when team average working hours exceeds 7.5 h")
        void alarmFiresAvgOver75h() {
            // 3 available drivers at 8.0h each → avg = 8.0 > 7.5
            // workingHoursToday=8.0 excludes them from pipeline assignment (< 8 fails)
            // but available=true so accumulate sees them
            List<Driver> drivers = List.of(
                    driver("D1", true, 8.0),
                    driver("D2", true, 8.0),
                    driver("D3", true, 8.0)
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(), drivers, List.of()));

            assertThat(res.getAlarms())
                    .anyMatch(a -> a.getType() == AlarmType.SHIFT_WARNING
                               && "TEAM".equals(a.getEntityId()));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("working hours") && m.contains("shift"));
        }

        @Test
        @DisplayName("No alarm when team average working hours is under 7.5 h")
        void noAlarmAvgUnder75h() {
            List<Driver> drivers = List.of(
                    driver("D1", true, 3.0),
                    driver("D2", true, 3.0),
                    driver("D3", true, 3.0)
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(), drivers, List.of()));

            assertThat(res.getAlarms())
                    .noneMatch(a -> a.getType() == AlarmType.SHIFT_WARNING);
        }
    }

    // ---- COLLECT LIST: idle fleet ----

    @Nested
    @DisplayName("COLLECT LIST — idle fleet (> 5 available trucks)")
    class IdleFleet {

        @Test
        @DisplayName("IDLE_FLEET alarm fires when more than 5 trucks are available")
        void alarmFiresOver5IdleTrucks() {
            List<Truck> trucks = List.of(
                    truck("T1", TruckStatus.AVAILABLE), truck("T2", TruckStatus.AVAILABLE),
                    truck("T3", TruckStatus.AVAILABLE), truck("T4", TruckStatus.AVAILABLE),
                    truck("T5", TruckStatus.AVAILABLE), truck("T6", TruckStatus.AVAILABLE)
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(trucks, List.of(), List.of()));

            assertThat(res.getAlarms())
                    .anyMatch(a -> a.getType() == AlarmType.IDLE_FLEET);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("idle trucks") && m.contains("6"));
        }

        @Test
        @DisplayName("No alarm when exactly 5 trucks are available — boundary: rule fires only when count > 5")
        void noAlarmAt5IdleTrucks() {
            List<Truck> trucks = List.of(
                    truck("T1", TruckStatus.AVAILABLE), truck("T2", TruckStatus.AVAILABLE),
                    truck("T3", TruckStatus.AVAILABLE), truck("T4", TruckStatus.AVAILABLE),
                    truck("T5", TruckStatus.AVAILABLE)
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(trucks, List.of(), List.of()));

            assertThat(res.getAlarms())
                    .noneMatch(a -> a.getType() == AlarmType.IDLE_FLEET);
        }
    }
}
