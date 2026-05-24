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

    // -------------------------------------------------------
    // Backward chaining fact tables (from spec section 9)
    // -------------------------------------------------------

    private void insertRejectionReasons(KieSession session) {
        session.insert(new RejectionReason("NemaSlobodnogKamiona",       "NalogNedodeljen"));
        session.insert(new RejectionReason("NemaDovoljneNosivosti",      "NemaSlobodnogKamiona"));
        session.insert(new RejectionReason("SviKamioniZauzeti",          "NemaSlobodnogKamiona"));
        session.insert(new RejectionReason("NemaDostupnogVozaca",        "NalogNedodeljen"));
        session.insert(new RejectionReason("SviVozaciPrekoraciliSate",   "NemaDostupnogVozaca"));
        session.insert(new RejectionReason("SviVozaciUmorni",            "NemaDostupnogVozaca"));
        session.insert(new RejectionReason("NemaLicenceZaTipKamiona",    "NemaDostupnogVozaca"));
        session.insert(new RejectionReason("NemaADRLicence",             "NemaDostupnogVozaca"));
        session.insert(new RejectionReason("RutaZabranjenaTipu",         "NalogNedodeljen"));
        session.insert(new RejectionReason("GradskiKamionVeliki",        "RutaZabranjenaTipu"));
        session.insert(new RejectionReason("MostOgranicenaNosivost",     "RutaZabranjenaTipu"));
        session.insert(new RejectionReason("TeretPremasen",              "NemaDovoljneNosivosti"));
        session.insert(new RejectionReason("ZimaRedukujNosivost",        "NemaDovoljneNosivosti"));
    }

    private void insertOrderGroupMemberships(KieSession session) {
        session.insert(new OrderGroupMembership("DostavaMaloprodaja",    "DostavaStandardna"));
        session.insert(new OrderGroupMembership("DostavaVeleprodaja",    "DostavaStandardna"));
        session.insert(new OrderGroupMembership("DostavaStandardna",     "NalogKomercijalni"));
        session.insert(new OrderGroupMembership("DostavaHitna",          "NalogKomercijalni"));
        session.insert(new OrderGroupMembership("DostavaFrigoriferska",  "NalogSpecijalni"));
        session.insert(new OrderGroupMembership("DostavaOpasnaRoba",     "NalogSpecijalni"));
        session.insert(new OrderGroupMembership("NalogKomercijalni",     "NalogOpsti"));
        session.insert(new OrderGroupMembership("NalogSpecijalni",       "NalogOpsti"));
        // Map cargo type names to group hierarchy
        session.insert(new OrderGroupMembership("RASHLADNI",             "NalogSpecijalni"));
        session.insert(new OrderGroupMembership("OPASNA_ROBA",           "NalogSpecijalni"));
        session.insert(new OrderGroupMembership("STANDARDNO",            "DostavaStandardna"));
    }

    // -------------------------------------------------------
    // Collect results from working memory
    // -------------------------------------------------------

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
