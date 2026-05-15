# TruckDispatch — Ekspertni sistem za dispečiranje kamionskog saobraćaja

Projekat iz predmeta **Sistemi bazirani na znanju**, FTN Novi Sad, 2025/2026.

**Autor:** Milica Bosančić, SV60/2022  
**Ciljna ocena:** 10 (diplomski)

## O projektu

TruckDispatch je rule-based ekspertni sistem koji podržava dispečere pri upravljanju 
flotom kamiona različitih nosivosti i opreme. Sistem automatizuje dodelu naloga, 
detektuje incidente u realnom vremenu i dijagnostikuje razloge neuspelih dodela.

## Tehnologije

- Java 17 + Spring Boot 3.x
- Drools 9.x (KIE Server) — rule engine
- Drools Fusion — CEP
- PostgreSQL
- Angular

## Ključne funkcionalnosti

- Forward chaining — 6 nivoa (kontekstualizacija, validacija, filtriranje, provera vozača, score sistem, domino efekat)
- Backward chaining — rekurzivni query-ji sa stablima hipoteza
- CEP — detekcija obrazaca u realnom vremenu (after[], window:time())
- Accumulate — 7 funkcija (sum, count, average, collectList...)
- Template-i — parametrizovana pravila po tipu kamiona i rute
