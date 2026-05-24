package com.truckdispatch.truck_dispatch.service;

import com.truckdispatch.truck_dispatch.model.Alarm;
import com.truckdispatch.truck_dispatch.model.FleetEvent;
import jakarta.annotation.PreDestroy;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * CEP service — maintains a long-lived stateful KieSession in stream mode.
 * FleetEvent objects are inserted as they arrive (real-time).
 * The session uses Drools Fusion temporal operators (after, window:time).
 */
@Service
public class CepService {

    private final KieSession cepSession;
    private final List<String> messages = new ArrayList<>();

    public CepService(KieContainer kieContainer) {
        this.cepSession = kieContainer.newKieSession("TruckDispatchCEPSession");
        this.cepSession.setGlobal("messages", messages);
    }

    /** Insert a new fleet event and fire rules immediately. */
    public List<String> processEvent(FleetEvent event) {
        messages.clear();
        cepSession.insert(event);
        cepSession.fireAllRules();

        // Also insert active orders/trucks from last dispatch — in a real system
        // these would be shared via a domain event bus or repository.
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
