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
        "rules/context.drl",
        "rules/validation.drl",
        "rules/truck-filtering.drl",
        "rules/driver-check.drl",
        "rules/scoring.drl",
        "rules/assignment.drl",
        "rules/domino.drl",
        "rules/accumulate.drl",
        "rules/backward-chaining.drl",
        "rules/cep/cep.drl"
    };

    // Tip kamiona × tip rute → score bonus (generisano iz template-a)
    private static final List<Map<String, Object>> TRUCK_TYPE_DATA = List.of(
        Map.of("truckType", "SMALL",  "routeType", "CITY",     "scoreBonus", 15),
        Map.of("truckType", "SMALL",  "routeType", "REGIONAL", "scoreBonus", 5),
        Map.of("truckType", "MEDIUM", "routeType", "REGIONAL", "scoreBonus", 15),
        Map.of("truckType", "MEDIUM", "routeType", "HIGHWAY",  "scoreBonus", 10),
        Map.of("truckType", "LARGE",  "routeType", "HIGHWAY",  "scoreBonus", 15)
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
        String generatedDrl = generateFromTemplate();
        kfs.write("src/main/resources/rules/truck-type-scoring.drl",
                ks.getResources()
                  .newByteArrayResource(generatedDrl.getBytes(StandardCharsets.UTF_8))
                  .setResourceType(ResourceType.DRL));

        // Kompajliraj
        KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
        Results results = kb.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Drools kompilacione greške:\n" + results);
        }

        return ks.newKieContainer(kb.getKieModule().getReleaseId());
    }

    private String generateFromTemplate() throws IOException {
        ObjectDataCompiler compiler = new ObjectDataCompiler();
        try (InputStream tpl = getClass()
                .getResourceAsStream("/rules/templates/truck-type.drt")) {
            if (tpl == null) {
                throw new IOException("Template nije pronađen: /rules/templates/truck-type.drt");
            }
            return compiler.compile(TRUCK_TYPE_DATA, tpl);
        }
    }
}
