package com.truckdispatch.truck_dispatch.service;

import com.truckdispatch.truck_dispatch.model.Alarm;
import com.truckdispatch.truck_dispatch.model.DeliveryOrder;
import com.truckdispatch.truck_dispatch.model.FleetEvent;
import com.truckdispatch.truck_dispatch.model.Truck;
import jakarta.annotation.PreDestroy;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CEP service — maintains a long-lived stateful KieSession in stream mode.
 * FleetEvent objects are inserted as they arrive (real-time).
 * Fleet state (trucks, orders) is synced after each dispatch so that
 * CEP_BreakdownDuringOrder and similar cross-session rules can fire.
 */
@Service
public class CepService {

    private final KieSession cepSession;
    private final List<String> messages = new ArrayList<>();

    // Track fact handles so we can retract and re-insert on next dispatch
    private final Map<String, FactHandle> truckHandles = new HashMap<>();
    private final Map<String, FactHandle> orderHandles = new HashMap<>();

    public CepService(KieContainer kieContainer) {
        this.cepSession = kieContainer.newKieSession("TruckDispatchCEPSession");
        this.cepSession.setGlobal("messages", messages);
    }

    /**
     * Called after each successful dispatch so CEP rules have access to
     * current truck and order state (needed for CEP_BreakdownDuringOrder).
     */
    public void syncFleetState(List<Truck> trucks, List<DeliveryOrder> orders) {
        // Retract stale facts
        truckHandles.values().forEach(cepSession::delete);
        truckHandles.clear();
        orderHandles.values().forEach(cepSession::delete);
        orderHandles.clear();

        // Insert fresh state
        for (Truck t : trucks) {
            FactHandle fh = cepSession.insert(t);
            truckHandles.put(t.getId(), fh);
        }
        for (DeliveryOrder o : orders) {
            FactHandle fh = cepSession.insert(o);
            orderHandles.put(o.getId(), fh);
        }
    }

    /** Insert a new fleet event and fire rules immediately. */
    public List<String> processEvent(FleetEvent event) {
        messages.clear();
        cepSession.insert(event);
        cepSession.fireAllRules();
        return new ArrayList<>(messages);
    }

    /** Return all alarms currently in the CEP session working memory. */
    public List<Alarm> getActiveAlarms() {
        Collection<? extends Object> facts = cepSession.getObjects();
        List<Alarm> alarms = new ArrayList<>();
        for (Object fact : facts) {
            if (fact instanceof Alarm alarm) {
                alarms.add(alarm);
            }
        }
        return alarms;
    }

    @PreDestroy
    public void shutdown() {
        if (cepSession != null) {
            cepSession.dispose();
        }
    }
}
