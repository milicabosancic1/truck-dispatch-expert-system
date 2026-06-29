package com.truckdispatch.truck_dispatch;

import com.truckdispatch.truck_dispatch.dto.DispatchRequest;
import com.truckdispatch.truck_dispatch.dto.DispatchResult;
import com.truckdispatch.truck_dispatch.model.*;
import com.truckdispatch.truck_dispatch.service.DispatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TruckDispatchApplicationTests {

    @Autowired
    private DispatchService dispatchService;

    // -------------------------------------------------------
    // Pomoćne metode za kreiranje test podataka
    // -------------------------------------------------------

    private Truck makeTruck(String id, TruckType type, double capacity, TruckStatus status,
                            boolean frigo, boolean adr, double fuel, double distToOrigin) {
        Truck t = new Truck();
        t.setId(id);
        t.setType(type);
        t.setMaxCapacityKg(capacity);
        t.setStatus(status);
        t.setHasRefrigerationUnit(frigo);
        t.setHasAdrEquipment(adr);
        t.setFuelPercent(fuel);
        t.setDistanceToOriginKm(distToOrigin);
        t.setDaysSinceRefrigerationService(12);
        t.setLocation("NS");
        return t;
    }

    private Driver makeDriver(String id, boolean available, double hours, String license,
                              boolean adr, int fatigue, int experience) {
        Driver d = new Driver();
        d.setId(id);
        d.setAvailable(available);
        d.setWorkingHoursToday(hours);
        d.setLicense(license);
        d.setHasAdrLicense(adr);
        d.setFatigueLevel(fatigue);
        d.setYearsOfExperience(experience);
        return d;
    }

    private Route makeRoute(String id, RoadType type, double distKm, double maxSpeed, boolean tunnel) {
        Route r = new Route();
        r.setId(id);
        r.setRoadType(type);
        r.setDistanceKm(distKm);
        r.setMaxSpeedKmh(maxSpeed);
        r.setHasTunnel(tunnel);
        r.setEstimatedTimeHours(distKm / maxSpeed);
        r.setMaxCapacityKg(24000);
        return r;
    }

    private DeliveryOrder makeOrder(String id, String routeId, double weightKg,
                                   CargoType cargo, int deadlineMin, OrderPriority priority) {
        DeliveryOrder o = new DeliveryOrder();
        o.setId(id);
        o.setRouteId(routeId);
        o.setWeightKg(weightKg);
        o.setCargoType(cargo);
        o.setDeliveryDeadlineMin(deadlineMin);
        o.setPriority(priority);
        o.setStatus(OrderStatus.NEW);
        o.setDestination("Beograd");
        return o;
    }

    // -------------------------------------------------------
    // Test 1: Spring kontekst se učitava
    // -------------------------------------------------------

    @Test
    @DisplayName("Spring context loads successfully")
    void contextLoads() {
        assertThat(dispatchService).isNotNull();
    }

    // -------------------------------------------------------
    // Test 2: Scenario iz specifikacije — sekcija 11
    // 07:30 sreda, temp=-3°C
    // Nalog 201: rashladna 4800kg  → treba biti ASSIGNED na K-09 + V-05
    // Nalog 202: standardno 2100kg → treba biti ASSIGNED
    // -------------------------------------------------------

    @Test
    @DisplayName("Spec scenario: morning peak + winter, refrigerated order gets assigned")
    void specScenario_MorningPeakAndWinter() {
        Truck k09 = makeTruck("K-09", TruckType.MEDIUM, 6000, TruckStatus.AVAILABLE, true, false, 78, 8);
        Truck k03 = makeTruck("K-03", TruckType.SMALL,  3500, TruckStatus.AVAILABLE, false, false, 90, 5);

        Driver v05 = makeDriver("V-05", true, 2, "C", true, 2, 5);
        v05.setRecentRouteIds(List.of("R-14"));

        Driver v01 = makeDriver("V-01", true, 1, "B", false, 1, 3);

        Route r14 = makeRoute("R-14", RoadType.HIGHWAY, 130, 120, false);
        Route r02 = makeRoute("R-02", RoadType.CITY,    20,  50, false);

        DeliveryOrder o201 = makeOrder("201", "R-14", 4800, CargoType.REFRIGERATED, 180, OrderPriority.HIGH);
        DeliveryOrder o202 = makeOrder("202", "R-02", 2100, CargoType.STANDARD, 300, OrderPriority.NORMAL);

        DispatchRequest req = new DispatchRequest();
        req.setTemperature(-3.0);
        req.setHour(7);
        req.setDayOfWeek(3); // sreda
        req.setTrucks(List.of(k09, k03));
        req.setDrivers(List.of(v05, v01));
        req.setRoutes(List.of(r14, r02));
        req.setOrders(List.of(o201, o202));

        DispatchResult result = dispatchService.processDispatch(req);

        System.out.println("=== MESSAGES ===");
        result.getMessages().forEach(System.out::println);

        // Zimski kontekst se aktivira
        assertThat(result.getMessages()).anyMatch(m -> m.contains("Winter conditions"));
        // Jutarnji špic se aktivira
        assertThat(result.getMessages()).anyMatch(m -> m.contains("Morning peak"));

        // Nalog 201 treba biti ASSIGNED (K-09 frigo, 6000*0.85=5100 >= 4800)
        DeliveryOrder assigned201 = result.getProcessedOrders().stream()
                .filter(o -> o.getId().equals("201")).findFirst().orElseThrow();
        assertThat(assigned201.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(assigned201.getAssignedTruckId()).isEqualTo("K-09");
        assertThat(assigned201.getAssignedDriverId()).isEqualTo("V-05");

        // Nalog 202 treba biti ASSIGNED
        DeliveryOrder assigned202 = result.getProcessedOrders().stream()
                .filter(o -> o.getId().equals("202")).findFirst().orElseThrow();
        assertThat(assigned202.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
    }

    // -------------------------------------------------------
    // Test 3: Neizvodljiv nalog — masa premašuje kapacitet svih kamiona zimi
    // -------------------------------------------------------

    @Test
    @DisplayName("Order is UNFEASIBLE when winter reduces capacity below order weight")
    void winterMakesOrderUnfeasible() {
        // Najveći kamion 6000kg, zimski faktor 0.85 → efektivno 5100kg
        // Nalog zahteva 5500kg → neizvodljivo u zimskim uslovima
        Truck k09 = makeTruck("K-09", TruckType.MEDIUM, 6000, TruckStatus.AVAILABLE, false, false, 80, 5);
        Driver v05 = makeDriver("V-05", true, 2, "C", false, 2, 5);
        Route r1  = makeRoute("R-01", RoadType.HIGHWAY, 100, 120, false);
        DeliveryOrder order = makeOrder("300", "R-01", 5500, CargoType.STANDARD, 240, OrderPriority.NORMAL);

        DispatchRequest req = new DispatchRequest();
        req.setTemperature(-3.0);
        req.setHour(10);
        req.setDayOfWeek(3);
        req.setTrucks(List.of(k09));
        req.setDrivers(List.of(v05));
        req.setRoutes(List.of(r1));
        req.setOrders(List.of(order));

        DispatchResult result = dispatchService.processDispatch(req);

        DeliveryOrder processed = result.getProcessedOrders().stream()
                .filter(o -> o.getId().equals("300")).findFirst().orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
        assertThat(result.getMessages()).anyMatch(m -> m.contains("UNFEASIBLE") && m.contains("winter"));
    }

    // -------------------------------------------------------
    // Test 4: Hitni nalog (rok < 120min) dobija prioritet
    // -------------------------------------------------------

    @Test
    @DisplayName("Urgent order gets assigned before normal order when same truck")
    void urgentOrderGetsPriority() {
        Truck truck = makeTruck("K-01", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
        Driver d1   = makeDriver("V-01", true, 1, "C", false, 2, 4);
        Route  r1   = makeRoute("R-01", RoadType.HIGHWAY, 100, 120, false);

        // Hitni nalog (rok 90min < 120)
        DeliveryOrder urgent = makeOrder("U-01", "R-01", 2000, CargoType.STANDARD, 90, OrderPriority.NORMAL);
        // Obični nalog
        DeliveryOrder normal = makeOrder("N-01", "R-01", 2000, CargoType.STANDARD, 300, OrderPriority.NORMAL);

        DispatchRequest req = new DispatchRequest();
        req.setTemperature(10.0);
        req.setHour(10);
        req.setDayOfWeek(3);
        req.setTrucks(List.of(truck));
        req.setDrivers(List.of(d1));
        req.setRoutes(List.of(r1));
        req.setOrders(List.of(urgent, normal));

        DispatchResult result = dispatchService.processDispatch(req);
        System.out.println("=== URGENT TEST MESSAGES ===");
        result.getMessages().forEach(System.out::println);

        DeliveryOrder processedUrgent = result.getProcessedOrders().stream()
                .filter(o -> o.getId().equals("U-01")).findFirst().orElseThrow();
        // Hitni nalog treba biti AUTO-upgrejdovan i dodeljen
        assertThat(processedUrgent.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(result.getMessages()).anyMatch(m -> m.contains("URGENT") && m.contains("U-01"));
    }

    // -------------------------------------------------------
    // Test 5: Rashladni nalog odbijen kada nema frigo kamiona
    // -------------------------------------------------------

    @Test
    @DisplayName("Refrigerated order is UNFEASIBLE when no frigo truck available")
    void refrigeratedOrderWithNoFrigoTruck() {
        Truck nonFrigo = makeTruck("K-05", TruckType.MEDIUM, 8000, TruckStatus.AVAILABLE, false, false, 80, 5);
        Driver driver  = makeDriver("V-01", true, 2, "C", false, 1, 4);
        Route  route   = makeRoute("R-01", RoadType.HIGHWAY, 100, 120, false);
        DeliveryOrder order = makeOrder("R-01", "R-01", 2000, CargoType.REFRIGERATED, 240, OrderPriority.NORMAL);

        DispatchRequest req = new DispatchRequest();
        req.setTemperature(15.0);
        req.setHour(10);
        req.setDayOfWeek(3);
        req.setTrucks(List.of(nonFrigo));
        req.setDrivers(List.of(driver));
        req.setRoutes(List.of(route));
        req.setOrders(List.of(order));

        DispatchResult result = dispatchService.processDispatch(req);

        DeliveryOrder processed = result.getProcessedOrders().stream()
                .filter(o -> o.getId().equals("R-01")).findFirst().orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
        assertThat(result.getMessages()).anyMatch(m -> m.contains("frigo") || m.contains("refriger"));
    }

    // -------------------------------------------------------
    // Test 6: Score bonus — poznata ruta daje viši skor
    // Dva identična kamiona, ali jedan vozač poznaje rutu
    // -------------------------------------------------------

    @Test
    @DisplayName("Driver familiar with route gets assigned (score bonus +20)")
    void familiarRouteDriverGetsAssigned() {
        Truck t1 = makeTruck("K-01", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 15);
        Truck t2 = makeTruck("K-02", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 15);

        Driver dFamiliar = makeDriver("V-FAM", true, 2, "C", false, 3, 4);
        dFamiliar.setRecentRouteIds(List.of("R-01")); // poznaje rutu → +20

        Driver dUnknown  = makeDriver("V-NEW", true, 2, "C", false, 3, 4);

        Route  route  = makeRoute("R-01", RoadType.HIGHWAY, 100, 120, false);
        DeliveryOrder order = makeOrder("O-01", "R-01", 2000, CargoType.STANDARD, 300, OrderPriority.NORMAL);

        DispatchRequest req = new DispatchRequest();
        req.setTemperature(10.0);
        req.setHour(10);
        req.setDayOfWeek(3);
        req.setTrucks(List.of(t1, t2));
        req.setDrivers(List.of(dFamiliar, dUnknown));
        req.setRoutes(List.of(route));
        req.setOrders(List.of(order));

        DispatchResult result = dispatchService.processDispatch(req);
        System.out.println("=== FAMILIAR ROUTE MESSAGES ===");
        result.getMessages().forEach(System.out::println);

        DeliveryOrder processed = result.getProcessedOrders().stream()
                .filter(o -> o.getId().equals("O-01")).findFirst().orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(processed.getAssignedDriverId()).isEqualTo("V-FAM");
    }

    // -------------------------------------------------------
    // Test 7: Noćni režim — neurgentni nalozi se odlažu
    // -------------------------------------------------------

    @Test
    @DisplayName("Night mode: non-urgent order is postponed to morning")
    void nightModePostponesNonUrgentOrder() {
        Truck  truck  = makeTruck("K-01", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
        Driver driver = makeDriver("V-01", true, 2, "C", false, 2, 4);
        Route  route  = makeRoute("R-01", RoadType.HIGHWAY, 100, 120, false);
        DeliveryOrder order = makeOrder("N-01", "R-01", 2000, CargoType.STANDARD, 300, OrderPriority.NORMAL);

        DispatchRequest req = new DispatchRequest();
        req.setTemperature(10.0);
        req.setHour(22); // noćni režim
        req.setDayOfWeek(3);
        req.setTrucks(List.of(truck));
        req.setDrivers(List.of(driver));
        req.setRoutes(List.of(route));
        req.setOrders(List.of(order));

        DispatchResult result = dispatchService.processDispatch(req);
        System.out.println("=== NIGHT MODE MESSAGES ===");
        result.getMessages().forEach(System.out::println);

        assertThat(result.getMessages()).anyMatch(m -> m.contains("Night mode") || m.contains("URGENT"));
        DeliveryOrder processed = result.getProcessedOrders().stream()
                .filter(o -> o.getId().equals("N-01")).findFirst().orElseThrow();
        // Noćni režim: nalog je VALID ali ga blokira pravilo noćnog režima
        assertThat(processed.getStatus()).isIn(OrderStatus.WAITING_RESOURCES, OrderStatus.POSTPONED_UNTIL_MORNING);
    }

    // -------------------------------------------------------
    // Test 8: Domino efekat — kaskadno kašnjenje
    // -------------------------------------------------------

    @Test
    @DisplayName("Domino effect: delay on in-progress order propagates to waiting order")
    void dominoEffectPropagatesDelay() {
        Truck truck = makeTruck("K-07", TruckType.MEDIUM, 5000, TruckStatus.BUSY, false, false, 80, 0);
        Driver driver = makeDriver("V-07", false, 6, "C", false, 4, 5);
        Route route = makeRoute("R-07", RoadType.HIGHWAY, 150, 120, false);

        // Nalog u toku — kasni 25 min
        DeliveryOrder n105 = makeOrder("105", "R-07", 2000, CargoType.STANDARD, 60, OrderPriority.NORMAL);
        n105.setStatus(OrderStatus.IN_PROGRESS);
        n105.setAssignedTruckId("K-07");
        n105.setDelayMin(25);

        // Nalog dodeljen istom kamionu — sledeći u redu, biće odložen
        DeliveryOrder n106 = makeOrder("106", "R-07", 1500, CargoType.STANDARD, 90, OrderPriority.NORMAL);
        n106.setStatus(OrderStatus.ASSIGNED);
        n106.setAssignedTruckId("K-07");

        DispatchRequest req = new DispatchRequest();
        req.setTemperature(10.0);
        req.setHour(10);
        req.setDayOfWeek(3);
        req.setTrucks(List.of(truck));
        req.setDrivers(List.of(driver));
        req.setRoutes(List.of(route));
        req.setOrders(List.of(n105, n106));

        DispatchResult result = dispatchService.processDispatch(req);
        System.out.println("=== DOMINO MESSAGES ===");
        result.getMessages().forEach(System.out::println);

        assertThat(result.getMessages()).anyMatch(m -> m.contains("DOMINO") && m.contains("105"));
        assertThat(result.getMessages()).anyMatch(m -> m.contains("106"));
    }

    // -------------------------------------------------------
    // Test 9: Unazadni lanac zaključivanja — BC dijagnostika se aktivira
    // -------------------------------------------------------

    @Test
    @DisplayName("Backward chaining: all rejection causes found for unassigned order")
    void backwardChainingDiagnosis() {
        // Nema kamiona uopšte → nalog će biti WAITING_RESOURCES → BC se okida
        Driver driver = makeDriver("V-01", true, 2, "C", false, 2, 4);
        Route  route  = makeRoute("R-01", RoadType.HIGHWAY, 100, 120, false);
        DeliveryOrder order = makeOrder("BC-01", "R-01", 2000, CargoType.STANDARD, 300, OrderPriority.NORMAL);

        DispatchRequest req = new DispatchRequest();
        req.setTemperature(10.0);
        req.setHour(10);
        req.setDayOfWeek(3);
        req.setTrucks(List.of());     // nema kamiona
        req.setDrivers(List.of(driver));
        req.setRoutes(List.of(route));
        req.setOrders(List.of(order));

        DispatchResult result = dispatchService.processDispatch(req);
        System.out.println("=== BC DIAGNOSIS MESSAGES ===");
        result.getMessages().forEach(System.out::println);

        assertThat(result.getMessages()).anyMatch(m -> m.contains("DIAGNOSIS") || m.contains("cause"));
    }

    // -------------------------------------------------------
    // Test 10: Accumulate — alarm za preopterećenog vozača
    // -------------------------------------------------------

    @Test
    @DisplayName("Accumulate: overloaded driver alarm fires when driver exceeds 9 working hours")
    void accumulateOverloadedDriver() {
        Truck  t1  = makeTruck("K-01", TruckType.LARGE, 24000, TruckStatus.AVAILABLE, false, false, 80, 0);
        Driver drv = makeDriver("V-01", true, 10, "CE", false, 3, 6);
        Route  r   = makeRoute("R-01", RoadType.HIGHWAY, 100, 120, false);

        DispatchRequest req = new DispatchRequest();
        req.setTemperature(10.0);
        req.setHour(10);
        req.setDayOfWeek(3);
        req.setTrucks(List.of(t1));
        req.setDrivers(List.of(drv));
        req.setRoutes(List.of(r));
        req.setOrders(List.of());

        DispatchResult result = dispatchService.processDispatch(req);

        assertThat(result.getMessages()).anyMatch(m -> m.contains("V-01") && m.contains("9h limit"));
        assertThat(result.getAlarms()).anyMatch(a -> a.getType() == AlarmType.OVERLOADED_DRIVER
                && "FLEET".equals(a.getEntityId()));
    }
}
