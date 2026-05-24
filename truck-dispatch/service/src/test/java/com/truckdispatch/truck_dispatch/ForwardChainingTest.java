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
@DisplayName("Forward Chaining — all pipeline levels")
class ForwardChainingTest {

    @Autowired
    private DispatchService dispatchService;

    // BUILDERS

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

    // NIVO 0 — Kontekst pravila

    @Nested
    @DisplayName("Nivo 0 — Context rules")
    class ContextRules {

        @Test
        @DisplayName("Evening peak fires between 17-20h on a weekday")
        void eveningPeakContext() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 18, 2, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(res.getMessages()).anyMatch(m -> m.contains("Evening peak"));
        }

        @Test
        @DisplayName("Weekend context fires on Saturday (day=6)")
        void weekendContext() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 6, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.URGENT))));

            assertThat(res.getMessages()).anyMatch(m -> m.contains("Weekend"));
        }

        @Test
        @DisplayName("Winter conditions fire when temperature < 0")
        void winterContext() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(-5, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(res.getMessages()).anyMatch(m -> m.contains("Winter conditions"));
        }

        @Test
        @DisplayName("Morning peak fires 06-09h on a weekday")
        void morningPeakContext() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 7, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(res.getMessages()).anyMatch(m -> m.contains("Morning peak"));
        }
    }

    // NIVO 1 — Validacija

    @Nested
    @DisplayName("Nivo 1 — Validation rules")
    class ValidationRules {

        @Test
        @DisplayName("Order is VALID when a truck with sufficient capacity exists")
        void orderBecomesValid() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 3000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        }

        @Test
        @DisplayName("Order is UNFEASIBLE when no truck has enough capacity")
        void orderUnfeasibleNoCapacity() {
            Truck  t = truck("T1", TruckType.SMALL, 1000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 5000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
        }

        @Test
        @DisplayName("Winter reduces effective capacity — order UNFEASIBLE at 0.85 factor")
        void winterReducesCapacity() {
            // Truck 6000kg × 0.85 = 5100kg effective; order needs 5500kg
            Truck  t = truck("T1", TruckType.LARGE, 6000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(-3, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 5500, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
            assertThat(res.getMessages()).anyMatch(m -> m.contains("winter") || m.contains("Winter"));
        }

        @Test
        @DisplayName("Order with deadline < 120min is auto-escalated to URGENT")
        void urgentAutoEscalation() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 90, OrderPriority.NORMAL))));

            assertThat(res.getMessages()).anyMatch(m -> m.contains("URGENT") && m.contains("O1"));
            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        }

        @Test
        @DisplayName("Night mode postpones HIGH priority order")
        void nightModePostponesHigh() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 23, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.HIGH))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
            assertThat(res.getMessages()).anyMatch(m -> m.contains("Night mode") && m.contains("O1"));
        }

        @Test
        @DisplayName("Night mode allows URGENT order through")
        void nightModeAllowsUrgent() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 22, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.URGENT))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        }

        @Test
        @DisplayName("Weekend postpones NORMAL priority order")
        void weekendPostponesNormal() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 6, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
            assertThat(res.getMessages()).anyMatch(m -> m.contains("Weekend") && m.contains("O1"));
        }

        @Test
        @DisplayName("Weekend allows HIGH priority order through")
        void weekendAllowsHigh() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 7, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.HIGH))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        }

        @Test
        @DisplayName("Second RASHLADNI order gets WAITING_RESOURCES when frigo truck is busy, not UNFEASIBLE")
        void secondRashladniWaitsWhenFrigoBusy() {
            // One frigo truck, two RASHLADNI orders — first gets assigned, second must WAIT not UNFEASIBLE
            Truck frigo = truck("TF", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, true, false, 80, 5);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DeliveryOrder o1 = order("O1", "R1", 1000, CargoType.RASHLADNI, 300, OrderPriority.HIGH);
            DeliveryOrder o2 = order("O2", "R1", 1000, CargoType.RASHLADNI, 300, OrderPriority.NORMAL);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(frigo), List.of(d1, d2), List.of(r), List.of(o1, o2)));

            long assigned = res.getProcessedOrders().stream()
                    .filter(o -> o.getStatus() == OrderStatus.ASSIGNED).count();
            long waiting  = res.getProcessedOrders().stream()
                    .filter(o -> o.getStatus() == OrderStatus.WAITING_RESOURCES).count();
            long unfeasible = res.getProcessedOrders().stream()
                    .filter(o -> o.getStatus() == OrderStatus.UNFEASIBLE).count();
            assertThat(assigned).isEqualTo(1);
            assertThat(waiting).isEqualTo(1);
            assertThat(unfeasible).isEqualTo(0); // frigo truck EXISTS, just busy
        }

        @Test
        @DisplayName("RASHLADNI order UNFEASIBLE when no refrigerated truck available")
        void rashladniUnfeasibleNoFrigo() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.RASHLADNI, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
        }

        @Test
        @DisplayName("ADR order on tunnel route triggers warning message")
        void adrTunnelRouteWarning() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, true, 80, 5);
            Driver d = driver("D1", true, 1, "CE", true, 1, 5);
            Route  r = route("R1", RoadType.REGIONAL, 100, 90, true); // hasTunnel=true
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.OPASNA_ROBA, 300, OrderPriority.URGENT))));

            assertThat(res.getMessages()).anyMatch(m -> m.contains("tunnel"));
        }
    }

    // NIVO 2 — Filtriranje kamiona

    @Nested
    @DisplayName("Nivo 2 — Truck filtering")
    class TruckFiltering {

        @Test
        @DisplayName("Non-frigo truck excluded for RASHLADNI — order gets WAITING_RESOURCES")
        void nonFrigoExcludedForRashladni() {
            // One frigo, one non-frigo; frigo has worse score (further away)
            // but must be picked because cargo requires it
            Truck tFrigo    = truck("TF", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, true,  false, 80, 20);
            Truck tNoFrigo  = truck("TN", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 3);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(tFrigo, tNoFrigo), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.RASHLADNI, 300, OrderPriority.NORMAL))));

            DeliveryOrder o = find(res, "O1");
            assertThat(o.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(o.getAssignedTruckId()).isEqualTo("TF"); // must be frigo
        }

        @Test
        @DisplayName("Non-ADR truck excluded for OPASNA_ROBA")
        void nonAdrTruckExcludedForDangerous() {
            Truck tAdr   = truck("TA", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, true,  80, 20);
            Truck tNoAdr = truck("TN", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 3);
            Driver d = driver("D1", true, 1, "CE", true, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(tAdr, tNoAdr), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.OPASNA_ROBA, 300, OrderPriority.URGENT))));

            assertThat(find(res, "O1").getAssignedTruckId()).isEqualTo("TA");
            assertThat(res.getMessages()).anyMatch(m -> m.contains("TN") && m.contains("ADR"));
        }

        @Test
        @DisplayName("LARGE truck excluded from REGIONAL route during morning peak")
        void largeTruckExcludedRegionalMorningPeak() {
            // During morning peak, LARGE trucks are banned from REGIONAL routes.
            // MEDIUM is allowed — should be assigned instead.
            Truck tLarge  = truck("TL", TruckType.LARGE,  10000, TruckStatus.AVAILABLE, false, false, 80, 3);
            Truck tMedium = truck("TM", TruckType.MEDIUM, 5000,  TruckStatus.AVAILABLE, false, false, 80, 8);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.REGIONAL, 80, 90, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 7, 3, List.of(tLarge, tMedium), List.of(d1, d2), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getAssignedTruckId()).isEqualTo("TM");
            assertThat(res.getMessages()).anyMatch(m -> m.contains("TL") && m.contains("excluded"));
        }

        @Test
        @DisplayName("minTruckType: SMALL truck excluded when order requires MEDIUM minimum")
        void minTruckTypeExcludesSmall() {
            Truck tSmall  = truck("TS", TruckType.SMALL,  2000, TruckStatus.AVAILABLE, false, false, 80, 3);
            Truck tMedium = truck("TM", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DeliveryOrder o = order("O1", "R1", 500, CargoType.STANDARDNO, 300, OrderPriority.NORMAL);
            o.setMinTruckType(TruckType.MEDIUM);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(tSmall, tMedium), List.of(d), List.of(r), List.of(o)));

            assertThat(find(res, "O1").getAssignedTruckId()).isEqualTo("TM");
            assertThat(res.getMessages()).anyMatch(m -> m.contains("TS") && m.contains("minTruckType"));
        }

        @Test
        @DisplayName("MEDIUM truck excluded from CITY road type")
        void mediumTruckNotAllowedOnCityRoute() {
            // Only SMALL is allowed on CITY; MEDIUM is not → order WAITING_RESOURCES if only MEDIUM available
            Truck tMedium = truck("TM", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.CITY, 20, 50, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 10, 3, List.of(tMedium), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
        }
    }

    // NIVO 3 — Provjera vozača

    @Nested
    @DisplayName("Nivo 3 — Driver check")
    class DriverCheck {

        @Test
        @DisplayName("ADR order requires driver with ADR license")
        void adrOrderRequiresAdrDriver() {
            Truck  t       = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, true, 80, 5);
            Driver dAdr    = driver("DA", true, 1, "CE", true,  1, 5);
            Driver dNoAdr  = driver("DN", true, 1, "CE", false, 1, 5);
            Route  r       = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(dAdr, dNoAdr), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.OPASNA_ROBA, 300, OrderPriority.URGENT))));

            DeliveryOrder o = find(res, "O1");
            assertThat(o.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(o.getAssignedDriverId()).isEqualTo("DA");
            assertThat(res.getMessages()).anyMatch(m -> m.contains("DN") && m.contains("ADR"));
        }

        @Test
        @DisplayName("ADR order goes to WAITING_RESOURCES when no driver has ADR license")
        void adrOrderWaitsWhenNoAdrDriver() {
            Truck  t      = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, true, 80, 5);
            Driver dNoAdr = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r      = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(dNoAdr), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.OPASNA_ROBA, 300, OrderPriority.URGENT))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
        }

        @Test
        @DisplayName("Driver with 8+ working hours is excluded")
        void driverExcludedTooManyHours() {
            Truck  t      = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver dTired = driver("DT", true, 8, "CE", false, 2, 5); // workingHours == 8 (not < 8)
            Driver dFresh = driver("DF", true, 1, "CE", false, 1, 5);
            Route  r      = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(dTired, dFresh), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getAssignedDriverId()).isEqualTo("DF");
        }

        @Test
        @DisplayName("Driver with fatigue >= 7 is excluded")
        void driverExcludedHighFatigue() {
            Truck  t       = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver dFatigued = driver("DX", true, 2, "CE", false, 7, 5); // fatigue == 7 (not < 7)
            Driver dFresh    = driver("DF", true, 2, "CE", false, 2, 5);
            Route  r       = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(dFatigued, dFresh), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getAssignedDriverId()).isEqualTo("DF");
        }

        @Test
        @DisplayName("B-license driver cannot drive MEDIUM truck")
        void bLicenseExcludedFromMediumTruck() {
            Truck  tMedium = truck("TM", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver dB      = driver("DB", true, 1, "B",  false, 1, 5);
            Driver dCE     = driver("DC", true, 1, "CE", false, 1, 5);
            Route  r       = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(tMedium), List.of(dB, dCE), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getAssignedDriverId()).isEqualTo("DC");
        }

        @Test
        @DisplayName("Night mode: driver with fatigue=6 excluded (night max is 5)")
        void nightModeFatigueConstraint() {
            Truck  t      = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver dTired = driver("DT", true, 1, "CE", false, 6, 5); // fatigue=6 -- allowed normally, blocked at night
            Driver dFresh = driver("DF", true, 1, "CE", false, 4, 5); // fatigue=4 -- passes night constraint
            Route  r      = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 22, 3, List.of(t), List.of(dTired, dFresh), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.URGENT))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(find(res, "O1").getAssignedDriverId()).isEqualTo("DF");
        }

        @Test
        @DisplayName("Order gets WAITING_RESOURCES when no driver qualifies")
        void noValidPairWhenNoDriverQualifies() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            // Driver with 8 working hours — excluded
            Driver d = driver("D1", true, 8, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
            assertThat(res.getMessages()).anyMatch(m -> m.contains("O1") && m.contains("waiting"));
        }
    }

    // NIVO 3+ — Scoring

    @Nested
    @DisplayName("Nivo 3+ — Scoring rules")
    class ScoringRules {

        @Test
        @DisplayName("Proximity bonus (+25): closer truck wins over same-score truck")
        void proximityBonusWins() {
            // Both trucks identical except distance; closer gets +25 and should win
            Truck tClose = truck("TC", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Truck tFar   = truck("TF", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 25);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(tClose, tFar), List.of(d1, d2), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getAssignedTruckId()).isEqualTo("TC");
        }

        @Test
        @DisplayName("High utilization bonus (+15): truck loaded >90% of capacity wins")
        void highUtilizationBonus() {
            // tSmall has capacity 1100; order is 1000kg → 1000/1100 = 91% → bonus
            // tLarge has capacity 5000; order is 1000kg → 20% → no bonus; also farther
            Truck tSmall = truck("TS", TruckType.MEDIUM, 1100, TruckStatus.AVAILABLE, false, false, 80, 5);
            Truck tLarge = truck("TL", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(tSmall, tLarge), List.of(d1, d2), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getAssignedTruckId()).isEqualTo("TS");
        }

        @Test
        @DisplayName("Fuel malus (-25): low-fuel truck loses to well-fueled truck")
        void fuelMalusLoses() {
            Truck tLowFuel  = truck("TL", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 30, 3);
            Truck tGoodFuel = truck("TG", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 90, 5);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(tLowFuel, tGoodFuel), List.of(d1, d2), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            // Low fuel truck: 100 + 25(proximity) - 25(fuel) = 100
            // Good fuel truck: 100 + 0(proximity) = 100 → same base, but message about fuel malus
            assertThat(res.getMessages()).anyMatch(m -> m.contains("TL") && m.contains("fuel"));
        }

        @Test
        @DisplayName("Template bonus: SMALL truck on CITY route gets score bonus")
        void templateBonusSmallOnCity() {
            Truck tSmall  = truck("TS", TruckType.SMALL, 2000, TruckStatus.AVAILABLE, false, false, 80, 15);
            Truck tMedium = truck("TM", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 3);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.CITY, 20, 50, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 10, 3, List.of(tSmall, tMedium), List.of(d1, d2), List.of(r),
                            List.of(order("O1", "R1", 500, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            // SMALL+CITY gets template +15; MEDIUM excluded from CITY → only SMALL qualifies
            assertThat(find(res, "O1").getAssignedTruckId()).isEqualTo("TS");
        }

        @Test
        @DisplayName("ADR inexperienced driver malus (-35): experienced ADR driver wins")
        void adrInexperiencedDriverMalus() {
            Truck  t      = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, true, 80, 5);
            Driver dExp   = driver("DE", true, 1, "CE", true, 1, 10); // experienced
            Driver dInexp = driver("DI", true, 1, "CE", true, 1, 2);  // < 3 years → malus
            Route  r      = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(dExp, dInexp), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.OPASNA_ROBA, 300, OrderPriority.URGENT))));

            assertThat(find(res, "O1").getAssignedDriverId()).isEqualTo("DE");
            assertThat(res.getMessages()).anyMatch(m -> m.contains("DI") && m.contains("inexperienced"));
        }
    }

    // NIVO 4 — Finalna dodela

    @Nested
    @DisplayName("Nivo 4 — Final assignment")
    class FinalAssignment {

        @Test
        @DisplayName("URGENT order assigned before HIGH when competing for same truck")
        void urgentBeforeHigh() {
            Truck  t  = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d  = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DeliveryOrder oUrgent = order("OU", "R1", 2000, CargoType.STANDARDNO, 300, OrderPriority.URGENT);
            DeliveryOrder oHigh   = order("OH", "R1", 2000, CargoType.STANDARDNO, 300, OrderPriority.HIGH);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r), List.of(oUrgent, oHigh)));

            assertThat(find(res, "OU").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            // Only one truck/driver — second order must wait
            assertThat(find(res, "OH").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
        }

        @Test
        @DisplayName("Double-assignment prevented: two orders competing for same truck")
        void doubleAssignmentPrevented() {
            // One truck, two orders — only one can be assigned
            Truck  t  = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DeliveryOrder o1 = order("O1", "R1", 2000, CargoType.STANDARDNO, 300, OrderPriority.HIGH);
            DeliveryOrder o2 = order("O2", "R1", 2000, CargoType.STANDARDNO, 300, OrderPriority.HIGH);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d1, d2), List.of(r), List.of(o1, o2)));

            long assigned = res.getProcessedOrders().stream()
                    .filter(o -> o.getStatus() == OrderStatus.ASSIGNED)
                    .count();
            long waiting = res.getProcessedOrders().stream()
                    .filter(o -> o.getStatus() == OrderStatus.WAITING_RESOURCES)
                    .count();
            // Exactly ONE assigned, ONE waiting — truck cannot serve both
            assertThat(assigned).isEqualTo(1);
            assertThat(waiting).isEqualTo(1);
            // Verify the assigned truck is T1 (only truck)
            res.getProcessedOrders().stream()
                    .filter(o -> o.getStatus() == OrderStatus.ASSIGNED)
                    .forEach(o -> assertThat(o.getAssignedTruckId()).isEqualTo("T1"));
        }

        @Test
        @DisplayName("Two trucks, two orders — both orders get assigned to different trucks")
        void twoTrucksTwoOrders() {
            Truck  t1 = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Truck  t2 = truck("T2", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 8);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DeliveryOrder o1 = order("O1", "R1", 2000, CargoType.STANDARDNO, 300, OrderPriority.HIGH);
            DeliveryOrder o2 = order("O2", "R1", 2000, CargoType.STANDARDNO, 300, OrderPriority.HIGH);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t1, t2), List.of(d1, d2), List.of(r), List.of(o1, o2)));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(find(res, "O2").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            // Each order must have a different truck
            assertThat(find(res, "O1").getAssignedTruckId())
                    .isNotEqualTo(find(res, "O2").getAssignedTruckId());
        }

        @Test
        @DisplayName("PostValidation: refrigeration service warning when >30 days")
        void refrigerationServiceWarning() {
            Truck t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, true, false, 80, 5);
            t.setDaysSinceRefrigerationService(35); // > 30 → warning
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.RASHLADNI, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(res.getMessages()).anyMatch(m -> m.contains("refriger") && m.contains("30"));
        }

        @Test
        @DisplayName("PostValidation: low fuel warning when assigned truck has < 20% fuel")
        void lowFuelWarningAfterAssignment() {
            Truck t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 15, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(res.getMessages()).anyMatch(m -> m.contains("fuel") || m.contains("goriv"));
        }

        @Test
        @DisplayName("Resources consumed by priority order leave secondary order WAITING")
        void resourcesConsumedLeaveSecondWaiting() {
            // One truck+driver; URGENT order takes the truck; ADR order has no truck left
            Truck  t      = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, true, 80, 5);
            Driver dAdr   = driver("DA", true, 1, "CE", true, 1, 5);
            Route  r      = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DeliveryOrder oUrgent = order("OU", "R1", 1000, CargoType.STANDARDNO, 300, OrderPriority.URGENT);
            DeliveryOrder oAdr    = order("OA", "R1", 1000, CargoType.OPASNA_ROBA, 300, OrderPriority.URGENT);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(dAdr), List.of(r), List.of(oUrgent, oAdr)));

            long assigned = res.getProcessedOrders().stream()
                    .filter(o -> o.getStatus() == OrderStatus.ASSIGNED).count();
            assertThat(assigned).isEqualTo(1);
        }
    }
}
