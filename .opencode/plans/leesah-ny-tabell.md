# Migrer Leesah-lesing til egen tabell før bytte av varselskilde

## Status etter implementering i backend-repoet

- `V6__narmeste_leder_esyfo.sql` oppretter tom projeksjon og varig checkpoint per topic/partisjon. V5 og eldre migreringer er uendret. Etter review mot `origin/main` er gammel writer rettet tilbake til aktiv insert/update og inaktiv delete/ignorering, uten timestamp-filter. Parserfeil og logger er fortsatt sanitert.
- Ny consumer bruker egen gruppe, `earliest` og sekvensiell behandling per partisjon. Den er av som standard. Oppstart avvises hvis gruppen er blank eller identisk med gammel gruppe, eller hvis ESYFO-lesing er valgt med consumer av. Ny consumer avviser null eller ikke-kanonisk UUID-key uten ack; den skriver bare projeksjon og checkpoint i én transaksjon og ack-er etter commit.
- Felles Kafka-feilhåndtering beholder saniterte logger. Begge listener-fabrikker bruker igjen `origin/main` sin `ExponentialBackOff(1000L, 1.5)` med maksimal pause på åtte minutter. Standard `maxElapsedTime` gir fortsatt ubegrenset retry.
- `NarmesteLederLeser` velger én tabell for begge varseltyper og filtrerer inaktive rader også på gammel bane. Ingen fallback eller varsling fra bootstrap-listeneren. PII-frie tellere og målere dekker behandlingsutfall, checkpoint og aktive/inaktive rader; lag, end-offset-barriere og avvik må måles mot Kafka og kildene før bytte.
- Enhetstester og integrasjonstester dekker Kafka-bootstrap, isolasjon, tombstone, duplikater, omstart, samtidighet, SQL-rollback, begge varseltyper og default gammel lesing. Tester kontrollerer at gammel inaktiv melding sletter eller ikke oppretter rad, mens ny bane beholder inaktiv rad uten å velge den som mottaker. Null/kort key og ugyldig gruppe testes uten checkpoint-flytting eller ack. IDE-kjørte `Migration ktlintFormat`, `Migration ktlintCheck` og `Migration build` var grønne etter rettingen. Full build kjørte 39 tester uten feil.
- **Ikke aktivert i dev/prod.** ACL, første tilgjengelige offset, `delete.retention.ms`, dekning, end-offset-barriere, speil-ajourføring, avviksanalyse, pod-overlapp og review fra kodeeier/produsent gjenstår. Høyere Kafka-offset med eldre tilstand kan midlertidig reaktivere en avkreftet leder. Varsling fra ny bane er no-go til sikker policy er godkjent.

## Mål og faste valg

- Denne planen gjelder kun `sykepengesoknad-narmesteleder-varsler`. Backend-endringen kan bygges og deployes uten endringer i andre repoer.
- Behold `teamsykmelding.syfo-narmesteleder-leesah` mot eksisterende `narmeste_leder` gjennom hele oppbyggingen og rollback-vinduet.
- Les `team-esyfo.syfo-narmesteleder-leesah` fra tidligste tilgjengelige offset til en egen, først tom tabell `narmeste_leder_esyfo`.
- Bruk egen consumer-gruppe, repository og skrivebane for ny projeksjon. Gammel consumer skriver aldri til ny tabell, ny consumer skriver aldri til gammel.
- Ikke kopier gammel tabell, dual-write, bruk fallback/UNION eller send varsler fra ny consumer under oppbyggingen.
- Velg lesekilde eksplisitt med konfigurasjon som standard er «gammel». Bytt alle lesere samlet; ikke gi gammel tabell nytt navn mens gamle pods kan kjøre.
- La begge consumers gå i rollback-vinduet. Stopp gammel consumer i et eget senere steg: da blir gammel tabell gradvis foreldet.

## Utgangspunkt før implementering

