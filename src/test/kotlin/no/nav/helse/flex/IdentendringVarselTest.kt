package no.nav.helse.flex

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.client.pdl.IdentGruppe.FOLKEREGISTERIDENT
import no.nav.helse.flex.client.pdl.PdlGraphQlFeil
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder
import no.nav.helse.flex.varsler.VarselUtsendelse
import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class IdentendringVarselTest : FellesTestOppsett() {
    @Autowired
    lateinit var varselUtsendelse: VarselUtsendelse

    private val planlagtVarselIder = mutableListOf<String>()

    @AfterEach
    fun ryddEtterTest() {
        doknotifikasjonKafkaConsumer.ventPåRecords(antall = 0)
        planlagtVarselIder.forEach(planlagtVarselRepository::deleteById)
        planlagtVarselIder.clear()
    }

    @Test
    fun `Sender varsel når lederen er registrert på gammelt fnr og søknaden på nytt`() {
        val gammeltFnr = fiktivtFnr()
        val nyttFnr = fiktivtFnr()
        val lederFnr = fiktivtFnr()
        val orgnummer = UUID.randomUUID().toString()
        settIdentkjede(nyttFnr, gammeltFnr, nyttFnr)
        lagreLeder(orgnummer, gammeltFnr, lederFnr)
        val varsel = lagreVarsel(orgnummer, nyttFnr)

        varselUtsendelse.sendVarsler() shouldBe 1

        val notifikasjon = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1).first().value()
        planlagtVarselRepository.findByIdOrNull(varsel.id!!)!!.status shouldBe PlanlagtVarselStatus.SENDT
        notifikasjon.getFodselsnummer() shouldBe lederFnr
    }

    @Test
    fun `Sender varsel når søknaden har gammelt fnr og lederen er registrert på nytt`() {
        val gammeltFnr = fiktivtFnr()
        val nyttFnr = fiktivtFnr()
        val lederFnr = fiktivtFnr()
        val orgnummer = UUID.randomUUID().toString()
        settIdentkjede(gammeltFnr, gammeltFnr, nyttFnr)
        lagreLeder(orgnummer, nyttFnr, lederFnr)
        val varsel = lagreVarsel(orgnummer, gammeltFnr)

        varselUtsendelse.sendVarsler() shouldBe 1

        val notifikasjon = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1).first().value()
        planlagtVarselRepository.findByIdOrNull(varsel.id!!)!!.status shouldBe PlanlagtVarselStatus.SENDT
        notifikasjon.getFodselsnummer() shouldBe lederFnr
    }

    @Test
    fun `Velger lederen på gjeldende fnr når det finnes ledere på begge identene`() {
        val gammeltFnr = fiktivtFnr()
        val nyttFnr = fiktivtFnr()
        val orgnummer = UUID.randomUUID().toString()
        settIdentkjede(gammeltFnr, gammeltFnr, nyttFnr)
        lagreLeder(orgnummer, gammeltFnr, fiktivtFnr(), "gammel@example.no")
        val gjeldendeLederFnr = fiktivtFnr()
        val gjeldendeLederEpost = "gjeldende@example.no"
        lagreLeder(orgnummer, nyttFnr, gjeldendeLederFnr, gjeldendeLederEpost)
        val varsel = lagreVarsel(orgnummer, gammeltFnr)

        varselUtsendelse.sendVarsler() shouldBe 1

        val notifikasjon = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1).first().value()
        planlagtVarselRepository.findByIdOrNull(varsel.id!!)!!.status shouldBe PlanlagtVarselStatus.SENDT
        notifikasjon.getEpostadresse() shouldBe gjeldendeLederEpost
        notifikasjon.getFodselsnummer() shouldBe gjeldendeLederFnr
    }

    @Test
    fun `Faller tilbake til eksakt fnr når PDL ikke finner personen`() {
        val brukerFnr = fiktivtFnr()
        val lederFnr = fiktivtFnr()
        val orgnummer = UUID.randomUUID().toString()
        pdlClientFake.setFeil(brukerFnr, PdlGraphQlFeil(listOf("not_found")))
        lagreLeder(orgnummer, brukerFnr, lederFnr)
        val varsel = lagreVarsel(orgnummer, brukerFnr)

        varselUtsendelse.sendVarsler() shouldBe 1

        val notifikasjon = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1).first().value()
        planlagtVarselRepository.findByIdOrNull(varsel.id!!)!!.status shouldBe PlanlagtVarselStatus.SENDT
        notifikasjon.getFodselsnummer() shouldBe lederFnr
    }

    @Test
    fun `Faller tilbake til eksakt fnr når PDL-svaret ikke inneholder fnr-et`() {
        val brukerFnr = fiktivtFnr()
        val annetFnr = fiktivtFnr()
        val lederFnr = fiktivtFnr()
        val orgnummer = UUID.randomUUID().toString()
        pdlClientFake.setIdenter(
            brukerFnr,
            listOf(PdlIdent(gruppe = FOLKEREGISTERIDENT, ident = annetFnr)),
        )
        lagreLeder(orgnummer, brukerFnr, lederFnr)
        val varsel = lagreVarsel(orgnummer, brukerFnr)

        varselUtsendelse.sendVarsler() shouldBe 1

        val notifikasjon = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1).first().value()
        planlagtVarselRepository.findByIdOrNull(varsel.id!!)!!.status shouldBe PlanlagtVarselStatus.SENDT
        notifikasjon.getFodselsnummer() shouldBe lederFnr
    }

    @Test
    fun `Stopper kjøringen og lar varselet stå som planlagt ved tilgangsfeil fra PDL`() {
        val brukerFnr = fiktivtFnr()
        val lederFnr = fiktivtFnr()
        val orgnummer = UUID.randomUUID().toString()
        pdlClientFake.setFeil(brukerFnr, PdlGraphQlFeil(listOf("unauthorized")))
        lagreLeder(orgnummer, brukerFnr, lederFnr)
        val varsel = lagreVarsel(orgnummer, brukerFnr)

        shouldThrow<PdlGraphQlFeil> { varselUtsendelse.sendVarsler() }

        planlagtVarselRepository.findByIdOrNull(varsel.id!!)!!.status shouldBe PlanlagtVarselStatus.PLANLAGT
        doknotifikasjonKafkaConsumer.ventPåRecords(antall = 0).shouldBeEmpty()
    }

    private fun settIdentkjede(
        opprinneligFnr: String,
        gammeltFnr: String,
        nyttFnr: String,
    ) {
        pdlClientFake.setIdenter(
            opprinneligFnr,
            listOf(
                PdlIdent(gruppe = FOLKEREGISTERIDENT, ident = gammeltFnr, historisk = true),
                PdlIdent(gruppe = FOLKEREGISTERIDENT, ident = nyttFnr),
            ),
        )
    }

    private fun lagreLeder(
        orgnummer: String,
        brukerFnr: String,
        lederFnr: String,
        epost: String = "leder@example.no",
    ): NarmesteLeder {
        val now = Instant.now()
        return narmesteLederRepository.save(
            NarmesteLeder(
                id = null,
                oppdatert = now,
                timestamp = now,
                narmesteLederId = UUID.randomUUID(),
                orgnummer = orgnummer,
                brukerFnr = brukerFnr,
                narmesteLederFnr = lederFnr,
                narmesteLederTelefonnummer = "12345678",
                narmesteLederEpost = epost,
                aktivFom = LocalDate.now(),
                arbeidsgiverForskutterer = null,
            ),
        )
    }

    private fun lagreVarsel(
        orgnummer: String,
        brukerFnr: String,
    ): PlanlagtVarsel {
        val varsel =
            planlagtVarselRepository.save(
                PlanlagtVarsel(
                    oppdatert = Instant.now(),
                    orgnummer = orgnummer,
                    brukerFnr = brukerFnr,
                    sykepengesoknadId = UUID.randomUUID().toString(),
                    status = PlanlagtVarselStatus.PLANLAGT,
                    varselType = PlanlagtVarselType.SENDT_SYKEPENGESOKNAD,
                    sendes = Instant.now().minusSeconds(60),
                    narmesteLederId = null,
                ),
            )
        planlagtVarselIder.add(varsel.id!!)
        return varsel
    }

    private fun fiktivtFnr(): String = ThreadLocalRandom.current().nextLong(10_000_000_000L, 100_000_000_000L).toString()
}
