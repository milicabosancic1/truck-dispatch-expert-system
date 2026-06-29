package com.truckdispatch.truck_dispatch.service;

import com.truckdispatch.truck_dispatch.dto.DispatchRequest;
import com.truckdispatch.truck_dispatch.dto.DispatchResult;
import com.truckdispatch.truck_dispatch.model.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class DispatchService {

    private final KieContainer       kieContainer;
    private final FleetStateService  fleetState;

    public DispatchService(KieContainer kieContainer, FleetStateService fleetState) {
        this.kieContainer = kieContainer;
        this.fleetState   = fleetState;
    }

    public DispatchResult processDispatch(DispatchRequest request) {
        // Register any fleet resources provided in the request (first call bootstraps the store;
        // subsequent calls with empty lists leave the stored state untouched).
        // When the request provides a fleet list it represents the full current configuration —
        // replace the stored state so stale entries from a previous configuration don't linger.
        boolean fullOverride = !request.getTrucks().isEmpty()
                            && !request.getDrivers().isEmpty()
                            && !request.getRoutes().isEmpty();
        if (!request.getTrucks().isEmpty())  fleetState.replaceTrucks(request.getTrucks());
        if (!request.getDrivers().isEmpty()) fleetState.replaceDrivers(request.getDrivers());
        if (!request.getRoutes().isEmpty())  fleetState.replaceRoutes(request.getRoutes());
        if (fullOverride) fleetState.clearOrders();

        KieSession session = kieContainer.newKieSession("TruckDispatchSession");
        List<String> messages = new ArrayList<>();

        try {
            session.setGlobal("messages", messages);

            // Context facts — hour/day auto-detected if not provided
            LocalDateTime now = LocalDateTime.now();
            int hour      = request.getHour()      != null ? request.getHour()      : now.getHour();
            int dayOfWeek = request.getDayOfWeek() != null ? request.getDayOfWeek() : now.getDayOfWeek().getValue();

            session.insert(Integer.valueOf(hour));
            session.insert(Long.valueOf(dayOfWeek));
            session.insert(request.getTemperature());

            // Fleet state comes from the store (may include CEP-updated truck statuses).
            // If the store was just populated above from the request, behaviour is identical to before.
            fleetState.getTrucks().forEach(session::insert);
            fleetState.getDrivers().forEach(session::insert);
            fleetState.getRoutes().forEach(session::insert);

            // Orders come from the request — FC only dispatches newly submitted orders.
            request.getOrders().forEach(session::insert);

            insertOrderGroupMemberships(session);

            session.fireAllRules();

            DispatchResult result = buildResult(session, messages);

            // Write updated truck/driver statuses and newly assigned orders back to the store
            // so that: (a) next dispatch sees current availability, (b) CEP gets fresh state.
            session.getObjects().forEach(obj -> {
                if (obj instanceof Truck t)          fleetState.upsertTruck(t);
                else if (obj instanceof Driver d)    fleetState.upsertDriver(d);
                else if (obj instanceof DeliveryOrder o) fleetState.upsertOrder(o);
            });

            return result;

        } finally {
            session.dispose();
        }
    }

    private void insertOrderGroupMemberships(KieSession session) {
        session.insert(new OrderGroupMembership("RetailDelivery",       "StandardDelivery"));
        session.insert(new OrderGroupMembership("WholesaleDelivery",    "StandardDelivery"));
        session.insert(new OrderGroupMembership("StandardDelivery",     "CommercialOrder"));
        session.insert(new OrderGroupMembership("UrgentDelivery",       "CommercialOrder"));
        session.insert(new OrderGroupMembership("RefrigeratedDelivery", "SpecialOrder"));
        session.insert(new OrderGroupMembership("HazardousDelivery",    "SpecialOrder"));
        session.insert(new OrderGroupMembership("CommercialOrder",      "GeneralOrder"));
        session.insert(new OrderGroupMembership("SpecialOrder",         "GeneralOrder"));
        session.insert(new OrderGroupMembership("REFRIGERATED", "SpecialOrder"));
        session.insert(new OrderGroupMembership("HAZARDOUS",    "SpecialOrder"));
        session.insert(new OrderGroupMembership("STANDARD",     "StandardDelivery"));
    }

    private DispatchResult buildResult(KieSession session, List<String> messages) {
        DispatchResult result = new DispatchResult();
        result.setMessages(messages);

        Collection<? extends Object> facts = session.getObjects();
        for (Object fact : facts) {
            if (fact instanceof DeliveryOrder order) {
                result.getProcessedOrders().add(order);
            } else if (fact instanceof Alarm alarm) {
                result.getAlarms().add(alarm);
            }
        }

        return result;
    }
}
