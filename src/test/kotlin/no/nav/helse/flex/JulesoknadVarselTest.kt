package no.nav.helse.flex

import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.date.shouldBeBefore
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidsgiverDTO
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.PLANLAGT
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class JulesoknadVarselTest : FellesTestOppsett() {
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
    @Order(1)
    fun `Mottar en NY julesøknad og planlegger et manglende søknad varsel med riktig sendestidspunkt`() {
        planlagteVarslerSomSendesFor(dager = 999999).size shouldBe 0

        // Denne testen knekker hvis koden fortsatt lever om 20 år.
        sendSykepengesoknad(soknad.copy(tom = LocalDate.of(2043, 12, 24)))

        await().atMost(5, SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadId(soknad.id).size == 1
        }

        val planlagtVarsel = planlagtVarselRepository.findBySykepengesoknadId(soknad.id).first()
        planlagtVarsel.brukerFnr shouldBe soknad.fnr
        planlagtVarsel.sykepengesoknadId shouldBe soknad.id
        planlagtVarsel.orgnummer shouldBe soknad.arbeidsgiver!!.orgnummer
        planlagtVarsel.varselType shouldBe MANGLENDE_SYKEPENGESOKNAD
        planlagtVarsel.status shouldBe PLANLAGT
        planlagtVarsel.sendes.shouldBeBefore(LocalDate.of(2044, 1, 9).atStartOfDay().toInstant(ZoneOffset.UTC))
        planlagtVarsel.sendes.shouldBeAfter(LocalDate.of(2044, 1, 8).atStartOfDay().toInstant(ZoneOffset.UTC))

        planlagteVarslerSomSendesFor(dager = 999999).size shouldBe 1
    }
}