- Lokal `split/narmesteleder-esyfo` er på `a59e79d04d2d7d261d1405ffff20043a3f445290`; arbeidskopien har allerede endret `.gitignore` og utrackede `.codegraph/`. La dem være urørt.
- Branch-diffen mot `main` endrer `KafkaErrorHandler.kt`, `NarmestelederListener.kt`, `NarmesteLederRepository.kt`, `OppdateringAvNarmesteLeder.kt`, begge domenemodellene, `VarselUtsendelse.kt`, to tester og legger til `V5__narmeste_leder_aktiv_tom.sql`.
- Ved planstart brukte `NarmestelederListener.listenEsyfo` samme `listen`/skrivemetode som gammel consumer; begge oppdaterte `narmeste_leder`. Den nye listeneren satte `auto.offset.reset=latest`. Dette er erstattet av den separate banen beskrevet i implementeringsstatusen.
- `KafkaConfig.kt` bruker felles gruppe `sykepengesoknad-narmesteleder-varsler`, `auto-offset-reset: none` i `application.yaml`, manuell umiddelbar ack og `MAX_POLL_RECORDS=1`. Testprofilen bruker earliest.
- Listenerens `id="narmesteleder-esyfo"` med `idIsGroup=false` er ikke alene en separat consumer-gruppe: sett og test eksplisitt gruppe-ID for ny consumer.
- `OppdateringAvNarmesteLeder.kt` gjør SQL-upsert bare når `narmeste_leder.timestamp < EXCLUDED.timestamp`. Dette er ikke kontraktens «siste record per key» ved like tidsstempler eller produsent-retry.
- Gammel lagring i `main` slettet inaktive rader. Branch-diffen innfører `aktiv_tom` og beholder inaktive rader; `VarselUtsendelse.kt` velger bare rader med `aktiv_tom IS NULL`.
- `V2__create_table_nl.sql` oppretter `narmeste_leder` med unik `narmeste_leder_id`, `timestamp`, kontaktfelt og indeks på `bruker_fnr`; branchens `V5` legger til `aktiv_tom`.
- `NarmesteLederRepository.kt` har både ufiltrert `findByBrukerFnrAndOrgnummer` og aktiv-filtrert `findAktiveByBrukerFnrAndOrgnummer`; gjennomgå alle kall før bytte.
- `CronJob.kt` kjører `VarselUtsendelse.sendVarsler()` hvert andre minutt på leader-pod. Utsendelsen henter `PLANLAGT` fra `PlanlagtVarselRepository.kt`, slår opp leder, sender doknotifikasjon og eventuelt Dine sykmeldte-hendelse.
- `VarselPlanlegger.kt` oppretter og avbryter planlagte varsler fra søknadsstrømmen via `SykepengesoknadListener.kt`; den leser ikke ledertabellen. `sendFerdigstillHendelseTilDineSykmeldte` følger søknadshendelser, ikke ny Leesah-consumer.
- `SendtSykepengesoknadVarsel.kt` og `ManglendeSykepengesoknadVarsel.kt` bygger kontaktmelding fra valgt `NarmesteLeder`; `PlanlagtVarsel` lagrer valgt leder-ID. Ingen andre direkte leder-repository-lesere ble funnet i `src/main`.
- `nais/app/dev.yaml` og `nais/app/prod.yaml` har én replika hver; `nais/app/naiserator.yaml` legger Kafka på valgt pool. Verifiser likevel rolling deploy og lederskifte som om gamle og nye pods overlapper.
- Remote PR #588 (`narmesteleder-esyfo-migrering`) har også en `AGENTS`-endring; den lokale branchen er kode-only oppå nyere `main`. Docs #589 er separat. Ikke endre Git-historikk, bytt branch eller forsøk force-push.

## Håndtering av eksisterende PR-endringer

- Ikke behold `listenEsyfo` som videresender til gammel `listen`: split ut ny listener og skriv kun til `narmeste_leder_esyfo`.
- Fjern `properties = ["auto.offset.reset=latest"]` for ny listener og erstatt med earliest i en ny, eksplisitt bootstrap-gruppe. La aldri produksjonens generelle `none`-innstilling styre denne første lesingen.
- Ikke gjenbruk gammel SQL-upsert med strikt sammenligning av `timestamp` for ny projeksjon. Behandle også records med lik eller eldre publiseringstid når Kafka-offset er høyere.
- Behold `@JsonIgnoreProperties(ignoreUnknown = true)` eller tilsvarende faktisk virkende Jackson-konfig for meldinger fra ny kilde; unngå statusfilter som utelater relasjoner.
- Behold gammel skrive-/lesesemantikk fra før PR-en gjennom migreringen. Bevaring av inaktive rader og aktiv-filtrering innføres på ny bane. Hvis endret gammel bane allerede er deployet, avklar håndtering av eksisterende inaktive rader før videre deploy eller rollback; ikke send dem gjennom en ufiltrert gammel leser.
- Vurder `V5` som mulig anvendt uansett om den er ny i lokal branch. Flyway skal ha én uforanderlig migrasjonshistorikk i hvert miljø; legg eventuelle justeringer i etterfølgende versjon.
- Feillogggrenen i `KafkaErrorHandler` er nødvendig for å unngå PII fra record-key eller exception. Gjennomgå også vanlige logger fra begge consumers, ikke bare denne handleren.
- Ikke bland dokumentasjons-PR #589 eller endringen av `AGENTS` i denne backend-planen. Eksisterende `.gitignore` og `.codegraph` skal ligge urørt.

