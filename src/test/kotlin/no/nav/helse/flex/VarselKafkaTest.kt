package no.nav.helse.flex

import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import no.nav.helse.flex.domain.NarmesteLeder
import no.nav.helse.flex.kafka.doknotifikasjonTopic
import no.nav.helse.flex.varsel.skapNySøknadNotifikasjon
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.*

class VarselKafkaTest : BaseTestClass() {

    @Autowired
    lateinit var kafkaProducer: Producer<String, NotifikasjonMedkontaktInfo>

    @Test
    fun `Oppretter ny nærmeste leder hvis den ikke finnes fra før og er aktiv`() {

        val narmesteLeder = NarmesteLeder(
            id = null,
            narmesteLederId = UUID.randomUUID(),
            brukerFnr = "fnr",
            orgnummer = "orgnummer",
            narmesteLederFnr = "narmesteLederFnr",
            narmesteLederTelefonnummer = "narmesteLederTelefonnummer",
            narmesteLederEpost = "narmesteLederEpost",
            aktivFom = LocalDate.now(),
            arbeidsgiverForskutterer = true,
            timestamp = Instant.now(),
            oppdatert = Instant.now()
        )
        val bestillingsId = UUID.randomUUID().toString()
        val notifikasjon = skapNySøknadNotifikasjon(bestillingsId, narmesteLeder)

        kafkaProducer.send(
            ProducerRecord(
                doknotifikasjonTopic,
                null,
                bestillingsId,
                notifikasjon
            )
        ).get()

        val notifikasjoner = doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1)
        notifikasjoner.first().value().getEpostadresse() `should be equal to` narmesteLeder.narmesteLederEpost
    }
}
