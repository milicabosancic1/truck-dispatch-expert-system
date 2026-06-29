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
@DisplayName("Domino — delay cascade rules (Level 5 FC)")
class DominoTest {

    @Autowired
    private DispatchService dispatchService;

    // ---- pomoćne metode ----

    private Truck truck(String id, TruckStatus status, double cap) {
        Truck t = new Truck();
        t.setId(id); t.setType(TruckType.MEDIUM); t.setMaxCapacityKg(cap);
        t.setStatus(status); t.setHasRefrigerationUnit(false); t.setHasAdrEquipment(false);
        t.setFuelPercent(80); t.setDistanceToOriginKm(5);
        t.setDaysSinceRefrigerationService(5); t.setLocation("NS");
        return t;
    }

    // Nalog u statusu IN_PROGRESS sa kašnjenjem — primarni uzrok domino efekta
    private DeliveryOrder delayed(String id, String truckId, int delayMin) {
        DeliveryOrder o = new DeliveryOrder();
        o.setId(id); o.setRouteId("R1"); o.setWeightKg(1000);
        o.setCargoType(CargoType.STANDARD); o.setDeliveryDeadlineMin(300);
        o.setPriority(OrderPriority.NORMAL); o.setStatus(OrderStatus.IN_PROGRESS);
        o.setDestination("Beograd"); o.setDelayMin(delayMin);
        o.setAssignedTruckId(truckId);
        return o;
    }

    // Nalog u statusu ASSIGNED — sledeći u redu za isti kamion, biće odložen zbog tekuće dostave
    private DeliveryOrder waiting(String id, String truckId, OrderPriority priority, double weightKg) {
        DeliveryOrder o = new DeliveryOrder();
        o.setId(id); o.setRouteId("R1"); o.setWeightKg(weightKg);
        o.setCargoType(CargoType.STANDARD); o.setDeliveryDeadlineMin(300);
        o.setPriority(priority); o.setStatus(OrderStatus.ASSIGNED);
        o.setDestination("Beograd");
        o.setAssignedTruckId(truckId);
        return o;
    }

    private DeliveryOrder find(DispatchResult res, String id) {
        return res.getProcessedOrders().stream()
                .filter(o -> o.getId().equals(id)).findFirst().orElseThrow();
    }

    private DispatchRequest req(int hour, List<Truck> trucks, List<DeliveryOrder> orders) {
        DispatchRequest r = new DispatchRequest();
        r.setTemperature(15.0); r.setHour(hour); r.setDayOfWeek(3);
        r.setTrucks(trucks); r.setDrivers(List.of());
        r.setRoutes(List.of()); r.setOrders(orders);
        return r;
    }

    // ---- testovi ----

    @Test
    @DisplayName("Domino_DetectPrimary: delayed order cascades to WAITING_UNLOADING order on same truck")
    void primaryDetection() {
        // O1 kasni; O2 čeka isti kamion T1
        DeliveryOrder o1 = delayed("O1", "T1", 30);
        DeliveryOrder o2 = waiting("O2", "T1", OrderPriority.NORMAL, 1000);

        DispatchResult res = dispatchService.processDispatch(
                req(10, List.of(truck("T1", TruckStatus.BUSY, 5000)), List.of(o1, o2)));

        assertThat(res.getMessages())
                .anyMatch(m -> m.contains("DOMINO:") && m.contains("O1") && m.contains("O2"));
    }

    @Test
    @DisplayName("Domino_PropagateChain: cascade spreads to all downstream orders blocked on the same truck")
    void chainPropagation() {
        // O1 kasni → O2 i O3 oba čekaju na T1 → oba se prijavljuju kao domino-zahvaćeni
        // DetectPrimary se okida za sve ASSIGNED naloge na istom kamionu;
        // PropagateChain proširuje kaskadu na svaki nalog koji još nije pokriven.
        DeliveryOrder o1 = delayed("O1", "T1", 30);
        DeliveryOrder o2 = waiting("O2", "T1", OrderPriority.NORMAL, 1000);
        DeliveryOrder o3 = waiting("O3", "T1", OrderPriority.NORMAL, 1000);

        DispatchResult res = dispatchService.processDispatch(
                req(10, List.of(truck("T1", TruckStatus.BUSY, 5000)), List.of(o1, o2, o3)));

        assertThat(res.getMessages())
                .anyMatch(m -> m.contains("DOMINO") && m.contains("O2"));
        assertThat(res.getMessages())
                .anyMatch(m -> m.contains("DOMINO") && m.contains("O3"));
    }

    @Test
    @DisplayName("Domino_ReplannUrgentOrder: URGENT order affected by domino gets reassigned to free truck")
    void urgentOrderReplanned() {
        // O1 kasni; O2 (URGENT) čeka na T1; T2 je slobodan i dovoljnog kapaciteta
        DeliveryOrder o1 = delayed("O1", "T1", 30);
        DeliveryOrder o2 = waiting("O2", "T1", OrderPriority.URGENT, 1000);

        DispatchResult res = dispatchService.processDispatch(
                req(10,
                    List.of(truck("T1", TruckStatus.BUSY, 5000),
                            truck("T2", TruckStatus.AVAILABLE, 5000)),
                    List.of(o1, o2)));

        DeliveryOrder result = find(res, "O2");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.REPLANNED);
        assertThat(result.getAssignedTruckId()).isEqualTo("T2");
        assertThat(res.getMessages())
                .anyMatch(m -> m.contains("O2") && m.contains("replanned") && m.contains("T2"));
    }

    @Test
    @DisplayName("Domino_Escalation: 3+ cascading delays from one truck trigger escalation alarm")
    void escalationAlarm() {
        // O1 kasni; O2+O3+O4 svi čekaju na T1 → 3 DelayPropagation-a → eskalacija
        DeliveryOrder o1 = delayed("O1", "T1", 30);
        DeliveryOrder o2 = waiting("O2", "T1", OrderPriority.NORMAL, 1000);
        DeliveryOrder o3 = waiting("O3", "T1", OrderPriority.NORMAL, 1000);
        DeliveryOrder o4 = waiting("O4", "T1", OrderPriority.NORMAL, 1000);

        DispatchResult res = dispatchService.processDispatch(
                req(10, List.of(truck("T1", TruckStatus.BUSY, 5000)), List.of(o1, o2, o3, o4)));

        assertThat(res.getAlarms())
                .anyMatch(a -> a.getType() == AlarmType.DOMINO_ESCALATION
                            && "T1".equals(a.getEntityId()));
        assertThat(res.getMessages())
                .anyMatch(m -> m.contains("ESCALATION") && m.contains("T1"));
    }

    @Test
    @DisplayName("Domino_NightMode_Postpone: non-urgent domino order postponed when night mode active and no free truck")
    void nightModePostpone() {
        // Noćni režim (hour=23); O1 kasni; O2 (HIGH prioritet, nije URGENT) čeka na T1; nema slobodnog kamiona
        DeliveryOrder o1 = delayed("O1", "T1", 30);
        DeliveryOrder o2 = waiting("O2", "T1", OrderPriority.HIGH, 1000);

        DispatchResult res = dispatchService.processDispatch(
                req(23, List.of(truck("T1", TruckStatus.BUSY, 5000)), List.of(o1, o2)));

        assertThat(find(res, "O2").getStatus()).isEqualTo(OrderStatus.POSTPONED_UNTIL_MORNING);
        assertThat(res.getMessages())
                .anyMatch(m -> m.contains("O2") && m.contains("postponed"));
    }
}
