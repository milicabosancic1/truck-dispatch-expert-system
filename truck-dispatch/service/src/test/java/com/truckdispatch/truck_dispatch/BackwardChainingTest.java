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
@DisplayName("Backward Chaining — queries and diagnosis rules")
class BackwardChainingTest {

    @Autowired
    private DispatchService dispatchService;

    // ---- builders ----

    private Truck truck(String id, TruckType type, double cap, TruckStatus status,
                        boolean frigo, boolean adr, double fuel, double dist) {
        Truck t = new Truck();
        t.setId(id); t.setType(type); t.setMaxCapacityKg(cap); t.setStatus(status);
        t.setHasRefrigerationUnit(frigo); t.setHasAdrEquipment(adr);
        t.setFuelPercent(fuel); t.setDistanceToOriginKm(dist);
        t.setDaysSinceRefrigerationService(5); t.setLocation("NS");
        return t;
    }

    private Driver driver(String id, boolean avail, double hours, String lic,
                          boolean adr, int fatigue, int exp) {
        Driver d = new Driver();
        d.setId(id); d.setAvailable(avail); d.setWorkingHoursToday(hours);
        d.setLicense(lic); d.setHasAdrLicense(adr);
        d.setFatigueLevel(fatigue); d.setYearsOfExperience(exp);
        return d;
    }

    private Route route(String id, RoadType type, double km, double speed, boolean tunnel) {
        Route r = new Route();
        r.setId(id); r.setRoadType(type); r.setDistanceKm(km);
        r.setMaxSpeedKmh(speed); r.setHasTunnel(tunnel);
        r.setEstimatedTimeHours(km / speed); r.setMaxCapacityKg(24000);
        return r;
    }

    private DeliveryOrder order(String id, String routeId, double kg,
                                CargoType cargo, int deadline, OrderPriority priority) {
        DeliveryOrder o = new DeliveryOrder();
        o.setId(id); o.setRouteId(routeId); o.setWeightKg(kg);
        o.setCargoType(cargo); o.setDeliveryDeadlineMin(deadline);
        o.setPriority(priority); o.setStatus(OrderStatus.NEW);
        o.setDestination("Beograd");
        return o;
    }

    private DispatchRequest req(double temp, int hour, int day,
                                List<Truck> trucks, List<Driver> drivers,
                                List<Route> routes, List<DeliveryOrder> orders) {
        DispatchRequest r = new DispatchRequest();
        r.setTemperature(temp); r.setHour(hour); r.setDayOfWeek(day);
        r.setTrucks(trucks); r.setDrivers(drivers);
        r.setRoutes(routes); r.setOrders(orders);
        return r;
    }

    private DeliveryOrder find(DispatchResult res, String id) {
        return res.getProcessedOrders().stream()
                .filter(o -> o.getId().equals(id)).findFirst().orElseThrow();
    }

    // ---- Query 1: jeUzrokNedodele ----

    @Nested
    @DisplayName("jeUzrokNedodele — recursive diagnosis chain")
    class JeUzrokNedodele {

        @Test
        @DisplayName("BC_Diagnosis_Winter fires for UNFEASIBLE order — resolves 3-hop chain")
        void diagnosisWinterFires() {
            // ZimaRedukujNosivost→NemaDovoljneNosivosti→NemaSlobodnogKamiona→NalogNedodeljen (3 hops)
            // Truck capacity 1000kg, order needs 5000kg → UNFEASIBLE
            Truck  t = truck("T1", TruckType.SMALL, 1000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 5000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS: winter conditions"));
        }

        @Test
        @DisplayName("BC_Diagnosis_AllCauses reports root causes at all chain depths for UNFEASIBLE order")
        void diagnosisAllCausesRecursiveChains() {
            // Every node that transitively reaches NalogNedodeljen is reported.
            // This verifies the recursive query resolves chains at depth 1, 2 and 3.
            Truck  t = truck("T1", TruckType.SMALL, 1000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 5000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            // 3-hop leaf nodes (recursion required):
            assertThat(res.getMessages()).anyMatch(m -> m.contains("DIAGNOSIS cause: ZimaRedukujNosivost"));
            assertThat(res.getMessages()).anyMatch(m -> m.contains("DIAGNOSIS cause: TeretPremasen"));
            // 2-hop leaf nodes:
            assertThat(res.getMessages()).anyMatch(m -> m.contains("DIAGNOSIS cause: SviVozaciUmorni"));
            assertThat(res.getMessages()).anyMatch(m -> m.contains("DIAGNOSIS cause: GradskiKamionVeliki"));
            // 1-hop intermediate nodes also reported:
            assertThat(res.getMessages()).anyMatch(m -> m.contains("DIAGNOSIS cause: NemaSlobodnogKamiona"));
        }

        @Test
        @DisplayName("BC_Diagnosis fires for WAITING_RESOURCES order — not only UNFEASIBLE")
        void diagnosisFiresForWaitingResources() {
            // Night mode postpones HIGH priority order → WAITING_RESOURCES
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 23, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.HIGH))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS"));
        }

        @Test
        @DisplayName("BC_Diagnosis does NOT fire when order is ASSIGNED")
        void noDiagnosisForAssignedOrder() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(res.getMessages()).noneMatch(m -> m.contains("DIAGNOSIS"));
        }
    }

    // ---- Query 2: pripadaKategorijiNaloga ----

    @Nested
    @DisplayName("pripadaKategorijiNaloga — order group hierarchy")
    class PripadaKategorijiNaloga {

        @Test
        @DisplayName("RASHLADNI triggers BC_SpecialOrderCheck — direct NalogSpecijalni membership")
        void rashladniIsSpecial() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, true, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.REFRIGERATED, 300, OrderPriority.NORMAL))));

            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("is SPECIAL") && m.contains("REFRIGERATED"));
        }

        @Test
        @DisplayName("OPASNA_ROBA triggers BC_SpecialOrderCheck — direct NalogSpecijalni membership")
        void opasnaRobaIsSpecial() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, true, 80, 5);
            Driver d = driver("D1", true, 1, "CE", true, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.HAZARDOUS, 300, OrderPriority.URGENT))));

            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("is SPECIAL") && m.contains("HAZARDOUS"));
        }

        @Test
        @DisplayName("STANDARDNO does NOT trigger BC_SpecialOrderCheck — belongs to NalogKomercijalni, not NalogSpecijalni")
        void standardnoIsNotSpecial() {
            // STANDARDNO→DostavaStandardna→NalogKomercijalni→NalogOpsti — never reaches NalogSpecijalni
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(res.getMessages()).noneMatch(m -> m.contains("is SPECIAL"));
        }

        @Test
        @DisplayName("RASHLADNI UNFEASIBLE does NOT trigger BC_SpecialOrderCheck — order never reaches VALID")
        void rashladniUnfeasibleSkipsSpecialCheck() {
            // No frigo truck → RASHLADNI goes UNFEASIBLE, never hits VALID status
            // BC_SpecialOrderCheck requires status == VALID, so it must not fire here
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.REFRIGERATED, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
            assertThat(res.getMessages()).noneMatch(m -> m.contains("is SPECIAL"));
        }
    }
}
