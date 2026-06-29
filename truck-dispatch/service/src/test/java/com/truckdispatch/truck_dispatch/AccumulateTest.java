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

    // ---- pomoćne metode ----

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

    // Nalog već u statusu ASSIGNED — ne prolazi kroz pipeline (samo NEW nalozi se obrađuju)
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

    // Nalog u statusu IN_PROGRESS sa kašnjenjem (za pravilo prosečnog kašnjenja po ruti)
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

    // ---- COLLECT LIST: preopterećeni vozači (radno vreme > 9h) ----

    @Nested
    @DisplayName("COLLECT LIST — overloaded drivers (working hours > 9h)")
    class OverloadedDriver {

        @Test
        @DisplayName("OVERLOADED_DRIVER fleet alarm fires listing all drivers over 9h")
        void alarmFiresWithMultipleDriversOver9h() {
            // D1=10h, D2=11h prelaze granicu; D3=8h ispod → alarm nabraja D1 i D2
            List<Driver> drivers = List.of(
                    driver("D1", true, 10.0),
                    driver("D2", true, 11.0),
                    driver("D3", true, 8.0)
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(), drivers, List.of()));

            assertThat(res.getAlarms())
                    .anyMatch(a -> a.getType() == AlarmType.OVERLOADED_DRIVER
                               && "FLEET".equals(a.getEntityId())
                               && a.getAffectedCount() == 2);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("2 driver(s)") && m.contains("D1") && m.contains("D2"));
        }

        @Test
        @DisplayName("No alarm when all drivers are at or under 9 working hours")
        void noAlarmAt9h() {
            List<Driver> drivers = List.of(
                    driver("D1", true, 9.0),
                    driver("D2", true, 7.0)
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(), drivers, List.of()));

            assertThat(res.getAlarms())
                    .noneMatch(a -> a.getType() == AlarmType.OVERLOADED_DRIVER);
        }
    }

    // ---- SUM: preopterećeni kamion (ukupna dodeljena masa > maxCapacityKg) ----

    @Nested
    @DisplayName("SUM — overloaded truck (total assigned weight > maxCapacityKg)")
    class OverloadedTruck {

        @Test
        @DisplayName("OVERLOADED_TRUCK alarm fires when total assigned weight exceeds truck capacity")
        void alarmFiresWhenWeightExceedsCapacity() {
            // kapacitet kamiona 10000 kg, 3 naloga × 4000 = 12000 > 10000
            Truck t = truck("T1", TruckStatus.BUSY);
            Driver d = driver("D1", false, 8.0);
            List<DeliveryOrder> orders = List.of(
                    assignedOrder("O1", "R1", 4000, "T1", "D1"),
                    assignedOrder("O2", "R1", 4000, "T1", "D1"),
                    assignedOrder("O3", "R1", 4000, "T1", "D1")
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(t), List.of(d), orders));

            assertThat(res.getAlarms())
                    .anyMatch(a -> a.getType() == AlarmType.OVERLOADED_TRUCK
                               && "T1".equals(a.getEntityId()));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("T1") && m.contains("exceeds capacity"));
        }

        @Test
        @DisplayName("No alarm when total weight equals truck capacity — boundary: rule fires only when > maxCapacityKg")
        void noAlarmAtExactCapacity() {
            // kapacitet kamiona 10000 kg, 2 naloga × 5000 = 10000, NIJE > 10000
            Truck t = truck("T1", TruckStatus.BUSY);
            Driver d = driver("D1", false, 8.0);
            List<DeliveryOrder> orders = List.of(
                    assignedOrder("O1", "R1", 5000, "T1", "D1"),
                    assignedOrder("O2", "R1", 5000, "T1", "D1")
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(t), List.of(d), orders));

            assertThat(res.getAlarms())
                    .noneMatch(a -> a.getType() == AlarmType.OVERLOADED_TRUCK);
        }
    }

    // ---- AVERAGE: kašnjenje po ruti ----

    @Nested
    @DisplayName("AVERAGE — problematic route (avg delay > 30 min)")
    class AvgDelayByRoute {

        @Test
        @DisplayName("PROBLEMATIC_ROUTE alarm fires when average delay on a route exceeds 30 min")
        void alarmFiresAvgOver30min() {
            // 2 naloga IN_PROGRESS na ruti R1, svaki kasni 40 min → prosek = 40 > 30
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

    // ---- SUM/COUNT: radni sati tima ----

    @Nested
    @DisplayName("SUM/COUNT — team working hours (avg > 7.5 h)")
    class TeamHoursWarning {

        @Test
        @DisplayName("SHIFT_WARNING alarm fires when team average working hours exceeds 7.5 h")
        void alarmFiresAvgOver75h() {
            // 3 slobodna vozača po 8.0h → prosek = 8.0 > 7.5
            // workingHoursToday=8.0 ih isključuje iz dodele u pipeline-u (uslov < 8 ne prolazi)
            // ali available=true pa ih accumulate uzima u obzir
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

    // ---- COUNT: zaostak naloga ----

    @Nested
    @DisplayName("COUNT — order backlog (> 5 orders in WAITING_RESOURCES)")
    class OrderBacklog {

        private DeliveryOrder waitingOrder(String id) {
            DeliveryOrder o = new DeliveryOrder();
            o.setId(id); o.setRouteId("R1"); o.setWeightKg(1000);
            o.setCargoType(CargoType.STANDARD); o.setDeliveryDeadlineMin(300);
            o.setPriority(OrderPriority.NORMAL); o.setStatus(OrderStatus.WAITING_RESOURCES);
            o.setDestination("Beograd");
            return o;
        }

        @Test
        @DisplayName("ORDER_BACKLOG alarm fires when more than 5 orders are waiting")
        void alarmFiresOver5Waiting() {
            // 6 naloga u WAITING_RESOURCES → 6 > 5 → alarm
            List<DeliveryOrder> orders = List.of(
                    waitingOrder("O1"), waitingOrder("O2"), waitingOrder("O3"),
                    waitingOrder("O4"), waitingOrder("O5"), waitingOrder("O6")
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(truck("T1", TruckStatus.BUSY)), List.of(), orders));

            assertThat(res.getAlarms())
                    .anyMatch(a -> a.getType() == AlarmType.ORDER_BACKLOG
                               && "FLEET".equals(a.getEntityId())
                               && a.getAffectedCount() == 6);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("order backlog") && m.contains("6"));
        }

        @Test
        @DisplayName("No alarm when exactly 5 orders are waiting — boundary: rule fires only when > 5")
        void noAlarmAt5Waiting() {
            List<DeliveryOrder> orders = List.of(
                    waitingOrder("O1"), waitingOrder("O2"), waitingOrder("O3"),
                    waitingOrder("O4"), waitingOrder("O5")
            );
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(truck("T1", TruckStatus.BUSY)), List.of(), orders));

            assertThat(res.getAlarms())
                    .noneMatch(a -> a.getType() == AlarmType.ORDER_BACKLOG);
        }
    }

    // ---- COUNT: nedostatak vozila ----

    @Nested
    @DisplayName("COUNT — fleet shortage (available trucks < 2 while orders are waiting)")
    class FleetShortage {

        private DeliveryOrder waitingOrder(String id) {
            DeliveryOrder o = new DeliveryOrder();
            o.setId(id); o.setRouteId("R1"); o.setWeightKg(1000);
            o.setCargoType(CargoType.STANDARD); o.setDeliveryDeadlineMin(300);
            o.setPriority(OrderPriority.NORMAL); o.setStatus(OrderStatus.WAITING_RESOURCES);
            o.setDestination("Beograd");
            return o;
        }

        @Test
        @DisplayName("FLEET_SHORTAGE alarm fires when only 1 truck available and orders are waiting")
        void alarmFiresWhenOnlyOneTruckAvailable() {
            // 1 slobodan kamion + 1 nalog u WAITING_RESOURCES → 1 < 2 → alarm
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(truck("T1", TruckStatus.AVAILABLE)),
                        List.of(), List.of(waitingOrder("O1"))));

            assertThat(res.getAlarms())
                    .anyMatch(a -> a.getType() == AlarmType.FLEET_SHORTAGE
                               && "FLEET".equals(a.getEntityId()));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("fleet shortage"));
        }

        @Test
        @DisplayName("No alarm when 2 or more trucks are available — boundary: rule fires only when < 2")
        void noAlarmWithTwoOrMoreTrucksAvailable() {
            // 2 slobodna kamiona + nalog na čekanju → 2 nije < 2 → nema alarma
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(truck("T1", TruckStatus.AVAILABLE), truck("T2", TruckStatus.AVAILABLE)),
                        List.of(), List.of(waitingOrder("O1"))));

            assertThat(res.getAlarms())
                    .noneMatch(a -> a.getType() == AlarmType.FLEET_SHORTAGE);
        }

        @Test
        @DisplayName("No alarm when no trucks available but also no waiting orders")
        void noAlarmWithNoWaitingOrders() {
            // 0 slobodnih kamiona, ali nema naloga u WAITING_RESOURCES → uslov nije ispunjen
            DispatchResult res = dispatchService.processDispatch(
                    req(List.of(truck("T1", TruckStatus.BUSY)),
                        List.of(), List.of()));

            assertThat(res.getAlarms())
                    .noneMatch(a -> a.getType() == AlarmType.FLEET_SHORTAGE);
        }
    }
}
