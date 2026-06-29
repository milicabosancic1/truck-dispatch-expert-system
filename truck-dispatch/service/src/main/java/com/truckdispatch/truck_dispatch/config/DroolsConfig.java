package com.truckdispatch.truck_dispatch.config;

import org.drools.template.ObjectDataCompiler;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Configuration
public class DroolsConfig {

    private static final String[] DRL_FILES = {
        "rules/fc/context.drl",
        "rules/fc/validation.drl",
        "rules/fc/truck-filtering.drl",
        "rules/fc/driver-check.drl",
        "rules/fc/scoring.drl",
        "rules/fc/assignment.drl",
        "rules/fc/domino.drl",
        "rules/accumulate/accumulate.drl",
        "rules/bc/backward-chaining.drl",
        "rules/cep/cep.drl",
        "rules/cep/lifecycle.drl"
    };

    // Tip kamiona × tip rute → score bonus (generisano iz template-a)
    private static final List<Map<String, Object>> TRUCK_TYPE_DATA = List.of(
        Map.of("truckType", "SMALL",  "routeType", "CITY",     "scoreBonus", 15),
        Map.of("truckType", "SMALL",  "routeType", "LOCAL",    "scoreBonus", 10),
        Map.of("truckType", "SMALL",  "routeType", "REGIONAL", "scoreBonus", 5),
        Map.of("truckType", "MEDIUM", "routeType", "LOCAL",    "scoreBonus", 10),
        Map.of("truckType", "MEDIUM", "routeType", "REGIONAL", "scoreBonus", 15),
        Map.of("truckType", "MEDIUM", "routeType", "HIGHWAY",  "scoreBonus", 10),
        Map.of("truckType", "LARGE",  "routeType", "REGIONAL", "scoreBonus", 10),
        Map.of("truckType", "LARGE",  "routeType", "HIGHWAY",  "scoreBonus", 15)
    );

    // Prioritet naloga → alert nivo kada nalog čeka resurse (generisano iz template-a)
    private static final List<Map<String, Object>> ORDER_PRIORITY_DATA = List.of(
        Map.of("priorityEnum", "NORMAL",            "priorityLabel", "Normal",           "urgencyTag", "INFO"),
        Map.of("priorityEnum", "HIGH",              "priorityLabel", "High Priority",    "urgencyTag", "WARN"),
        Map.of("priorityEnum", "URGENT",            "priorityLabel", "Urgent",           "urgencyTag", "ALERT"),
        Map.of("priorityEnum", "CRITICAL_DELIVERY", "priorityLabel", "Critical Delivery","urgencyTag", "CRITICAL")
    );

    // Operativni kontekst → alert kada nalog kasni više od praga (generisano iz template-a)
    private static final List<Map<String, Object>> OPERATIONAL_MODE_DATA = List.of(
        Map.of("contextEnum", "MORNING_PEAK",      "contextLabel", "Morning Peak",      "severityTag", "WARN"),
        Map.of("contextEnum", "EVENING_PEAK",      "contextLabel", "Evening Peak",      "severityTag", "WARN"),
        Map.of("contextEnum", "NIGHT_MODE",        "contextLabel", "Night Mode",        "severityTag", "ALERT"),
        Map.of("contextEnum", "WEEKEND",           "contextLabel", "Weekend",           "severityTag", "INFO"),
        Map.of("contextEnum", "WINTER_CONDITIONS", "contextLabel", "Winter Conditions", "severityTag", "WARN")
    );

    @Bean
    public KieContainer kieContainer() throws IOException {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();

        // kmodule.xml
        try (InputStream kmodule = getClass().getClassLoader()
                .getResourceAsStream("META-INF/kmodule.xml")) {
            kfs.writeKModuleXML(new String(
                    kmodule.readAllBytes(), StandardCharsets.UTF_8));
        }

        // Učitaj sve ručno pisane DRL fajlove
        for (String path : DRL_FILES) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    kfs.write("src/main/resources/" + path,
                            ks.getResources()
                              .newByteArrayResource(is.readAllBytes())
                              .setResourceType(ResourceType.DRL));
                }
            }
        }

        // Generiši pravila iz DRL template-a i dodaj u KieFileSystem
        writeGeneratedDrl(kfs, ks, TRUCK_TYPE_DATA,       "/rules/templates/truck-type.drt",       "rules/truck-type-scoring.drl");
        writeGeneratedDrl(kfs, ks, ORDER_PRIORITY_DATA,   "/rules/templates/order-priority.drt",   "rules/order-priority-alerts.drl");
        writeGeneratedDrl(kfs, ks, OPERATIONAL_MODE_DATA, "/rules/templates/operational-mode.drt", "rules/operational-mode-alerts.drl");

        // Kompajliraj
        KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
        Results results = kb.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Drools kompilacione greške:\n" + results);
        }

        return ks.newKieContainer(kb.getKieModule().getReleaseId());
    }

    private void writeGeneratedDrl(KieFileSystem kfs,
                                   org.kie.api.KieServices ks,
                                   List<Map<String, Object>> data,
                                   String templatePath,
                                   String outputPath) throws IOException {
        ObjectDataCompiler compiler = new ObjectDataCompiler();
        try (InputStream tpl = getClass().getResourceAsStream(templatePath)) {
            if (tpl == null) {
                throw new IOException("Template nije pronađen: " + templatePath);
            }
            String generated = compiler.compile(data, tpl);
            kfs.write("src/main/resources/" + outputPath,
                    ks.getResources()
                      .newByteArrayResource(generated.getBytes(StandardCharsets.UTF_8))
                      .setResourceType(ResourceType.DRL));
        }
    }
}
