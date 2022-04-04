# sykepengesoknad-narmesteleder-varsler

Denne applikasjonen lytter på sykepengesoknad Kafka-topicet, og har to oppgaver:

## Opprettelse av  brukeroppgave i Dine Sykmeldte

En brukeroppgave vises som en rød prikk i Dine Sykmeldte. Dette skjer når det kommer sykepengesøknader som er sendt til
arbeidsgiver. Opprettelsen skjer ved at det gjøres et REST-kall `syfoservicestrangler` som oppdaterer `syfoservice`
-databasen.

## Varsling av nærmeste leder

Det sendes to typer varsler til nærmeste leder:

1. Varsel om at noen har sendt inn en sykepengesøknad. Planlegges umiddelbart.
2. Varsel om manglende innsending av sykepengesøknad. Planlegges sendt to uker etter søknaden er tilgjengelig for
   innsending. Dersom søknaden blir sendt eller avbrutt før det har gått to uker avbrytes varselet.

- Meldinger sendes kun mellom 09:00 og 15:00, mandag - fredag.
- Varsler sendes kun på e-post, og det gjøres ingen re-varsling.

Det kjøres en cronjob hvert 2. minutt som leser planlagte varsler. Hvis nærmeste leder (mottaker av
varselet) finnes i databasen sendes varslende på
Kafka-topic `teamdokumenthandtering.privat-dok-notifikasjon-med-kontakt-info` og status settes til `SENDT`. Hvis ingen
nærmeste leder er angitt oppdateres status til `INGEN_LEDER`.

## Oppdatering av nærmeste leder

Applikasjonen konsumerer Kafka-topicet `teamsykmelding.syfo-narmesteleder-leesah` og lagrer mottatt informasjon om nærmeste leder
i databasen. Det gjøres ingen synkrone kall for å oppdatere egne data.

## Hvordan tester?

1. Lag en testperson i Dolly. Vedkommende må ha et `arbeidsforhold`.
2. Registrer en nærmeste leder for vedkommende i `syfomock`. Personen kan være sin egen nærmeste leder.
3. Send inn en sykemelding. Man vil se i innsendingen om nærmeste leder er registrert korrekt.
4. Send inn en sykepengesøknad.
5. Verifiser at det sendes et varsel, gitt at testingen foregår i tidsrommet det sendes varsler.
6. Ved testing utenfor tidsrommet for sending av varslet eller teste varsel om **manglende sykepengesøknad** er det
   praktisk å oppdatere `sendes` i tabellen `narmeste_leder` til et tidspunkt tilbake i tid. Da vil varselet sendes ved
   neste hele femte minutt.

Rollen `cloudsqliamuser` har `UPDATE` tilgang i databasen i dev-gcp. Så dersom du bruker fremgangsmåten
fra `flex-cloud-sql-tools` vil du ha tilgang til å oppdatere tidspunkt for utsending med din personlig bruker.

Hvis e-post varselet ikke kommer ut så kan det være at `team-dokumentløsninger` har skrudd av testing mot Altinn, da må
man sjekke med dem om det er åpent eller ikke.

## Data

Applikasjonen har en database i GCP. Tabellen `planlagt varsel` holder oversikt over alle planlagte, avbrutte og sendte
varsler. Tabellen inkluderer fødselsnummer, orgnummer og sykpengesøknad_id og er derfor personidentifiserbar. Det
slettes ikke data fra tabellen.

Tabellen `narmeste leder` holder oversikt over alle **aktive** nærmesteleder-relasjoner og forskutteringsstatus for
nærmesteleder-skjemaet. Dataene er personidentifiserbare. Det slettes ikke fra tabellen.

# Utvikling

Applikasjonen er en Spring Boot Kotlin applikasjon som bygges med Gradle:

```sh
$ ./gradlew clean build
```

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan sendes på e-post til `flex@nav.no`. Interne henvendelser i NAV kan
sendes via Slack i kanalen `#flex`.
