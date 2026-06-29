package com.truckdispatch.truck_dispatch.config;

import org.drools.template.DataProviderCompiler;
import org.drools.template.objects.ArrayDataProvider;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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

    private static final String CSV_TRUCK_TYPE       = "rules/templates/truck-type-data.csv";
    private static final String CSV_ORDER_PRIORITY   = "rules/templates/order-priority-data.csv";
    private static final String CSV_OPERATIONAL_MODE = "rules/templates/operational-mode-data.csv";

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
        writeGeneratedDrl(kfs, ks, loadCsvRows(CSV_TRUCK_TYPE),       "/rules/templates/truck-type.drt",       "rules/truck-type-scoring.drl");
        writeGeneratedDrl(kfs, ks, loadCsvRows(CSV_ORDER_PRIORITY),   "/rules/templates/order-priority.drt",   "rules/order-priority-alerts.drl");
        writeGeneratedDrl(kfs, ks, loadCsvRows(CSV_OPERATIONAL_MODE), "/rules/templates/operational-mode.drt", "rules/operational-mode-alerts.drl");

        // Kompajliraj
        KieBuilder kb = ks.newKieBuilder(kfs).buildAll();
        Results results = kb.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Drools kompilacione greške:\n" + results);
        }

        return ks.newKieContainer(kb.getKieModule().getReleaseId());
    }

    // Čita CSV fajl i vraća podatke kao 2D String niz (bez header reda).
    // Kolone su u istom redosledu kao template header promenljive.
    private String[][] loadCsvRows(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IOException("CSV nije pronađen: " + path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            reader.readLine(); // preskoči header red
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                rows.add(line.split(","));
            }
            return rows.toArray(new String[0][]);
        }
    }

    private void writeGeneratedDrl(KieFileSystem kfs,
                                   KieServices ks,
                                   String[][] data,
                                   String templatePath,
                                   String outputPath) throws IOException {
        DataProviderCompiler compiler = new DataProviderCompiler();
        ArrayDataProvider dataProvider = new ArrayDataProvider(data);
        try (InputStream tpl = getClass().getResourceAsStream(templatePath)) {
            if (tpl == null) {
                throw new IOException("Template nije pronađen: " + templatePath);
            }
            String generated = compiler.compile(dataProvider, tpl);
            kfs.write("src/main/resources/" + outputPath,
                    ks.getResources()
                      .newByteArrayResource(generated.getBytes(StandardCharsets.UTF_8))
                      .setResourceType(ResourceType.DRL));
        }
    }
}
