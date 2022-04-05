package no.nav.helse.flex

import no.nav.doknotifikasjon.schemas.PrefererteKanal
import no.nav.helse.flex.dinesykmeldte.OPPGAVETYPE_IKKE_SENDT_SOKNAD
import no.nav.helse.flex.dinesykmeldte.tilDineSykmeldteHendelse
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
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeAfter
import org.amshove.kluent.shouldBeBefore
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBe
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
class VarselTest : Testoppsett() {

    @Autowired
    lateinit var varselUtsendelse: VarselUtsendelse

    final val orgnummer = "999111555"
    val soknad = SykepengesoknadDTO(
        fnr = fnr,
        id = UUID.randomUUID().toString(),
        type = SoknadstypeDTO.ARBEIDSTAKERE,
        status = SoknadsstatusDTO.NY,
        arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
        arbeidsgiver = ArbeidsgiverDTO(navn = "Bedriften AS", orgnummer = orgnummer)
    )

    @Test
    @Order(0)
    fun `Arbeidsledig, frilanser og sånt skaper ikke planlagt varsel`() {

        planlagtVarselRepository.findAll().iterator().asSequence().toList().isEmpty()

        val arbeidsledig = SykepengesoknadDTO(
            fnr = fnr,
            id = UUID.randomUUID().toString(),
            type = SoknadstypeDTO.ARBEIDSLEDIG,
            status = SoknadsstatusDTO.NY,
            arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSLEDIG,
        )
        val frilanser = SykepengesoknadDTO(
            fnr = fnr,
            id = UUID.randomUUID().toString(),
            type = SoknadstypeDTO.SELVSTENDIGE_OG_FRILANSERE,
            status = SoknadsstatusDTO.NY,
            arbeidssituasjon = ArbeidssituasjonDTO.FRILANSER,
        )

        sendSykepengesoknad(arbeidsledig)
        sendSykepengesoknad(frilanser)

        await().during(3, SECONDS).until {
            planlagtVarselRepository.findAll().iterator().asSequence().toList().isEmpty()
        }

        planlagtVarselRepository.findAll().iterator().asSequence().toList().shouldBeEmpty()
    }

    @Test
    @Order(1)
    fun `Vi mottar en søknad med status NY og planlegger et manglende søknad varsel`() {

        planlagteVarslerSomSendesFør(dager = 20).size `should be equal to` 0

        sendSykepengesoknad(soknad)

        await().atMost(5, SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadId(soknad.id).size == 1
        }

        val planlagtVarsel = planlagtVarselRepository.findBySykepengesoknadId(soknad.id).first()
        planlagtVarsel.brukerFnr `should be equal to` soknad.fnr
        planlagtVarsel.sykepengesoknadId `should be equal to` soknad.id
        planlagtVarsel.orgnummer `should be equal to` soknad.arbeidsgiver!!.orgnummer
        planlagtVarsel.varselType `should be equal to` MANGLENDE_SYKEPENGESOKNAD
        planlagtVarsel.status `should be equal to` PLANLAGT
        planlagtVarsel.sendes.shouldBeBefore(Instant.now().plus(17L, ChronoUnit.DAYS))
        planlagtVarsel.sendes.shouldBeAfter(Instant.now().plus(13L, ChronoUnit.DAYS))

        planlagteVarslerSomSendesFør(dager = 20).size `should be equal to` 1
    }

    @Test
    @Order(2)
    fun `Vi mottar en søknad med status SENDT og avbryter manglende søknad varsel og planlegger SENDT søknad varsel `() {
        mockPdlResponse()
        planlagtVarselRepository.findBySykepengesoknadId(soknad.id).size `should be equal to` 1
        planlagteVarslerSomSendesFør(dager = 20).size `should be equal to` 1
        planlagteVarslerSomSendesFør(dager = 3).size `should be equal to` 0
        val soknaden = soknad.copy(status = SENDT, sendtArbeidsgiver = LocalDateTime.now())
        sendSykepengesoknad(soknaden)

        await().atMost(5, SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadIdAndStatus(soknad.id, AVBRUTT).isNotEmpty()
        }

        val planlagteVarsler = planlagtVarselRepository.findBySykepengesoknadId(soknad.id)
        planlagteVarsler.size `should be equal to` 2

        val planlagtVarsel = planlagteVarsler.first { it.status == PLANLAGT }
        planlagtVarsel.brukerFnr `should be equal to` soknad.fnr
        planlagtVarsel.sykepengesoknadId `should be equal to` soknad.id
        planlagtVarsel.orgnummer `should be equal to` soknad.arbeidsgiver!!.orgnummer
        planlagtVarsel.varselType `should be equal to` SENDT_SYKEPENGESOKNAD
        planlagtVarsel.status `should be equal to` PLANLAGT
        planlagtVarsel.sendes.shouldBeBefore(Instant.now().plus(3L, ChronoUnit.DAYS))
        planlagtVarsel.sendes.shouldBeAfter(Instant.now().minus(1L, ChronoUnit.MINUTES))

        val avbruttVarsel = planlagteVarsler.first { it.status == AVBRUTT }
        avbruttVarsel.brukerFnr `should be equal to` soknad.fnr
        avbruttVarsel.sykepengesoknadId `should be equal to` soknad.id
        avbruttVarsel.orgnummer `should be equal to` soknad.arbeidsgiver!!.orgnummer
        avbruttVarsel.varselType `should be equal to` MANGLENDE_SYKEPENGESOKNAD
        avbruttVarsel.status `should be equal to` AVBRUTT

        planlagteVarslerSomSendesFør(dager = 3).size `should be equal to` 1
        pdlMockServer?.reset()
    }

