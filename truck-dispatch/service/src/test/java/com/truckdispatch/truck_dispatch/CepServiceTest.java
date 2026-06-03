package com.truckdispatch.truck_dispatch;

import com.truckdispatch.truck_dispatch.model.Alarm;
import com.truckdispatch.truck_dispatch.model.AlarmType;
import com.truckdispatch.truck_dispatch.model.CargoType;
import com.truckdispatch.truck_dispatch.model.DeliveryOrder;
import com.truckdispatch.truck_dispatch.model.FleetEvent;
import com.truckdispatch.truck_dispatch.model.FleetEventType;
import com.truckdispatch.truck_dispatch.model.OrderPriority;
import com.truckdispatch.truck_dispatch.model.OrderStatus;
import com.truckdispatch.truck_dispatch.model.Truck;
import com.truckdispatch.truck_dispatch.model.TruckStatus;
import com.truckdispatch.truck_dispatch.model.TruckType;
import com.truckdispatch.truck_dispatch.service.CepService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("CEP rules")
class CepServiceTest {

    @Autowired
    private CepService cepService;

    private FleetEvent event(FleetEventType type, String entityId, double value, String location, long timestamp) {
        FleetEvent event = new FleetEvent();
        event.setType(type);
        event.setEntityId(entityId);
        event.setValue(value);
        event.setLocation(location);
        event.setTimestamp(timestamp);
        return event;
    }

    private Truck truck(String id, double capacity, TruckStatus status) {
        Truck truck = new Truck();
        truck.setId(id);
        truck.setType(TruckType.MEDIUM);
        truck.setMaxCapacityKg(capacity);
        truck.setStatus(status);
        truck.setLocation("NS");
        truck.setFuelPercent(80);
        return truck;
    }

    private DeliveryOrder inProgressOrder(String id, String truckId, double weightKg) {
        DeliveryOrder order = new DeliveryOrder();
        order.setId(id);
        order.setDestination("Beograd");
        order.setWeightKg(weightKg);
        order.setCargoType(CargoType.STANDARD);
        order.setDeliveryDeadlineMin(120);
        order.setPriority(OrderPriority.URGENT);
        order.setStatus(OrderStatus.IN_PROGRESS);
        order.setAssignedTruckId(truckId);
        order.setRouteId("R1");
        return order;
    }

    @Test
    @DisplayName("three delay events inside 30 minutes create escalation alarm")
    void delayEscalation() {
        long t0 = System.currentTimeMillis() - 10 * 60_000L;

        cepService.processEvent(event(FleetEventType.DELAY, "K-01", 10, "", t0));
        cepService.processEvent(event(FleetEventType.DELAY, "K-01", 12, "", t0 + 5 * 60_000L));
        List<String> messages = cepService.processEvent(
                event(FleetEventType.DELAY, "K-01", 15, "", t0 + 10 * 60_000L));

        assertThat(messages).anyMatch(message -> message.contains("3 delays") && message.contains("K-01"));
        assertThat(cepService.getActiveAlarms())
                .anyMatch(alarm -> alarm.getType() == AlarmType.ESCALATION
                        && alarm.getEntityId().equals("K-01"));
    }

    @Test
    @DisplayName("same position after 20 minutes creates vehicle-stopped alarm")
    void vehicleStopped() {
        long t0 = System.currentTimeMillis() - 21 * 60_000L;

        cepService.processEvent(event(FleetEventType.POSITION, "K-02", 0, "45.267,19.833", t0));
        List<String> messages = cepService.processEvent(
                event(FleetEventType.POSITION, "K-02", 0, "45.267,19.833", t0 + 21 * 60_000L));

        assertThat(messages).anyMatch(message -> message.contains("stopped >20min"));
        assertThat(cepService.getActiveAlarms())
                .anyMatch(alarm -> alarm.getType() == AlarmType.VEHICLE_STOPPED
                        && alarm.getEntityId().equals("K-02"));
    }

    @Test
    @DisplayName("fuel drop over 15 percent inside 10 minutes creates fuel leak alarm")
    void fuelDrop() {
        long t0 = System.currentTimeMillis() - 4 * 60_000L;

        cepService.processEvent(event(FleetEventType.FUEL_LEVEL, "K-03", 80, "", t0));
        List<String> messages = cepService.processEvent(
                event(FleetEventType.FUEL_LEVEL, "K-03", 60, "", t0 + 4 * 60_000L));

        assertThat(messages).anyMatch(message -> message.contains("fuel dropped 20.0%"));
        assertThat(cepService.getActiveAlarms())
                .anyMatch(alarm -> alarm.getType() == AlarmType.FUEL_LEAK
                        && alarm.getEntityId().equals("K-03"));
    }

    @Test
    @DisplayName("five new orders inside 15 minutes create order spike message")
    void orderSpike() {
        long t0 = System.currentTimeMillis() - 4 * 60_000L;
        List<String> messages = List.of();

        for (int i = 0; i < 5; i++) {
            messages = cepService.processEvent(
                    event(FleetEventType.NEW_ORDER, "O-" + i, 0, "", t0 + i * 60_000L));
        }

        assertThat(messages).anyMatch(message -> message.contains("spike") && message.contains("5"));
    }

    @Test
    @DisplayName("breakdown during active order replans to available spare truck")
    void breakdownDuringOrder() {
        Truck broken = truck("K-BROKEN", 6000, TruckStatus.BUSY);
        Truck spare = truck("K-SPARE", 6000, TruckStatus.AVAILABLE);
        DeliveryOrder order = inProgressOrder("O-100", "K-BROKEN", 2500);

        cepService.syncFleetState(List.of(broken, spare), List.of(order));
        List<String> messages = cepService.processEvent(
                event(FleetEventType.BREAKDOWN, "K-BROKEN", 0, "", System.currentTimeMillis()));

        assertThat(messages).anyMatch(message -> message.contains("breakdown K-BROKEN")
                && message.contains("K-SPARE"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REPLANNED);
        assertThat(order.getAssignedTruckId()).isEqualTo("K-SPARE");
        assertThat(spare.getStatus()).isEqualTo(TruckStatus.BUSY);
        assertThat(cepService.getActiveAlarms())
                .extracting(Alarm::getType)
                .contains(AlarmType.EMERGENCY_REPLACEMENT);
    }
}