## Kontrakt og beslutningsgrense

- Kilde: [team-esyfos Leesah-kontrakt](https://github.com/navikt/esyfo-narmesteleder/blob/main/docs/kafka/syfo-narmesteleder-leesah.md). Original key og JSON-verdi speiles uendret etter validering; offsetene på de to topicene er forskjellige.
- Kafka-key er stabil UUID (`narmesteLederId`) på samme partisjon. Hver ikke-null record har hele relasjonstilstanden; siste record per key er gjeldende tilstand. Det er ingen rekkefølge på tvers av keys.
- Leveringen er at least once. Produsent-retry kan midlertidig publisere eldre tilstand etter nyere, også med en ny Kafka-offset; siste record skal etter hvert være riktig.
- Null-verdi er tombstone: slett raden i ny projeksjon etter key. Ikke behold en gammel rad som fallback. Checkpoint må overleve sletting.
- Topicet er compact med ubegrenset retention og min lag omtrent 31 dager, max lag omtrent 35 dager. Tombstones kan likevel forsvinne: få bekreftet `delete.retention.ms` og fullfør rebuild før dette vinduet.
- Speilet har kun validerte ikke-null records. `orgnummer` er alltid ni siffer; øvrige felt følger kontrakten. Ukjente JSON-props og ukjent/null `status` må tåles uten at ny consumer stopper.
- Vi forutsetter at den nødvendige siste tilstanden finnes i nytt topic. Kontroller bootstrap-dekning og at speilet er ajour før bytte; lov aldri full historikk eller likhet mellom gamle og nye offsets.
- Kompaksjon kan ha fjernet mellomliggende records. En tom projeksjon må derfor bygges fra de tilgjengelige siste records, ikke ved å forutsette at alle gamle endringshendelser finnes.
- Tombstone uten tilgjengelig tidligere verdi skal fortsatt flytte checkpoint. Når tombstones er fjernet av kompaksjon, må det avklares om fraværende keys er riktig tilstand før switch.
- Behandle offsetene for nytt topic som egne identifikatorer; en offset fra gammelt topic kan ikke brukes som avstemming, checkpoint eller reset-verdi på nytt topic.
- **🔴 Rød sone, go/no-go:** Hvis varsling ikke tåler at en avkreftet leder blir midlertidig aktiv igjen ved produsent-replay, må kodeeier og produsent løse dette før read-switch. Rebuild, DB-checkpoint og et ugarantert `timestamp`-filter gir ikke live-sikkerhet.
- Dokumenter hvilken sikkerhetsregel og produsentgaranti som faktisk brukes før bytte. Avvis switch hvis midlertidig feil mottaker ikke er akseptabelt; ikke skjul dette bak overvåking.

## Gjennomføringsrekkefølge i dette repoet

1. Avklar med kodeeier hva som skal beholdes fra branch/PR #588 før ny implementering; ikke gjør endringen som en tabell-rename eller som en ren konfigurasjonsendring.
2. Behold gammel consumer i `NarmestelederListener.kt` og gammel skrivebane til `narmeste_leder`; sørg for at gammel listener fortsatt eier bare gammelt topic.
3. Behold PII-trygg logggren i `KafkaErrorHandler.kt`: ingen key, verdi, persondata eller exception med payload i lederfeil. Dette er et loggvalg, ikke en ordningsgaranti; vurder resten av feilhåndteringen separat.
4. Ta endringene som bevarer inaktive rader og innfører ny timestamp-upsert ut av gammel skrivebane hvis de ikke er deployet. Gammel bane skal beholde før-PR-semantikken; inaktive felt og aktiv-filtrering trengs på **ny** bane. Ved allerede deployede endringer kreves egen kompatibilitetsvurdering av eksisterende rader før tilbakeføring.
5. Sjekk Flyway-historikken ved implementering. `V5__narmeste_leder_aktiv_tom.sql` kan være deployet: aldri rediger en anvendt migrasjon. Velg neste ledige versjon ut fra faktisk historikk.
   Hvis `V5` ikke er anvendt i noe relevant miljø, erstatt PR-ens endring av gammel tabell med migreringen for ny tabell. Ellers behold migrasjonshistorikken uendret.
6. Lag en ny Flyway-migrasjon under `src/main/resources/db/migration/` for tom `narmeste_leder_esyfo` med unik ikke-null leder-ID, brukernøkkel, orgnummer, kontaktfelt, datoer, `aktiv_tom` og relevante indekser for aktivt oppslag. Behold teknisk `id`/ID-generering dersom eksisterende Spring Data-mapping gjenbrukes.
7. Lag en egen checkpoint-tabell, for eksempel `narmeste_leder_esyfo_offset`, med primærnøkkel `(topic, partition)` og lagret siste ferdigbehandlede offset. Ikke legg checkpoint på relasjonsradene.
8. Innfør en egen modell og repository/skrivekomponent for `narmeste_leder_esyfo` i `src/main/kotlin/no/nav/helse/flex/narmesteleder/`. La nytt oppslag kunne levere samme data til varseldomenet uten å endre avsendingskontrakten.
9. Håndter `NarmesteLederLeesah.kt` slik at nytt topic tåler ekstra props og ukjent/null `status`; ikke bruk en uttømmende enum som avviser fremtidige statuser. Bekreft JSON-mapperens faktiske konfigurasjon.
10. Lag egen ny listener i `src/main/kotlin/no/nav/helse/flex/kafka/`. Sett eksplisitt, unik consumer-gruppe og earliest for **ny gruppe uten committed offsets**. Gammel consumer beholder gruppe og topic.
11. Behandle hver ny Kafka-record i partisjonsrekkefølge, synkront. Ikke parallelliser eller sorter per key på tvers av offsets; ack først når DB-commit er gjennomført.
12. Åpne én DB-transaksjon per record: opprett checkpoint-rad ved behov med `INSERT ... ON CONFLICT DO NOTHING`, lås raden med `SELECT ... FOR UPDATE`, og avvis offset `<=` siste lagrede for samme `(topic, partition)`.
13. Hvis offset er ny: upsert hele den validerte ikke-null tilstanden etter Kafka-rekkefølge, eller slett raden ved tombstone; oppdater checkpoint i **samme** transaksjon. Ved feil rulles begge tilbake, uten ack.
14. Lås også ved første insert-race på flere pods; test at første tomme checkpoint ikke kan tillate en eldre writer å overskrive nyere tilstand. Bruk betinget oppdatering eller serialisert radlås, ikke «finnes ikke, så skriv».
15. Bruk UUID-key til tombstone og sjekk samsvar mellom key og verdi på ikke-null records uten å logge dem. Avklar feilpolicy for ugyldig key eller mismatch med eier; ingen stille ack av en ukjent endring.
16. Ikke bruk `timestamp < EXCLUDED.timestamp` på ny bane. Ny offset ved produsent-replay kan midlertidig reaktivere en rad; checkpoint hindrer bare gjentatt/eldre **Kafka-offset**, ikke denne hendelsen.
17. Ikke legg produsentkall, varselsending eller annen forretningssideeffekt i ny listener. Under bootstrap skriver den kun ny projeksjon og checkpoint.
18. Velg «gammel» som default for en eksplisitt read-switch i `application.yaml` og testoppsett; legg valg av repository bak én avgrenset leser brukt av `VarselUtsendelse.kt`.
19. La ny lesebane filtrere `aktiv_tom IS NULL`; gammel bane beholder sin før-PR-semantikk der avkreftede relasjoner slettes. Hvis gamle rader allerede beholdes som inaktive, kreves aktiv-filter der også til denne tilstanden er håndtert. Se over begge repository-metoder slik at ingen runtime-leser omgår kildevalget.
20. Sikre at valg av tabell gjelder varsling fra `CronJob` og hele `VarselUtsendelse`-flyten: `SENDT_SYKEPENGESOKNAD` og `MANGLENDE_SYKEPENGESOKNAD`, doknotifikasjon, Dine sykmeldte-oppgave og lagret leder-ID.
21. Oppdater test-fixtures og integrasjonstester i `src/test/kotlin/no/nav/helse/flex/OppdaterNarmesteLederTest.kt`, `VarselTest.kt` og ved behov `FellesTestOppsett.kt` / `kafka/TestKafkaBeans.kt`. Verifiser gruppe-ID og testisolasjon.

### Detaljer for transaksjon og lesing

- Skriv den høyeste fullførte offseten først etter at upsert eller delete er utført i samme transaksjon. Ved duplikat/eldre offset gjør ingen tabellendring; listeneren kan ack-e etter at det er konstatert trygt.
- Behandle partisjoner uavhengig: låsen tas per `(topic, partition)`. Den serialiserer DB-transaksjoner, men gjenoppretter ikke feil applikasjonsrekkefølge. Behold én sekvensiell behandlingsbane per tildelt partisjon, også ved rebalance, og test at en tidligere upåført record ikke hoppes over fordi en senere ble startet parallelt. Unngå global checkpoint. Kompaktering gir lovlige offset-hull, så ikke krev fortløpende heltall.
- En tombstone har ingen JSON-verdi å hente ID fra. Slett etter gyldig Kafka-key, og behold checkpoint også når tilhørende relasjon ikke finnes i tabellen.
- Ikke hent et `MAX(offset)` fra Kafka og skriv det direkte som DB-checkpoint. Barrieren er oppfylt først når forretningsdata og checkpoint er atomisk skrevet frem til offseten.
- Hvis ack eller Kafka-commit feiler etter DB-commit, kommer recorden tilbake. DB-checkpoint gjør duplikatet til en ufarlig no-op; Kafka og DB har ingen felles transaksjon.
- Skill mellom syntaktisk ugyldig record og ukjent, men gyldig `status`. Avtal håndtering av faktisk ugyldig data med produsent før deploy; ikke dropp en record som trengs for riktig siste tilstand.
- Når read-switch står på gammel, må ny repository ikke være kandidat for implicit autowiring av den eksisterende leseren. Gjør kildevalget synlig, deterministisk og testbart.
- Ved switch er det varselschedulerens oppslag etter `brukerFnr` og `orgnummer` som avgjør mottaker. Ikke endre kriteriene for planlagte varsler eller sender-funksjonene som en skjult sideeffekt av tabellvalget.

## Tester og kontroll før aktivering

- Verifiser at et ferskt nytt topic fra earliest bygger ny tabell, mens gammel tabell og varselsending fortsatt bruker gammel consumer. Ingen doknotifikasjon, Dine sykmeldte-event eller planlagt varsel skal skapes av replay alene.
- Test aktiv `aktivTom=null` mot inaktiv `aktivTom!=null`, inkludert inaktiv første record, oppdatering og reaktivering i loggrekkefølge.
- Test ukjente props, ukjent/null `status`, ni-sifret orgnummer og original key/value-format uten å logge payload. Deaktivering må ikke avhenge av et bestemt statusnavn.
- Test tombstone etter opprettelse, tombstone på fraværende key og tombstone ved restart: slettet rad forblir slettet når samme/eldre offset kommer igjen.
- Test samme offset levert to ganger, eldre offset etter nyere, like/eldre `timestamp` ved høyere offset, og produsent-replay med **ny** offset som midlertidig kan reaktivere før siste record retter tilstanden.
- Fremprovoser feil mellom upsert/delete og checkpoint, og motsatt; krev atomisk rollback, ingen ack før commit og riktig tilstand etter restart/rebalance.
- Test to samtidige pods mot tom checkpoint-rad, overtakelse av partisjon og serialisering uten at eldre offset skriver over ny. Ingen distribuert Kafka/DB-transaksjon er lovet.
- Test at begge grupper kan gå samtidig uten krysskriving; aktiv-leser, default gammel, eksplisitt ny og rollback til gammel må gi rett mottaker og tilstand for begge varseltyper.
- Test negativt at ny consumer ikke utfører varselsending eller oppgaveopprettelse under bootstrap, også ved tombstone, duplikat og feil/retry.
- Kontroller at gammel consumer fortsatt behandler sine records når ny consumer er stanset, bakpå eller mangler ACL. Ny feil skal ikke gi stilltiende bytte av varselskilde.
- Sjekk at ingen ny eller gammel listener logger Kafka-key, verdi eller persondata ved parserfeil, SQL-feil og gjentatte leveringer.
- Verifiser at én aktiv og én inaktiv leder for samme arbeidstaker og virksomhet gir én mottaker; flere aktive skal fortsatt gi eksisterende feilbane fremfor tilfeldig valg.
- Kontroller en varselsending rett før og etter bytte, inkludert `INGEN_LEDER`, `SENDT`, lagret `narmesteLederId` og tilsvarende rollback uten at leseren faller tilbake til feil tabell.
- Kjør format, bygg og relevante enhets-/integrasjonstester via IntelliJ MCP `execute_run_configuration`. Finn faktiske run configuration-navn ved implementering; ikke bruk shell til prosjekt-scripts.

## Deploy, go/no-go og rollback

- Be team-esyfo om read-ACL på `team-esyfo.syfo-narmesteleder-leesah` for denne appen i både dev og prod; bekreft faktisk tilgang før consumer aktiveres i hvert miljø.
- Opprett ny gruppe uten committed offsets. `auto.offset.reset=earliest` flytter ikke en gruppe som allerede har commits. Ved tidligere commits: lag kontrollert ny bootstrap-gruppe og tom projeksjon etter avtale, ikke blind reset eller replay i ikke-tom tabell.
- Deploy migrasjon og begge consumers mens read-switch står på gammel. Behold gammel consumer og varselscheduler som før; ikke la bootstrap sende varsler fra ny tilstand.
- Ta et end-offset-snapshot per partisjon på nytt topic som barriere. Grensene er eksklusive: kontroller at consumer-posisjonen har passert tilgjengelige records før dem og at alle er DB-committet uten utestående behandling. Checkpoint er siste faktisk behandlede record, ikke nødvendigvis `endOffset - 1` ved tomme partisjoner eller kompakterte hull; test begge tilfeller og skriv aldri barrieren direkte til checkpoint. Kontroller separat at speilingen fra gammelt topic er ajour. Kafka-lag lik null alene beviser ikke dekning.
- Sammenlign aggregater og målrettede, tilgangsstyrte tilstandskontroller for aktive/inaktive relasjoner mellom kildene uten å eksportere persondata. Undersøk avvik og tombstone-dekning; verifiser `delete.retention.ms` og tidsvindu før switch.
- Publiser PII-frie metrikker for lag per partisjon, checkpoint/barriere, feil, tombstones, aktive/inaktive antall og avvik; ikke ha UUID, fnr, e-post, key eller payload som label/logg.
- Review-gate før switch: kodeeier for varsler og team-esyfo bekrefter dekning, ACL, tombstone-vindu, replay-policy og at ny projeksjon er ajour. Hvis midlertidig reaktivering kan gi feil varselmottaker, er dette **no-go** til produsent-/kodeierløsningen er avklart.
- Bytt eksplisitt lesekilde til ny først etter gate og verifiser begge varseltyper og lederskifte. Hold begge consumers i drift i avtalt rollback-vindu. Unngå ulike leseflagg på samtidige pods ved rolling deploy.
- Et koordinerbart skifte må omfatte cronjob og alle dens samtidige pods: enten sikre felles kildevalg gjennom overgangsvinduet eller stanse utsendelse mens ulike versjoner overlapper. Verifiser valgt fremgangsmåte før prod.
- Mål ny consumer etter switch også. At den var ajour ved barrieren betyr ikke at fremtidig lag, feilet tombstone eller produsent-replay er ufarlig for utsending.
- Før beslutning om å slå av gammel consumer, dokumenter hvordan rollback skal håndteres når gammel tabell ikke lenger oppdateres. Ikke behold en konfigurerbar gammel lesekilde som ser frisk ut, men er foreldet.
- Ved feil: sett lesekilde tilbake til gammel og verifiser at gammel consumer fortsatt er ajour. Ikke rull tilbake DB-skjema under kjørende pods; ikke slett ny checkpoint eller reset Kafka-gruppe som del av akutt rollback.
- Verifiser dessuten at produsenten fortsatt leverer nødvendige endringer på gammelt topic. Null lag på et tausnet topic beviser ikke fersk tilstand. Koordiner eSyfos produsentovertakelse med rollback-vinduet; ikke bytt tilbake til en tabell som har sluttet å få oppdateringer selv om gammel consumer fortsatt kjører.
- Eget senere steg etter godkjent rollback-vindu: stopp gammel consumer eksplisitt, informer om at gammel tabell blir foreldet, og planlegg eventuell opprydding separat. Ingen cleanup eller historikkendring i denne planen.