    @Test
    @Order(3)
    fun `Vi sender ikke ut det ene varselet fordi det ikke finnes en nærmeste leder `() {
        val planlagtVarsel = planlagteVarslerSomSendesFør(dager = 3).first()
        planlagtVarsel.status `should be equal to` PLANLAGT

        val antallVarsel = varselUtsendelse.sendVarsler(Instant.now().plus(3L, ChronoUnit.DAYS))
        antallVarsel `should be equal to` 0
        doknotifikasjonKafkaConsumer.ventPåRecords(antall = 0)

        await().atMost(5, SECONDS).until {
            planlagteVarslerSomSendesFør(dager = 3).isEmpty()
        }

        val utelattVarsel = planlagtVarselRepository.findByIdOrNull(planlagtVarsel.id!!)!!
        utelattVarsel.status `should be equal to` INGEN_LEDER
    }

    @Test
    @Order(4)
    fun `Vi lagrer to nærmeste ledere for brukeren `() {

        val narmesteLederId = UUID.randomUUID()
        narmesteLederRepository.findByNarmesteLederId(narmesteLederId).shouldBeNull()
        narmesteLederRepository.findAll().iterator().asSequence().toList().size `should be equal to` 0

        val narmesteLederLeesah = NarmesteLederLeesah(
            narmesteLederId = narmesteLederId,
            fnr = fnr,
            orgnummer = orgnummer,
            narmesteLederFnr = "01987654321",
            narmesteLederTelefonnummer = "4818494949",
            narmesteLederEpost = "sjefen@bedriften.nav",
            aktivFom = LocalDate.now(),
            aktivTom = null,
            arbeidsgiverForskutterer = true,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC)
        )
        sendNarmesteLederLeesah(narmesteLederLeesah)
        sendNarmesteLederLeesah(
            narmesteLederLeesah.copy(
                narmesteLederId = UUID.randomUUID(),
                orgnummer = "AnnetOrgnummer",
                narmesteLederEpost = "annen@epost.no",
                narmesteLederFnr = "123456"

            )
        )

