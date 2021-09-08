package no.nav.helse.flex

import no.nav.helse.flex.kafka.FLEX_SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.PLANLAGT
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType
import no.nav.syfo.kafka.felles.*
import no.nav.syfo.kafka.felles.SoknadsstatusDTO.SENDT
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeAfter
import org.amshove.kluent.shouldBeBefore
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VarselTest : BaseTestClass() {

    @Autowired
    lateinit var kafkaProducer: Producer<String, String>

    val soknad = SykepengesoknadDTO(
        fnr = fnr,
        id = UUID.randomUUID().toString(),
        type = SoknadstypeDTO.ARBEIDSTAKERE,
        status = SoknadsstatusDTO.NY,
        arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
        arbeidsgiver = ArbeidsgiverDTO(navn = "Bedriften AS", orgnummer = "999111555")
    )

    fun planlagteVarslerSomSendesFør(dager: Int): List<PlanlagtVarsel> {
        return planlagtVarselRepository.findFirst100ByStatusAndSendesIsBefore(
            PLANLAGT,
            OffsetDateTime.now().plusDays(dager.toLong())
        )
    }

    @Test
    @Order(1)
    fun `Vi mottar en søknad med status NY og planlegger et manglende søknad varsel`() {

        planlagteVarslerSomSendesFør(dager = 20).size `should be equal to` 0

        kafkaProducer.send(
            ProducerRecord(
                FLEX_SYKEPENGESOKNAD_TOPIC,
                null,
                soknad.id,
                soknad.serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadId(soknad.id).size == 1
        }

        val planlagtVarsel = planlagtVarselRepository.findBySykepengesoknadId(soknad.id).first()
        planlagtVarsel.brukerFnr `should be equal to` soknad.fnr
        planlagtVarsel.sykepengesoknadId `should be equal to` soknad.id
        planlagtVarsel.orgnummer `should be equal to` soknad.arbeidsgiver!!.orgnummer
        planlagtVarsel.varselType `should be equal to` PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD
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
        kafkaProducer.send(
            ProducerRecord(
                FLEX_SYKEPENGESOKNAD_TOPIC,
                null,
                soknad.id,
                soknad.copy(status = SENDT, sendtArbeidsgiver = LocalDateTime.now()).serialisertTilString()
            )
        ).get()

        await().atMost(5, TimeUnit.SECONDS).until {
            planlagtVarselRepository.findBySykepengesoknadId(soknad.id).size == 2
        }

        val planlagteVarsler = planlagtVarselRepository.findBySykepengesoknadId(soknad.id)
        planlagteVarsler.size `should be equal to` 2

        val planlagtVarsel = planlagteVarsler.first { it.status == PLANLAGT }
        planlagtVarsel.brukerFnr `should be equal to` soknad.fnr
        planlagtVarsel.sykepengesoknadId `should be equal to` soknad.id
        planlagtVarsel.orgnummer `should be equal to` soknad.arbeidsgiver!!.orgnummer
        planlagtVarsel.varselType `should be equal to` PlanlagtVarselType.SENDT_SYKEPENGESOKNAD
        planlagtVarsel.status `should be equal to` PLANLAGT
        planlagtVarsel.sendes.shouldBeBefore(OffsetDateTime.now().plusDays(3).toInstant())
        planlagtVarsel.sendes.shouldBeAfter(OffsetDateTime.now().minusMinutes(1).toInstant())

        val avbruttVarsel = planlagteVarsler.first { it.status == PlanlagtVarselStatus.AVBRUTT }
        avbruttVarsel.brukerFnr `should be equal to` soknad.fnr
        avbruttVarsel.sykepengesoknadId `should be equal to` soknad.id
        avbruttVarsel.orgnummer `should be equal to` soknad.arbeidsgiver!!.orgnummer
        avbruttVarsel.varselType `should be equal to` PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD
        avbruttVarsel.status `should be equal to` PlanlagtVarselStatus.AVBRUTT

        planlagteVarslerSomSendesFør(dager = 3).size `should be equal to` 1
    }
}
