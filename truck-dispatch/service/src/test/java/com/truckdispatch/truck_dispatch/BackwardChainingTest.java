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

    // ── Query 1: isCauseOfRejection ───────────────────────────────────────────

    @Nested
    @DisplayName("isCauseOfRejection — recursive diagnosis chain")
    class IsCauseOfRejection {

        @Test
        @DisplayName("WinterReducesCapacity fires when winter is the REAL cause for this order")
        void diagnosisWinterFires() {
            // Kamion 1000 kg × 0.85 (zimski faktor) = 850 kg efektivno; nalog treba 5000 kg → UNFEASIBLE.
            // Dijagnostika mora prijaviti WinterReducesCapacity jer je to stvarni uzrok.
            Truck  t = truck("T1", TruckType.SMALL, 1000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(-5, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 5000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: WinterReducesCapacity"));
        }

        @Test
        @DisplayName("BC_Diagnosis_AllCauses: NoFrigoTruck 2-hop chain za REFRIGERATED nalog")
        void diagnosisNoFrigoTruck() {
            // Nema rashladnog kamiona u floti → REFRIGERATED nalog je UNFEASIBLE.
            // FC pravilo: CargoType_Refrigerated_NoFrigoTruck (salience 870) — order ide NEW→VALID→UNFEASIBLE.
            // Lanac: NoFrigoTruck (2 hopa) → NoTruckAvailable (1 hop) → OrderUnassigned
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.REFRIGERATED, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: NoFrigoTruck"));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: NoTruckAvailable"));
            // BC_SpecialOrderCheck ne pali jer nalog nikad ne dostiže ASSIGNED
            assertThat(res.getMessages()).noneMatch(m -> m.contains("is SPECIAL"));
        }

        @Test
        @DisplayName("BC_Diagnosis_Winter does NOT fire when temperature is positive — no false positives")
        void noWinterDiagnosisWhenTempPositive() {
            // Isti scenario kao gore ali temp=+15 → bez zimskog konteksta.
            // Stara implementacija bi pogrešno prikazivala 'WinterReducesCapacity' jer je imala globalnu
            // statičku bazu. Nova implementacija to NE smije prikazati.
            Truck  t = truck("T1", TruckType.SMALL, 1000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 5000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("DIAGNOSIS cause: CargoOverweight"));
            assertThat(res.getMessages())
                    .noneMatch(m -> m.contains("WinterReducesCapacity"));
        }

        @Test
        @DisplayName("BC_Diagnosis_AllCauses: CargoOverweight 3-hop chain — svi cvorovi se prijavljuju")
        void diagnosisAllCausesCargoOverweightChain() {
            // temp=15 (bez zime), kamion 1000 kg, nalog 5000 kg → UNFEASIBLE zbog prekomjerne tezine.
            // Rekurzivni query vraca sve cvorove koji transitivno vode do OrderUnassigned:
            //   CargoOverweight (3 hopa) → InsufficientCapacity (2 hopa) → NoTruckAvailable (1 hop)
            Truck  t = truck("T1", TruckType.SMALL, 1000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 5000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);

            // leaf cvor (3 hopa od OrderUnassigned)
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: CargoOverweight"));
            // medjucvor (2 hopa)
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: InsufficientCapacity"));
            // medjucvor (1 hop)
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: NoTruckAvailable"));

            // NE smije prikazati uzroke koji nisu stvarno aktivni za ovaj nalog
            assertThat(res.getMessages()).noneMatch(m -> m.contains("WinterReducesCapacity"));
            assertThat(res.getMessages()).noneMatch(m -> m.contains("AllDriversFatigued"));
        }

        @Test
        @DisplayName("BC_Diagnosis_AllCauses: WinterReducesCapacity 3-hop chain sa zimskim kontekstom")
        void diagnosisAllCausesWinterChain() {
            // Kamion 6000 kg × 0.85 = 5100 kg; nalog 5500 kg → UNFEASIBLE samo zbog zime.
            // Lanac: WinterReducesCapacity → InsufficientCapacity → NoTruckAvailable → OrderUnassigned
            Truck  t = truck("T1", TruckType.LARGE, 6000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(-3, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 5500, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.UNFEASIBLE);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("DIAGNOSIS cause: WinterReducesCapacity"));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("DIAGNOSIS cause: InsufficientCapacity"));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("DIAGNOSIS cause: NoTruckAvailable"));
            // CargoOverweight NE smije biti prikazan — bez zime bi truck 6000 >= 5500 prosao
            assertThat(res.getMessages()).noneMatch(m -> m.contains("CargoOverweight"));
        }

        @Test
        @DisplayName("BC_Diagnosis_AllCauses: AllDriversFatigued 2-hop chain")
        void diagnosisAllDriversFatiguedChain() {
            // Kamion OK (5000 kg), nalog 1000 kg → prolazi validaciju,
            // ali vozac fatigueLevel=7 → nema ValidPair → WAITING_RESOURCES.
            // Uzroci: AllDriversFatigued (2 hopa) → NoDriverAvailable (1 hop) → OrderUnassigned
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 7, 5); // fatigue=7 iskljucuje vozaca
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: AllDriversFatigued"));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: NoDriverAvailable"));
            // NE smije prijaviti uzroke kapaciteta — kamion je prosao
            assertThat(res.getMessages()).noneMatch(m -> m.contains("NoTruckAvailable"));
        }

        @Test
        @DisplayName("BC_Diagnosis_AllCauses: AllDriversOverHours 2-hop chain")
        void diagnosisAllDriversOverHoursChain() {
            // Vozac s radnim satima=8 (ne < 8) → iskljucen; kamion OK → WAITING_RESOURCES
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 8, "CE", false, 1, 5); // hours=8 nije < 8
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: AllDriversOverHours"));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: NoDriverAvailable"));
        }

        @Test
        @DisplayName("BC_Diagnosis_AllCauses: NoAdrLicense 2-hop chain za HAZARDOUS nalog")
        void diagnosisNoAdrLicenseChain() {
            // ADR kamion postoji i slobodan je, ali vozac nema ADR licencu → WAITING_RESOURCES
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, true, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5); // nema ADR
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.HAZARDOUS, 300, OrderPriority.URGENT))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: NoAdrLicense"));
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: NoDriverAvailable"));
        }

        @Test
        @DisplayName("BC_Diagnosis_AllCauses: NightModeRestriction 1-hop za nocni HIGH nalog")
        void diagnosisNightModeRestriction() {
            // Nocni rezim (hour=23) odlaze HIGH prioritet → WAITING_RESOURCES.
            // Uzrok: NightModeRestriction → OrderUnassigned (1 hop, direktno)
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(10, 23, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.HIGH))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: NightModeRestriction"));
            assertThat(res.getMessages()).noneMatch(m -> m.contains("NoTruckAvailable"));
            assertThat(res.getMessages()).noneMatch(m -> m.contains("NoDriverAvailable"));
        }

        @Test
        @DisplayName("BC_Diagnosis_AllCauses: WeekendRestriction 1-hop za NORMAL nalog vikendom")
        void diagnosisWeekendRestriction() {
            // Vikend (day=6), NORMAL prioritet → WAITING_RESOURCES.
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 6, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.WAITING_RESOURCES);
            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("DIAGNOSIS cause: WeekendRestriction"));
        }

        @Test
        @DisplayName("BC_Diagnosis does NOT fire when order is ASSIGNED")
        void noDiagnosisForAssignedOrder() {
            // Negativan test — BC dijagnostika se ne smije okidati za uspjesno dodijeljene naloge.
            Truck  t = truck("T1", TruckType.MEDIUM, 5000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 1, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(find(res, "O1").getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(res.getMessages()).noneMatch(m -> m.contains("DIAGNOSIS"));
        }

        @Test
        @DisplayName("Dijagnoza je specificna po nalogu — O1 i O2 dobijaju razlicite uzroke")
        void diagnosisIsOrderSpecific() {
            // O1: 5000 kg, kamion 2000 kg → UNFEASIBLE (CargoOverweight)
            // O2: 1000 kg, vozac fatigue=7 → WAITING_RESOURCES (AllDriversFatigued)
            // Svaki nalog mora dobiti SAMO svoje uzroke, ne tudje.
            Truck  t = truck("T1", TruckType.MEDIUM, 2000, TruckStatus.AVAILABLE, false, false, 80, 5);
            Driver d = driver("D1", true, 1, "CE", false, 7, 5);
            Route  r = route("R1", RoadType.HIGHWAY, 100, 120, false);
            DispatchResult res = dispatchService.processDispatch(
                    req(15, 10, 3, List.of(t), List.of(d), List.of(r),
                            List.of(order("O1", "R1", 5000, CargoType.STANDARD, 300, OrderPriority.NORMAL),
                                    order("O2", "R1", 1000, CargoType.STANDARD, 300, OrderPriority.NORMAL))));

            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O1") && m.contains("CargoOverweight"));
            assertThat(res.getMessages())
                    .noneMatch(m -> m.contains("O1") && m.contains("AllDriversFatigued"));

            assertThat(res.getMessages())
                    .anyMatch(m -> m.contains("O2") && m.contains("AllDriversFatigued"));
            assertThat(res.getMessages())
                    .noneMatch(m -> m.contains("O2") && m.contains("CargoOverweight"));
        }
    }

    // ── Query 2: belongsToOrderCategory ──────────────────────────────────────

    @Nested
    @DisplayName("belongsToOrderCategory — order group hierarchy")
    class BelongsToOrderCategory {

        @Test
        @DisplayName("REFRIGERATED triggers BC_SpecialOrderCheck — direct SpecialOrder membership")
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
        @DisplayName("HAZARDOUS triggers BC_SpecialOrderCheck — direct SpecialOrder membership")
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
        @DisplayName("STANDARD does NOT trigger BC_SpecialOrderCheck — belongs to CommercialOrder")
        void standardIsNotSpecial() {
            // STANDARD → StandardDelivery → CommercialOrder → GeneralOrder (nikad ne doseze SpecialOrder)
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
        @DisplayName("REFRIGERATED UNFEASIBLE — BC_SpecialOrderCheck se ne smije okidati")
        void refrigeratedUnfeasibleSkipsSpecialCheck() {
            // Bez frigo kamiona nalog postaje UNFEASIBLE, nikad ne doseze ASSIGNED.
            // BC_SpecialOrderCheck zahtijeva status == ASSIGNED, pa se ne smije okidati.
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