        await().atMost(2, SECONDS).until {
            narmesteLederRepository.findAll().iterator().asSequence().toList().size == 2
        }
    }

    @Test
    @Order(5)
    fun `Vi mottar en søknad med status NY og planlegger et manglende søknad varsel som vi sender ut`() {

        planlagteVarslerSomSendesFør(dager = 20).size `should be equal to` 0
        val id = UUID.randomUUID().toString()
        repeat(2) { // Takler duplikater
            sendSykepengesoknad(soknad.copy(id = id))
        }
        await().atMost(5, SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadId(id).size == 1
        }

        val planlagtVarsel = planlagtVarselRepository.findBySykepengesoknadId(id).first()
        planlagtVarsel.varselType `should be equal to` MANGLENDE_SYKEPENGESOKNAD
        planlagtVarsel.status `should be equal to` PLANLAGT

        planlagteVarslerSomSendesFør(dager = 20).size `should be equal to` 1

        val antallVarsel = varselUtsendelse.sendVarsler(Instant.now().plus(20L, ChronoUnit.DAYS))
        antallVarsel `should be equal to` 1

        val notifikasjon = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1).first().value()
        val dineSykmeldteHendelse = hendelseKafkaConsumer.ventPåRecords(1).first().value().tilDineSykmeldteHendelse()

        await().atMost(2, SECONDS).until {
            planlagteVarslerSomSendesFør(dager = 20).isEmpty()
        }

        val sendtVarsel = planlagtVarselRepository.findByIdOrNull(planlagtVarsel.id!!)!!
        sendtVarsel.status `should be equal to` PlanlagtVarselStatus.SENDT
        planlagteVarslerSomSendesFør(dager = 20).size `should be equal to` 0

        notifikasjon.getEpostadresse() `should be equal to` "sjefen@bedriften.nav"
        notifikasjon.getBestillingsId() `should be equal to` sendtVarsel.id!!
        notifikasjon.getPrefererteKanaler() `should be equal to` listOf(PrefererteKanal.EPOST)
        notifikasjon.getFodselsnummer() `should be equal to` "01987654321"
        notifikasjon.getEpostTekst() `should be equal to` MANGLENDE_VARSEL_EPOST_TEKST
        notifikasjon.getTittel() `should be equal to` MANGLENDE_VARSEL_TITTEL

        val varselMedHendelse = planlagtVarselRepository.findBySendtDineSykmeldte(id, MANGLENDE_SYKEPENGESOKNAD.toString()).first()
        varselMedHendelse.dineSykmeldteHendelseOpprettet shouldNotBe null
        varselMedHendelse.dineSykmeldteHendelseFerdigstilt shouldBe null

        dineSykmeldteHendelse.id `should be equal to` varselMedHendelse.sykepengesoknadId

        val opprettHendelse = dineSykmeldteHendelse.opprettHendelse!!
        opprettHendelse.ansattFnr `should be equal to` "12345678901"
        opprettHendelse.orgnummer `should be equal to` "999111555"
        opprettHendelse.timestamp shouldNotBe null
        opprettHendelse.oppgavetype `should be equal to` OPPGAVETYPE_IKKE_SENDT_SOKNAD
    }

    @Test
    @Order(6)
    fun `Vi mottar en søknad med status SENDT og planlegger en NY søknad varsel som vi sender ut`() {
        mockPdlResponse()
        planlagteVarslerSomSendesFør(dager = 20).size `should be equal to` 0
        val id = UUID.randomUUID().toString()
        val soknaden = soknad.copy(
            id = id,
            status = SENDT,
            sendtArbeidsgiver = LocalDateTime.now(),
            arbeidsgiver = ArbeidsgiverDTO("annen", "AnnetOrgnummer")
        )

        sendSykepengesoknad(soknaden)

        await().atMost(2, SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadId(id).size == 1
        }
        val allePlanlagte = planlagtVarselRepository.findAll().iterator().asSequence().toList()
        allePlanlagte.toString()
        val planlagtVarsel = planlagtVarselRepository.findBySykepengesoknadId(id).first()
        planlagtVarsel.varselType `should be equal to` SENDT_SYKEPENGESOKNAD
        planlagtVarsel.status `should be equal to` PLANLAGT

        planlagteVarslerSomSendesFør(dager = 3).size `should be equal to` 1

        val antallVarsel = varselUtsendelse.sendVarsler(Instant.now().plus(20L, ChronoUnit.DAYS))
        antallVarsel `should be equal to` 1
        val notifikasjon = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1).first().value()

        await().atMost(1, SECONDS).until {
            planlagteVarslerSomSendesFør(dager = 3).isEmpty()
        }

        val sendtVarsel = planlagtVarselRepository.findByIdOrNull(planlagtVarsel.id!!)!!
        sendtVarsel.status `should be equal to` PlanlagtVarselStatus.SENDT
        planlagteVarslerSomSendesFør(dager = 20).size `should be equal to` 0

        notifikasjon.getEpostadresse() `should be equal to` "annen@epost.no"
        notifikasjon.getBestillingsId() `should be equal to` sendtVarsel.id!!
        notifikasjon.getPrefererteKanaler() `should be equal to` listOf(PrefererteKanal.EPOST)
        notifikasjon.getFodselsnummer() `should be equal to` "123456"
        notifikasjon.getEpostTekst() `should be equal to` SENDT_SYKEPENGESOKNAD_EPOST_TEKST
        notifikasjon.getTittel() `should be equal to` SENDT_SYKEPENGESOKNAD_TITTEL
        pdlMockServer?.reset()
    }

    private fun planlagteVarslerSomSendesFør(dager: Int): List<PlanlagtVarsel> {
        return planlagtVarselRepository.findFirst300ByStatusAndSendesIsBefore(
            PLANLAGT,
            Instant.now().plus(dager.toLong(), ChronoUnit.DAYS)
        )
    }
}
