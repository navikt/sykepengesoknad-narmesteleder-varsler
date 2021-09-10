package no.nav.helse.flex

import no.nav.doknotifikasjon.schemas.PrefererteKanal
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederLeesah
import no.nav.helse.flex.varsler.*
import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.*
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.SENDT_SYKEPENGESOKNAD
import no.nav.syfo.kafka.felles.*
import no.nav.syfo.kafka.felles.SoknadsstatusDTO.SENDT
import org.amshove.kluent.*
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VarselTest : Testoppsett() {

    @Autowired
    lateinit var varselUtsendelse: VarselUtsendelse

    val orgnummer = "999111555"
    val soknad = SykepengesoknadDTO(
        fnr = fnr,
        id = UUID.randomUUID().toString(),
        type = SoknadstypeDTO.ARBEIDSTAKERE,
        status = SoknadsstatusDTO.NY,
        arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
        arbeidsgiver = ArbeidsgiverDTO(navn = "Bedriften AS", orgnummer = orgnummer)
    )

    fun planlagteVarslerSomSendesFør(dager: Int): List<PlanlagtVarsel> {
        return planlagtVarselRepository.findFirst100ByStatusAndSendesIsBefore(
            PLANLAGT,
            OffsetDateTime.now().plusDays(dager.toLong())
        )
    }

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
        planlagtVarsel.sendes.shouldBeBefore(OffsetDateTime.now().plusDays(17).toInstant())
        planlagtVarsel.sendes.shouldBeAfter(OffsetDateTime.now().plusDays(13).toInstant())

        planlagteVarslerSomSendesFør(dager = 20).size `should be equal to` 1
    }

    @Test
    @Order(2)
    fun `Vi mottar en søknad med status SENDT og avbryter manglende søknad varsel og planlegger SENDT søknad varsel `() {
        mockPdlResponse()
        mockSyfoserviceStranglerBrukeroppgavePost()
        planlagtVarselRepository.findBySykepengesoknadId(soknad.id).size `should be equal to` 1
        planlagteVarslerSomSendesFør(dager = 20).size `should be equal to` 1
        planlagteVarslerSomSendesFør(dager = 3).size `should be equal to` 0
        val soknaden = soknad.copy(status = SENDT, sendtArbeidsgiver = LocalDateTime.now())
        sendSykepengesoknad(soknaden)

        await().atMost(5, SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadId(soknad.id).size == 2
        }

        val planlagteVarsler = planlagtVarselRepository.findBySykepengesoknadId(soknad.id)
        planlagteVarsler.size `should be equal to` 2

        val planlagtVarsel = planlagteVarsler.first { it.status == PLANLAGT }
        planlagtVarsel.brukerFnr `should be equal to` soknad.fnr
        planlagtVarsel.sykepengesoknadId `should be equal to` soknad.id
        planlagtVarsel.orgnummer `should be equal to` soknad.arbeidsgiver!!.orgnummer
        planlagtVarsel.varselType `should be equal to` SENDT_SYKEPENGESOKNAD
        planlagtVarsel.status `should be equal to` PLANLAGT
        planlagtVarsel.sendes.shouldBeBefore(OffsetDateTime.now().plusDays(3).toInstant())
        planlagtVarsel.sendes.shouldBeAfter(OffsetDateTime.now().minusMinutes(1).toInstant())

        val avbruttVarsel = planlagteVarsler.first { it.status == AVBRUTT }
        avbruttVarsel.brukerFnr `should be equal to` soknad.fnr
        avbruttVarsel.sykepengesoknadId `should be equal to` soknad.id
        avbruttVarsel.orgnummer `should be equal to` soknad.arbeidsgiver!!.orgnummer
        avbruttVarsel.varselType `should be equal to` MANGLENDE_SYKEPENGESOKNAD
        avbruttVarsel.status `should be equal to` AVBRUTT

        planlagteVarslerSomSendesFør(dager = 3).size `should be equal to` 1
        pdlMockServer?.reset()
        syfoServiceStanglerMockServer?.reset()
    }

    @Test
    @Order(3)
    fun `Vi sender ikke ut det ene varselet fordi det ikke finnes en nærmeste leder `() {
        val planlagtVarsel = planlagteVarslerSomSendesFør(dager = 3).first()
        planlagtVarsel.status `should be equal to` PLANLAGT

        val antallVarsel = varselUtsendelse.sendVarsler(OffsetDateTime.now().plusDays(3), dryrun = false)
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

        val antallVarsel = varselUtsendelse.sendVarsler(OffsetDateTime.now().plusDays(20), dryrun = false)
        antallVarsel `should be equal to` 1
        val notifikasjon = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1).first().value()

        await().atMost(1, SECONDS).until {
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
    }

    @Test
    @Order(6)
    fun `Vi mottar en søknad med status SENDT og planlegger en NY søknad varsel som vi sender ut`() {
        mockPdlResponse()
        mockSyfoserviceStranglerBrukeroppgavePost()
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

        val antallVarsel = varselUtsendelse.sendVarsler(OffsetDateTime.now().plusDays(20), dryrun = false)
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
        syfoServiceStanglerMockServer?.reset()
    }
}
