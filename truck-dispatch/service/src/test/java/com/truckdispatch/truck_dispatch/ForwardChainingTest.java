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

    // ---- pomoćne metode ----

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.URGENT))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 3000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 5000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
        }

        @Test
        @DisplayName("Winter reduces effective capacity — order UNFEASIBLE at 0.85 factor")
        void winterReducesCapacity() {
            // Kamion 6000kg × 0.85 = 5100kg efektivno; nalog zahteva 5500kg
            Truck  t = truck("T1", TruckType.LARGE, 6000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(-3, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 5500, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 90, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.HIGH))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.URGENT))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.HIGH))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        }

        @Test
        @DisplayName("Second RASHLADNI order gets WAITING_RESOURCES when frigo truck is busy, not UNFEASIBLE")
        void secondRashladniWaitsWhenFrigoBusy() {
            // Jedan frigo kamion, dva RASHLADNI naloga — prvi se dodeljuje, drugi mora da čeka, ne UNFEASIBLE
            Truck frigo = truck("TF", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, true, false, 80, 5);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DeliveryOrder o1 = order("O1", "R1", 1000, CargoType.REFRIGERATED, 300, OrderPriority.HIGH);
            DeliveryOrder o2 = order("O2", "R1", 1000, CargoType.REFRIGERATED, 300, OrderPriority.NORMAL);
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
            assertThat(unfeasible).isEqualTo(0); // frigo kamion POSTOJI, samo je zauzet
        }

        @Test
        @DisplayName("RASHLADNI order UNFEASIBLE when no refrigerated truck available")
        void rashladniUnfeasibleNoFrigo() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.REFRIGERATED, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
        }

        @Test
        @DisplayName("WAITING_RESOURCES when truck has capacity but is already busy at dispatch time")
        void waitingResourcesWhenTruckBusy() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.BUSY, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 3000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
            assertThat(find(res, "O1").getStatus()).isNotEqualTo(OrderStatus.UNFEASIBLE);
        }

        @Test
        @DisplayName("Weekend night: HIGH priority order dispatched — weekend rule overrides night restriction")
        void weekendNightAllowsHigh() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 22, 6, List.of(t), List.of(d), List.of(r),  // Subota 22h
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.HIGH))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        }

        @Test
        @DisplayName("ADR order on tunnel route triggers warning message")
        void adrTunnelRouteWarning() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, true, 80, 5);
            Driver d = driver("D1", true, 1, "CE", true, 1, 5);
            Route  r = route("R1", RoadType.REGIONAL, 100, 90, true); // hasTunnel=true
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.HAZARDOUS, 300, OrderPriority.URGENT))));

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
            // Jedan frigo, jedan bez frigo; frigo ima lošiji skor (dalje je)
            // ali mora biti izabran jer to zahteva tip tereta
            Truck tFrigo    = truck("TF", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, true,  false, 80, 20);
            Truck tNoFrigo  = truck("TN", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 3);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(tFrigo, tNoFrigo), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.REFRIGERATED, 300, OrderPriority.NORMAL))));

            DeliveryOrder o = find(res, "O1");
            assertThat(o.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(o.getAssignedTruckId()).isEqualTo("TF"); // mora biti frigo
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
                            List.of(order("O1", "R1", 1000, CargoType.HAZARDOUS, 300, OrderPriority.URGENT))));

            assertThat(find(res, "O1").getAssignedTruckId()).isEqualTo("TA");
            assertThat(res.getMessages()).anyMatch(m -> m.contains("TN") && m.contains("ADR"));
        }

        @Test
        @DisplayName("LARGE truck remains allowed on REGIONAL route during morning peak")
        void largeTruckAllowedRegionalMorningPeak() {
            // Tokom jutarnjeg špica, LARGE kamioni su zabranjeni samo u gradskim zonama.
            // REGIONAL ostaje dozvoljen, pa LARGE kamion sa boljim skorom treba biti dodeljen.
            Truck tLarge  = truck("TL", TruckType.LARGE,  10000, TruckStatus.AVAILABLE, false, false, 80, 3);
            Truck tMedium = truck("TM", TruckType.MEDIUM, 5000,  TruckStatus.AVAILABLE, false, false, 80, 20);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.REGIONAL, 80, 90, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 7, 3, List.of(tLarge, tMedium), List.of(d1, d2), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getAssignedTruckId()).isEqualTo("TL");
            assertThat(res.getMessages()).noneMatch(m -> m.contains("TL") && m.contains("excluded"));
        }

        @Test
        @DisplayName("minTruckType: SMALL truck excluded when order requires MEDIUM minimum")
        void minTruckTypeExcludesSmall() {
            Truck tSmall  = truck("TS", TruckType.SMALL,  2000, TruckStatus.AVAILABLE, false, false, 80, 3);
            Truck tMedium = truck("TM", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DeliveryOrder o = order("O1", "R1", 500, CargoType.STANDARD, 300, OrderPriority.NORMAL);
            o.setMinTruckType(TruckType.MEDIUM);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(tSmall, tMedium), List.of(d), List.of(r), List.of(o)));

            assertThat(find(res, "O1").getAssignedTruckId()).isEqualTo("TM");
            assertThat(res.getMessages()).anyMatch(m -> m.contains("TS") && m.contains("minTruckType"));
        }

        @Test
        @DisplayName("MEDIUM truck excluded from CITY road type")
        void mediumTruckNotAllowedOnCityRoute() {
            // Samo SMALL je dozvoljen na CITY; MEDIUM nije → nalog u WAITING_RESOURCES ako je dostupan samo MEDIUM
            Truck tMedium = truck("TM", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.CITY, 20, 50, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 10, 3, List.of(tMedium), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 1000, CargoType.HAZARDOUS, 300, OrderPriority.URGENT))));

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
                            List.of(order("O1", "R1", 1000, CargoType.HAZARDOUS, 300, OrderPriority.URGENT))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
        }

        @Test
        @DisplayName("Driver with 8+ working hours is excluded")
        void driverExcludedTooManyHours() {
            Truck  t      = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver dTired = driver("DT", true, 8, "CE", false, 2, 5); // workingHours == 8 (nije < 8)
            Driver dFresh = driver("DF", true, 1, "CE", false, 1, 5);
            Route  r      = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(dTired, dFresh), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getAssignedDriverId()).isEqualTo("DC");
        }

        @Test
        @DisplayName("Night mode: driver with fatigue=6 excluded (night max is 5)")
        void nightModeFatigueConstraint() {
            Truck  t      = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver dTired = driver("DT", true, 1, "CE", false, 6, 5); // fatigue=6 -- dozvoljen inače, blokiran noću
            Driver dFresh = driver("DF", true, 1, "CE", false, 4, 5); // fatigue=4 -- prolazi noćni uslov
            Route  r      = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 22, 3, List.of(t), List.of(dTired, dFresh), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.URGENT))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(find(res, "O1").getAssignedDriverId()).isEqualTo("DF");
        }

        @Test
        @DisplayName("Order gets WAITING_RESOURCES when no driver qualifies")
        void noValidPairWhenNoDriverQualifies() {
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            // Vozač sa 8 radnih sati — isključen
            Driver d = driver("D1", true, 8, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

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
            // Oba kamiona identična osim udaljenosti; bliži dobija +25 i treba da pobedi
            Truck tClose = truck("TC", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Truck tFar   = truck("TF", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 25);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(tClose, tFar), List.of(d1, d2), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getAssignedTruckId()).isEqualTo("TC");
        }

        @Test
        @DisplayName("High utilization bonus (+15): truck loaded >90% of capacity wins")
        void highUtilizationBonus() {
            // tSmall kapacitet 1100; nalog je 1000kg → 1000/1100 = 91% → bonus
            // tLarge kapacitet 5000; nalog je 1000kg → 20% → nema bonusa; i dalje je
            Truck tSmall = truck("TS", TruckType.MEDIUM, 1100, TruckStatus.AVAILABLE, false, false, 80, 5);
            Truck tLarge = truck("TL", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(tSmall, tLarge), List.of(d1, d2), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            // Kamion sa malo goriva: 100 + 25(blizina) - 25(gorivo) = 100
            // Kamion sa dobrim gorivom: 100 + 0(blizina) = 100 → ista osnova, ali poruka o malusu za gorivo
            assertThat(res.getMessages()).anyMatch(m -> m.contains("TL") && m.contains("fuel"));
        }

        @Test
        @DisplayName("LOMLJIVO malus (-20): fragile cargo on fast route reduces score")
        void lomljivoMalusOnFastRoute() {
            // udaljenost=15km (nema bonusa za blizinu), fatigue=4 (nema bonusa/malusa za umor)
            // LOMLJIVO + HIGHWAY maxSpeedKmh=120 > 80 → skor = 100 + 10 (MEDIUM+HIGHWAY šablon) - 20 = 90
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 15);
            Driver d = driver("D1", true, 1, "CE", false, 4, 5);
            Route  r = new Route();
            r.setId("R1"); r.setRoadType(RoadType.HIGHWAY); r.setDistanceKm(100);
            r.setMaxSpeedKmh(120); r.setHasTunnel(false);
            r.setEstimatedTimeHours(100.0 / 120); r.setMaxCapacityKg(24000);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.FRAGILE, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(res.getMessages()).anyMatch(m -> m.contains("score=90"));
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
                            List.of(order("O1", "R1", 500, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            // SMALL+CITY dobija šablon +15; MEDIUM isključen iz CITY → samo SMALL prolazi
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
                            List.of(order("O1", "R1", 1000, CargoType.HAZARDOUS, 300, OrderPriority.URGENT))));

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
            DeliveryOrder oUrgent = order("OU", "R1", 2000, CargoType.STANDARD, 300, OrderPriority.URGENT);
            DeliveryOrder oHigh   = order("OH", "R1", 2000, CargoType.STANDARD, 300, OrderPriority.HIGH);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r), List.of(oUrgent, oHigh)));

            assertThat(find(res, "OU").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            // Samo jedan kamion/vozač — drugi nalog mora da čeka
            assertThat(find(res, "OH").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
        }

        @Test
        @DisplayName("Double-assignment prevented: two orders competing for same truck")
        void doubleAssignmentPrevented() {
            // Jedan kamion, dva naloga — samo jedan može biti dodeljen
            Truck  t  = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d1 = driver("D1", true, 1, "CE", false, 1, 5);
            Driver d2 = driver("D2", true, 1, "CE", false, 1, 5);
            Route  r  = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DeliveryOrder o1 = order("O1", "R1", 2000, CargoType.STANDARD, 300, OrderPriority.HIGH);
            DeliveryOrder o2 = order("O2", "R1", 2000, CargoType.STANDARD, 300, OrderPriority.HIGH);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d1, d2), List.of(r), List.of(o1, o2)));

            long assigned = res.getProcessedOrders().stream()
                    .filter(o -> o.getStatus() == OrderStatus.ASSIGNED)
                    .count();
            long waiting = res.getProcessedOrders().stream()
                    .filter(o -> o.getStatus() == OrderStatus.WAITING_RESOURCES)
                    .count();
            // Tačno JEDAN dodeljen, JEDAN na čekanju — kamion ne može opslužiti oba
            assertThat(assigned).isEqualTo(1);
            assertThat(waiting).isEqualTo(1);
            // Verifikacija da je dodeljeni kamion T1 (jedini kamion)
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
            DeliveryOrder o1 = order("O1", "R1", 2000, CargoType.STANDARD, 300, OrderPriority.HIGH);
            DeliveryOrder o2 = order("O2", "R1", 2000, CargoType.STANDARD, 300, OrderPriority.HIGH);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t1, t2), List.of(d1, d2), List.of(r), List.of(o1, o2)));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(find(res, "O2").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            // Svaki nalog mora imati drugi kamion
            assertThat(find(res, "O1").getAssignedTruckId())
                    .isNotEqualTo(find(res, "O2").getAssignedTruckId());
        }

        @Test
        @DisplayName("PostValidation: refrigeration service warning when >30 days")
        void refrigerationServiceWarning() {
            Truck t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, true, false, 80, 5);
            t.setDaysSinceRefrigerationService(35); // > 30 → upozorenje
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.REFRIGERATED, 300, OrderPriority.NORMAL))));

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
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(res.getMessages()).anyMatch(m -> m.contains("fuel") || m.contains("goriv"));
        }

        @Test
        @DisplayName("Resources consumed by priority order leave secondary order WAITING")
        void resourcesConsumedLeaveSecondWaiting() {
            // Jedan kamion+vozač; URGENT nalog zauzima kamion; ADR nalog ostaje bez kamiona
            Truck  t      = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, true, 80, 5);
            Driver dAdr   = driver("DA", true, 1, "CE", true, 1, 5);
            Route  r      = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DeliveryOrder oUrgent = order("OU", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.URGENT);
            DeliveryOrder oAdr    = order("OA", "R1", 1000, CargoType.HAZARDOUS, 300, OrderPriority.URGENT);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(dAdr), List.of(r), List.of(oUrgent, oAdr)));

            long assigned = res.getProcessedOrders().stream()
                    .filter(o -> o.getStatus() == OrderStatus.ASSIGNED).count();
            assertThat(assigned).isEqualTo(1);
        }
    }

    // Pravila generisana iz šablona

    @Nested
    @DisplayName("Template-generated rules")
    class TemplateRules {

        @Test
        @DisplayName("order-priority template: PriorityWaiting fires for HIGH order in WAITING_RESOURCES")
        void priorityWaitingHighOrder() {
            // Noćni režim + HIGH prioritet → nalog ide u WAITING_RESOURCES
            // PriorityWaiting_HIGH pravilo šablona mora dodati [WARN] poruku upozorenja
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 23, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.HIGH))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("[WARN]") && m.contains("O1") && m.contains("waiting for resources"));
        }

        @Test
        @DisplayName("operational-mode template: ContextDelayAlert fires for evening-peak order exceeding delay threshold")
        void contextDelayAlertEveningPeak() {
            // Večernji špic (hour=18) → DispatchContext sa delayThresholdMin=40
            // Unapred podešen IN_PROGRESS nalog sa delayMin=45 > 40 → okida se upozorenje
            DeliveryOrder o = new DeliveryOrder();
            o.setId("O1"); o.setRouteId("R1"); o.setWeightKg(1000);
            o.setCargoType(CargoType.STANDARD); o.setDeliveryDeadlineMin(300);
            o.setPriority(OrderPriority.NORMAL); o.setStatus(OrderStatus.IN_PROGRESS);
            o.setDestination("Beograd"); o.setDelayMin(45);
            o.setAssignedTruckId("T1");

            Truck t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.BUSY, false, false, 80, 5);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 18, 3, List.of(t), List.of(), List.of(), List.of(o)));

            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("[WARN]") && m.contains("Evening Peak")
                               && m.contains("O1") && m.contains("45"));
        }
    }
}
