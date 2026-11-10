package no.nav.helse.flex

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.date.shouldBeBefore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.doknotifikasjon.schemas.PrefererteKanal
import no.nav.helse.flex.dinesykmeldte.OPPGAVETYPE_IKKE_SENDT_SOKNAD
import no.nav.helse.flex.dinesykmeldte.tilDineSykmeldteHendelse
import no.nav.helse.flex.narmesteleder.OppdateringAvNarmesteLeder
import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederLeesah
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO.SENDT
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.varsler.MANGLENDE_VARSEL_EPOST_TEKST
import no.nav.helse.flex.varsler.MANGLENDE_VARSEL_TITTEL
import no.nav.helse.flex.varsler.SENDT_SYKEPENGESOKNAD_EPOST_TEKST
import no.nav.helse.flex.varsler.SENDT_SYKEPENGESOKNAD_TITTEL
import no.nav.helse.flex.varsler.VarselUtsendelse
import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.AVBRUTT
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.INGEN_LEDER
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.PLANLAGT
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.SENDT_SYKEPENGESOKNAD
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VarselTest : FellesTestOppsett() {
    @Autowired
    lateinit var varselUtsendelse: VarselUtsendelse

    @Autowired
    lateinit var oppdateringAvNarmesteLeder: OppdateringAvNarmesteLeder

    final val orgnummer = "999111555"
    val soknad =
        SykepengesoknadDTO(
            fnr = fnr,
            id = UUID.randomUUID().toString(),
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            status = SoknadsstatusDTO.NY,
            arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
            arbeidsgiver = ArbeidsgiverDTO(navn = "Bedriften AS", orgnummer = orgnummer),
        )

    @Test
    @Order(0)
    fun `Arbeidsledig, frilanser og sånt skaper ikke planlagt varsel`() {
        planlagtVarselRepository
            .findAll()
            .iterator()
            .asSequence()
            .toList()
            .isEmpty()

        val arbeidsledig =
            SykepengesoknadDTO(
                fnr = fnr,
                id = UUID.randomUUID().toString(),
                type = SoknadstypeDTO.ARBEIDSLEDIG,
                status = SoknadsstatusDTO.NY,
                arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSLEDIG,
            )
        val frilanser =
            SykepengesoknadDTO(
                fnr = fnr,
                id = UUID.randomUUID().toString(),
                type = SoknadstypeDTO.SELVSTENDIGE_OG_FRILANSERE,
                status = SoknadsstatusDTO.NY,
                arbeidssituasjon = ArbeidssituasjonDTO.FRILANSER,
            )

        sendSykepengesoknad(arbeidsledig)
        sendSykepengesoknad(frilanser)

        await().during(3, SECONDS).until {
            planlagtVarselRepository
                .findAll()
                .iterator()
                .asSequence()
                .toList()
                .isEmpty()
        }

        planlagtVarselRepository
            .findAll()
            .iterator()
            .asSequence()
            .toList()
            .shouldBeEmpty()
    }

    @Test
    @Order(1)
    fun `Vi mottar en søknad med status NY og planlegger et manglende søknad varsel`() {
        planlagteVarslerSomSendesFor(dager = 20).size shouldBe 0

        sendSykepengesoknad(soknad)

        await().atMost(5, SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadId(soknad.id).size == 1
        }

        val planlagtVarsel = planlagtVarselRepository.findBySykepengesoknadId(soknad.id).first()
        planlagtVarsel.brukerFnr shouldBe soknad.fnr
        planlagtVarsel.sykepengesoknadId shouldBe soknad.id
        planlagtVarsel.orgnummer shouldBe soknad.arbeidsgiver!!.orgnummer
        planlagtVarsel.varselType shouldBe MANGLENDE_SYKEPENGESOKNAD
        planlagtVarsel.status shouldBe PLANLAGT
        planlagtVarsel.sendes.shouldBeBefore(Instant.now().plus(17L, ChronoUnit.DAYS))
        planlagtVarsel.sendes.shouldBeAfter(Instant.now().plus(13L, ChronoUnit.DAYS))

        planlagteVarslerSomSendesFor(dager = 20).size shouldBe 1
    }

    @Test
    @Order(2)
    fun `Vi mottar en søknad med status SENDT og avbryter manglende søknad varsel og planlegger SENDT søknad varsel `() {
        mockPdlResponse()
        planlagtVarselRepository.findBySykepengesoknadId(soknad.id).size shouldBe 1
        planlagteVarslerSomSendesFor(dager = 20).size shouldBe 1
        planlagteVarslerSomSendesFor(dager = 3).size shouldBe 0
        val soknaden = soknad.copy(status = SENDT, sendtArbeidsgiver = LocalDateTime.now())
        sendSykepengesoknad(soknaden)

        await().atMost(5, SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadIdAndStatus(soknad.id, AVBRUTT).isNotEmpty()
        }

        val planlagteVarsler = planlagtVarselRepository.findBySykepengesoknadId(soknad.id)
        planlagteVarsler.size shouldBe 2

        val planlagtVarsel = planlagteVarsler.first { it.status == PLANLAGT }
        planlagtVarsel.brukerFnr shouldBe soknad.fnr
        planlagtVarsel.sykepengesoknadId shouldBe soknad.id
        planlagtVarsel.orgnummer shouldBe soknad.arbeidsgiver!!.orgnummer
        planlagtVarsel.varselType shouldBe SENDT_SYKEPENGESOKNAD
        planlagtVarsel.status shouldBe PLANLAGT
        planlagtVarsel.sendes.shouldBeBefore(Instant.now().plus(3L, ChronoUnit.DAYS))
        planlagtVarsel.sendes.shouldBeAfter(Instant.now().minus(1L, ChronoUnit.MINUTES))

        val avbruttVarsel = planlagteVarsler.first { it.status == AVBRUTT }
        avbruttVarsel.brukerFnr shouldBe soknad.fnr
        avbruttVarsel.sykepengesoknadId shouldBe soknad.id
        avbruttVarsel.orgnummer shouldBe soknad.arbeidsgiver!!.orgnummer
        avbruttVarsel.varselType shouldBe MANGLENDE_SYKEPENGESOKNAD
        avbruttVarsel.status shouldBe AVBRUTT

        planlagteVarslerSomSendesFor(dager = 3).size shouldBe 1
        pdlMockServer?.reset()
    }

    @Test
    @Order(3)
    fun `Vi sender ikke ut det ene varselet fordi det ikke finnes en nærmeste leder `() {
        val planlagtVarsel = planlagteVarslerSomSendesFor(dager = 3).first()
        planlagtVarsel.status shouldBe PLANLAGT

        val antallVarsel = varselUtsendelse.sendVarsler(Instant.now().plus(3L, ChronoUnit.DAYS))
        antallVarsel shouldBe 0
        doknotifikasjonKafkaConsumer.ventPåRecords(antall = 0)

        await().atMost(5, SECONDS).until {
            planlagteVarslerSomSendesFor(dager = 3).isEmpty()
        }

        val utelattVarsel = planlagtVarselRepository.findByIdOrNull(planlagtVarsel.id!!)!!
        utelattVarsel.status shouldBe INGEN_LEDER
    }

    @Test
    @Order(4)
    fun `Vi lagrer to nærmeste ledere for brukeren `() {
        val narmesteLederId = UUID.randomUUID()
        narmesteLederRepository.findByNarmesteLederId(narmesteLederId).shouldBeNull()
        narmesteLederRepository
            .findAll()
            .iterator()
            .asSequence()
            .toList()
            .size shouldBe 0

        val narmesteLederLeesah =
            NarmesteLederLeesah(
                narmesteLederId = narmesteLederId,
                fnr = fnr,
                orgnummer = orgnummer,
                narmesteLederFnr = "01987654321",
                narmesteLederTelefonnummer = "4818494949",
                narmesteLederEpost = "sjefen@bedriften.nav",
                aktivFom = LocalDate.now(),
                aktivTom = null,
                arbeidsgiverForskutterer = true,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            )
        sendNarmesteLederLeesah(narmesteLederLeesah)
        sendNarmesteLederLeesah(
            narmesteLederLeesah.copy(
                narmesteLederId = UUID.randomUUID(),
                orgnummer = "AnnetOrgnummer",
                narmesteLederEpost = "annen@epost.no",
                narmesteLederFnr = "123456",
            ),
        )

        await().atMost(2, SECONDS).until {
            narmesteLederRepository
                .findAll()
                .iterator()
                .asSequence()
                .toList()
                .size == 2
        }
    }

    @Test
    @Order(5)
    fun `Vi mottar en søknad med status NY og planlegger et manglende søknad varsel som vi sender ut`() {
        planlagteVarslerSomSendesFor(dager = 20).size shouldBe 0
        val id = "fbf80f07-e4dc-34d2-8a91-e504f80f3eb5"
        repeat(2) {
            // Takler duplikater
            sendSykepengesoknad(soknad.copy(id = id))
        }
        await().atMost(5, SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadId(id).size == 1
        }

        val planlagtVarsel = planlagtVarselRepository.findBySykepengesoknadId(id).first()
        planlagtVarsel.varselType shouldBe MANGLENDE_SYKEPENGESOKNAD
        planlagtVarsel.status shouldBe PLANLAGT

        planlagteVarslerSomSendesFor(dager = 20).size shouldBe 1

        val antallVarsel = varselUtsendelse.sendVarsler(Instant.now().plus(20L, ChronoUnit.DAYS))
        antallVarsel shouldBe 1

        val notifikasjon = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1).first().value()
        val dineSykmeldteHendelse =
            hendelseKafkaConsumer
                .ventPåRecords(1)
                .first()
                .value()
                .tilDineSykmeldteHendelse()

        await().atMost(2, SECONDS).until {
            planlagteVarslerSomSendesFor(dager = 20).isEmpty()
        }

        val sendtVarsel = planlagtVarselRepository.findByIdOrNull(planlagtVarsel.id!!)!!
        sendtVarsel.status shouldBe PlanlagtVarselStatus.SENDT
        planlagteVarslerSomSendesFor(dager = 20).size shouldBe 0

        notifikasjon.getEpostadresse() shouldBe "sjefen@bedriften.nav"
        notifikasjon.getBestillingsId() shouldBe sendtVarsel.id!!
        notifikasjon.getPrefererteKanaler() shouldBe listOf(PrefererteKanal.EPOST)
        notifikasjon.getFodselsnummer() shouldBe "01987654321"
        notifikasjon.getEpostTekst() shouldBe MANGLENDE_VARSEL_EPOST_TEKST
        notifikasjon.getTittel() shouldBe MANGLENDE_VARSEL_TITTEL

        val varselMedHendelse =
            planlagtVarselRepository.findMedSendtDineSykmeldteHendelse(id, MANGLENDE_SYKEPENGESOKNAD.toString()).first()
        varselMedHendelse.dineSykmeldteHendelseOpprettet shouldNotBe null
        varselMedHendelse.dineSykmeldteHendelseFerdigstilt shouldBe null

        dineSykmeldteHendelse.id shouldBe varselMedHendelse.sykepengesoknadId

        val opprettHendelse = dineSykmeldteHendelse.opprettHendelse!!
        opprettHendelse.ansattFnr shouldBe "12345678901"
        opprettHendelse.orgnummer shouldBe "999111555"
        opprettHendelse.timestamp shouldNotBe null
        opprettHendelse.oppgavetype shouldBe OPPGAVETYPE_IKKE_SENDT_SOKNAD
    }

    @Test
    @Order(6)
    fun `Vi mottar en søknad med status SENDT og planlegger en NY søknad varsel som vi sender ut`() {
        mockPdlResponse()
        planlagteVarslerSomSendesFor(dager = 20).size shouldBe 0
        val id = UUID.randomUUID().toString()
        val soknaden =
            soknad.copy(
                id = id,
                status = SENDT,
                sendtArbeidsgiver = LocalDateTime.now(),
                arbeidsgiver = ArbeidsgiverDTO("annen", "AnnetOrgnummer"),
            )

        sendSykepengesoknad(soknaden)

        await().atMost(2, SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadId(id).size == 1
        }
        val allePlanlagte =
            planlagtVarselRepository
                .findAll()
                .iterator()
                .asSequence()
                .toList()
        allePlanlagte.toString()
        val planlagtVarsel = planlagtVarselRepository.findBySykepengesoknadId(id).first()
        planlagtVarsel.varselType shouldBe SENDT_SYKEPENGESOKNAD
        planlagtVarsel.status shouldBe PLANLAGT

        planlagteVarslerSomSendesFor(dager = 3).size shouldBe 1

        val antallVarsel = varselUtsendelse.sendVarsler(Instant.now().plus(20L, ChronoUnit.DAYS))
        antallVarsel shouldBe 1
        val notifikasjon = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1).first().value()

        await().atMost(1, SECONDS).until {
            planlagteVarslerSomSendesFor(dager = 3).isEmpty()
        }

        val sendtVarsel = planlagtVarselRepository.findByIdOrNull(planlagtVarsel.id!!)!!
        sendtVarsel.status shouldBe PlanlagtVarselStatus.SENDT
        planlagteVarslerSomSendesFor(dager = 20).size shouldBe 0

        notifikasjon.getEpostadresse() shouldBe "annen@epost.no"
        notifikasjon.getBestillingsId() shouldBe sendtVarsel.id!!
        notifikasjon.getPrefererteKanaler() shouldBe listOf(PrefererteKanal.EPOST)
        notifikasjon.getFodselsnummer() shouldBe "123456"
        notifikasjon.getEpostTekst() shouldBe SENDT_SYKEPENGESOKNAD_EPOST_TEKST
        notifikasjon.getTittel() shouldBe SENDT_SYKEPENGESOKNAD_TITTEL
        pdlMockServer?.reset()
    }

    @Test
    @Order(7)
    fun `Vi mottar en søknad med status SENDT og avbryter opprettet hendelse i Dine sykmeldte`() {
        val id = "fbf80f07-e4dc-34d2-8a91-e504f80f3eb5"
        val soknaden =
            soknad.copy(
                id = id,
                status = SENDT,
                sendtArbeidsgiver = LocalDateTime.now(),
                arbeidsgiver = ArbeidsgiverDTO("Arbeidsgiver", "Orgnummer"),
            )

        sendSykepengesoknad(soknaden)

        val dineSykmeldteHendelse =
            hendelseKafkaConsumer
                .ventPåRecords(1)
                .first()
                .value()
                .tilDineSykmeldteHendelse()

        await().atMost(2, SECONDS).until {
            planlagtVarselRepository.findMedSendtDineSykmeldteHendelse(id, MANGLENDE_SYKEPENGESOKNAD.toString()).size == 1
        }
        val varselMedHendelse =
            planlagtVarselRepository.findMedSendtDineSykmeldteHendelse(id, MANGLENDE_SYKEPENGESOKNAD.toString()).first()

        varselMedHendelse.dineSykmeldteHendelseOpprettet shouldNotBe null
        varselMedHendelse.dineSykmeldteHendelseFerdigstilt shouldNotBe null

        dineSykmeldteHendelse.id shouldBe id
        dineSykmeldteHendelse.ferdigstillHendelse shouldNotBe null
        dineSykmeldteHendelse.ferdigstillHendelse!!.timestamp shouldNotBe null
    }

    @Test
    @Order(8)
    fun `Varsel sendes til aktiv leder når en annen leder er inaktiv`() {
        val orgnummer = "aktiv-og-inaktiv"
        val aktivLeder = lagLeder(orgnummer, "aktiv@bedriften.nav", aktivTom = null)
        narmesteLederRepository.save(aktivLeder)
        narmesteLederRepository.save(lagLeder(orgnummer, "inaktiv@bedriften.nav", aktivTom = LocalDate.now()))
        val varsel = lagPlanlagtVarsel(orgnummer)

        varselUtsendelse.sendVarsler(Instant.now()) shouldBe 1

        val notifikasjon = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1).first().value()
        notifikasjon.getEpostadresse() shouldBe aktivLeder.narmesteLederEpost
        hendelseKafkaConsumer.ventPåRecords(1)
        val sendtVarsel = planlagtVarselRepository.findByIdOrNull(varsel.id!!)!!
        sendtVarsel.status shouldBe PlanlagtVarselStatus.SENDT
        sendtVarsel.narmesteLederId shouldBe aktivLeder.narmesteLederId
    }

    @Test
    @Order(9)
    fun `Varsel sendes ikke etter avkrefting i eksisterende ledertabell`() {
        val orgnummer = "bare-inaktiv"
        val tidspunkt = OffsetDateTime.parse("2026-01-01T10:00:00Z")
        val inaktiv =
            getNarmesteLederLeesah(UUID.randomUUID(), aktivTom = LocalDate.now()).copy(
                fnr = fnr,
                orgnummer = orgnummer,
                timestamp = tidspunkt,
            )
        oppdateringAvNarmesteLeder.behandleMeldingFraKafka(inaktiv.serialisertTilString())
        narmesteLederRepository.findByNarmesteLederId(inaktiv.narmesteLederId).shouldBeNull()
        val varsel = lagPlanlagtVarsel(orgnummer)

        varselUtsendelse.sendVarsler(Instant.now()) shouldBe 0
        doknotifikasjonKafkaConsumer.ventPåRecords(antall = 0)
        val utelattVarsel = planlagtVarselRepository.findByIdOrNull(varsel.id!!)!!
        utelattVarsel.status shouldBe INGEN_LEDER
    }

    @Test
    @Order(10)
    fun `Varsel sendes ikke når flere ledere er aktive`() {
        val orgnummer = "flere-aktive"
        narmesteLederRepository.save(lagLeder(orgnummer, "aktiv1@bedriften.nav", aktivTom = null))
        narmesteLederRepository.save(lagLeder(orgnummer, "aktiv2@bedriften.nav", aktivTom = null))
        val varsel = lagPlanlagtVarsel(orgnummer)

        shouldThrow<RuntimeException> { varselUtsendelse.sendVarsler(Instant.now()) }
        doknotifikasjonKafkaConsumer.ventPåRecords(antall = 0)
        planlagtVarselRepository.findByIdOrNull(varsel.id!!)?.status shouldBe PLANLAGT
        planlagtVarselRepository.deleteById(varsel.id)
    }

    private fun lagLeder(
        orgnummer: String,
        epost: String,
        aktivTom: LocalDate?,
    ) = NarmesteLeder(
        oppdatert = Instant.now(),
        timestamp = Instant.now(),
        narmesteLederId = UUID.randomUUID(),
        orgnummer = orgnummer,
        brukerFnr = fnr,
        narmesteLederFnr = "01987654321",
        narmesteLederTelefonnummer = "4818494949",
        narmesteLederEpost = epost,
        aktivFom = LocalDate.now().minusDays(1),
        aktivTom = aktivTom,
        arbeidsgiverForskutterer = true,
    )

    private fun lagPlanlagtVarsel(orgnummer: String) =
        planlagtVarselRepository.save(
            PlanlagtVarsel(
                oppdatert = Instant.now(),
                orgnummer = orgnummer,
                brukerFnr = fnr,
                sykepengesoknadId = UUID.randomUUID().toString(),
                status = PLANLAGT,
                varselType = MANGLENDE_SYKEPENGESOKNAD,
                sendes = Instant.now().minusSeconds(1),
                narmesteLederId = null,
            ),
        )
}
