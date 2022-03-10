# sykepengesoknad-narmesteleder-varsler


## Opprettelse av brukeroppgave (rød prikk) i Dine Sykmeldte
Dette gjøres når det kommer sendte sykepengesøknader som er sendt arbeidsgiver.
Opprettelsen skjer med et API kall via flex-fss-proxy til syfoservice strangler som oppdaterer syfoservicedatabasen.
Dette vil på sikt skje på en annen måte når nye dinesykmeldte er klar.

## Utsendelse av varsler til nærmeste leder
Det sendes typer varsler til nærmeste leder. 
Varsel om en sendt sykepengesøknad planlegges sendt med en gang når det kommer inn en sendt sykepengesøknad.
Varsel om manglende sykepengesøknad planlegges sendt to uker at søknaden har blitt tilgjengelig for innsendelse.
Dersom søknaden blir sendt eller avbrutt innen utsendelses tidspunktet avbryter vi varselet om 

Alle utsendelsestidspunkt flyttes fremover til mellom 9 og 15 mandag til fredag.

Alle varsler sendes kun som epost. Det er ingen revarsler.

Det går en cronjob hvert femte minutt som finner planlagte varsler som skal sendes. 
Hvis vi finner nærmeste leder i databasen sendes varselet med en kafkamelding på `teamdokumenthandtering.privat-dok-notifikasjon-med-kontakt-info` .
Statusen endres da til SENDT. 

Hvis vi ikke finner en nærmeste leder oppdateres status i databasen til INGEN_LEDER


## Oppdatering av nærmeste leder
Applikasjonen har en kopi i databasen av alle aktive nærmeste ledere. Appen konsumerer `teamsykmelding.syfo-narmesteleder-leesah` og oppdaterer den lokale kopien slik at vi slipper å gjøre synkrone kall til nærmeste leder appen.
Logikken er lik som team sykmelding sin app for nærmeste leder varsler


## Hvordan tester jeg dette?
Lag en testperson i dolly med et arbeidsforhold. Deretter registerer du en nærmesteleder i syfomock på den personen med din egen epost addresse. Personen kan være sin egen nærmeste leder.
Send deretter inn en sykmelding, du vil se i innsendelsen om du har klart å registerer nærmeste leder korrekt. 
Du kan så sende inn en søknad, varselet vil da gå ut iløpet av 5 minutter hvis klokka er mellom 9 og 15 på en hverdag. 

Skal du teste utenfor kl 9 eller teste manglende sykepengesøknad varselet er det praktisk å oppdatere `sendes` i tabellen `narmeste_leder` til å være tilbake i tid. 
Da vil varselet sendes ved neste hele femte minutt.

Rollen `cloudsqliamuser` har UPDATE tilgang i databasen i dev-gcp. Så dersom du bruker fremgangsmåten fra `flex-cloud-sql-tools` vil du ha tilgang til å oppdatere utsendelsetidspunktet med personlig bruker.

Hvis epost varselet ikke kommer ut så kan det være at team-dokumentløsninger har skrudd av testing mot altinn, da må man sjekke med dem om det er åpent eller ikke.

## Data
Applikasjonen har en database i GCP.

Tabellen `planlagt varsel` holder oversikt over alle planlagte, avbrutte og sendte varsler.
Tabellen inkluderer fødselsnummer, orgnummer og sykpengesøknad_id og er derfor personidentifiserbar. Det slettes ikke data fra tabellen.

Tabellen `narmeste leder` holder oversikt over alle **aktive** nærmesteleder relasjoner og forskutteringsstatus fr anærmeste leder skjemaet.
Dataene er personidentifiserbare.
Det slettes ikke fra tabellen.


# Komme i gang

Bygges med gradle. Standard spring boot oppsett.

---

# Henvendelser


Spørsmål knyttet til koden eller prosjektet kan stilles til flex@nav.no

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #flex.
