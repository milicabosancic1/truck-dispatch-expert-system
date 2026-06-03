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

    private final KieContainer kieContainer;

    public DispatchService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public DispatchResult processDispatch(DispatchRequest request) {
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

            // Domain facts
            request.getRoutes().forEach(session::insert);
            request.getTrucks().forEach(session::insert);
            request.getDrivers().forEach(session::insert);
            request.getOrders().forEach(session::insert);

            // Backward chaining facts — RejectionReason hierarchy
            insertRejectionReasons(session);

            // Backward chaining facts — OrderGroupMembership hierarchy
            insertOrderGroupMemberships(session);

            session.fireAllRules();

            return buildResult(session, messages);

        } finally {
            session.dispose();
        }
    }

    // Backward chaining fact tables 
    private void insertRejectionReasons(KieSession session) {
        session.insert(new RejectionReason("NoTruckAvailable",       "OrderUnassigned"));
        session.insert(new RejectionReason("InsufficientCapacity",   "NoTruckAvailable"));
        session.insert(new RejectionReason("AllTrucksBusy",          "NoTruckAvailable"));
        session.insert(new RejectionReason("NoDriverAvailable",      "OrderUnassigned"));
        session.insert(new RejectionReason("AllDriversOverHours",    "NoDriverAvailable"));
        session.insert(new RejectionReason("AllDriversFatigued",     "NoDriverAvailable"));
        session.insert(new RejectionReason("NoLicenseForTruckType",  "NoDriverAvailable"));
        session.insert(new RejectionReason("NoAdrLicense",           "NoDriverAvailable"));
        session.insert(new RejectionReason("RouteRestrictedForType", "OrderUnassigned"));
        session.insert(new RejectionReason("LargeTruckInCity",       "RouteRestrictedForType"));
        session.insert(new RejectionReason("BridgeCapacityExceeded", "RouteRestrictedForType"));
        session.insert(new RejectionReason("CargoOverweight",        "InsufficientCapacity"));
        session.insert(new RejectionReason("WinterReducesCapacity",  "InsufficientCapacity"));
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
        // Map cargo type names to group hierarchy
        session.insert(new OrderGroupMembership("REFRIGERATED", "SpecialOrder"));
        session.insert(new OrderGroupMembership("HAZARDOUS",    "SpecialOrder"));
        session.insert(new OrderGroupMembership("STANDARD",     "StandardDelivery"));
    }

    // Collect results from working memory

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
