package no.nav.helse.flex

import no.nav.helse.flex.db.NarmesteLederRepository
import no.nav.helse.flex.domain.NarmesteLederLeesah
import no.nav.helse.flex.kafka.NARMESTELEDER_LEESAH_TOPIC
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit

class OppdaterNarmesteLederTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var narmesteLederRepository: NarmesteLederRepository

    @Test
    fun `Oppretter ny nærmeste leder hvis den ikke finnes fra før og er aktiv`() {

        val narmesteLederId = UUID.randomUUID()
        narmesteLederRepository.findByNarmesteLederId(narmesteLederId).shouldBeNull()

        val narmesteLederLeesah = getNarmesteLederLeesah(narmesteLederId)
        kafkaProducer.send(
            ProducerRecord(
                NARMESTELEDER_LEESAH_TOPIC,
                null,
                narmesteLederId.toString(),
                narmesteLederLeesah.serialisertTilString()
            )
        ).get()

        await().atMost(10, TimeUnit.SECONDS).until {
            narmesteLederRepository.findByNarmesteLederId(narmesteLederId) != null
        }

        val narmesteLeder = narmesteLederRepository.findByNarmesteLederId(narmesteLederId)
        narmesteLeder.shouldNotBeNull()
        narmesteLeder.narmesteLederEpost `should be equal to` narmesteLederLeesah.narmesteLederEpost
    }

    @Test
    fun `Ignorerer melding om ny nærmeste leder hvis den ikke finnes fra før og er inaktiv`() {
        val narmesteLederId = UUID.randomUUID()
        val narmesteLederLeesah = getNarmesteLederLeesah(narmesteLederId, aktivTom = LocalDate.now())
        kafkaProducer.send(
            ProducerRecord(
                NARMESTELEDER_LEESAH_TOPIC,
                null,
                narmesteLederId.toString(),
                narmesteLederLeesah.serialisertTilString()
            )
        ).get()

        await().during(2, TimeUnit.SECONDS).until {
            narmesteLederRepository.findByNarmesteLederId(narmesteLederId) == null
        }

        narmesteLederRepository.findByNarmesteLederId(narmesteLederId).shouldBeNull()
    }

    @Test
    fun `Oppdaterer nærmeste leder hvis den finnes fra før og er aktiv`() {
        val narmesteLederId = UUID.randomUUID()
        val narmesteLederLeesah = getNarmesteLederLeesah(narmesteLederId)
        kafkaProducer.send(
            ProducerRecord(
                NARMESTELEDER_LEESAH_TOPIC,
                null,
                narmesteLederId.toString(),
                narmesteLederLeesah.serialisertTilString()
            )
        ).get()

        await().atMost(10, TimeUnit.SECONDS).until {
            narmesteLederRepository.findByNarmesteLederId(narmesteLederId) != null
        }

        val narmesteLeder = narmesteLederRepository.findByNarmesteLederId(narmesteLederId)!!
        narmesteLeder.narmesteLederTelefonnummer `should be equal to` "90909090"
        narmesteLeder.narmesteLederEpost `should be equal to` "test@nav.no"

        kafkaProducer.send(
            ProducerRecord(
                NARMESTELEDER_LEESAH_TOPIC,
                null,
                narmesteLederId.toString(),
                getNarmesteLederLeesah(
                    narmesteLederId,
                    telefonnummer = "98989898",
                    epost = "mail@banken.no"
                ).serialisertTilString()
            )
        ).get()

        await().atMost(10, TimeUnit.SECONDS).until {
            narmesteLederRepository.findByNarmesteLederId(narmesteLederId)!!.narmesteLederEpost == "mail@banken.no"
        }

        val oppdaterNl = narmesteLederRepository.findByNarmesteLederId(narmesteLederId)!!
        oppdaterNl.narmesteLederTelefonnummer `should be equal to` "98989898"
        oppdaterNl.narmesteLederEpost `should be equal to` "mail@banken.no"
    }

    @Test
    fun `Sletter nærmeste leder hvis den finnes fra før og er inaktiv`() {
        val narmesteLederId = UUID.randomUUID()
        val narmesteLederLeesah = getNarmesteLederLeesah(narmesteLederId)
        kafkaProducer.send(
            ProducerRecord(
                NARMESTELEDER_LEESAH_TOPIC,
                null,
                narmesteLederId.toString(),
                narmesteLederLeesah.serialisertTilString()
            )
        ).get()

        await().atMost(10, TimeUnit.SECONDS).until {
            narmesteLederRepository.findByNarmesteLederId(narmesteLederId) != null
        }

        narmesteLederRepository.findByNarmesteLederId(narmesteLederId).shouldNotBeNull()

        kafkaProducer.send(
            ProducerRecord(
                NARMESTELEDER_LEESAH_TOPIC,
                null,
                narmesteLederId.toString(),
                getNarmesteLederLeesah(
                    narmesteLederId,
                    aktivTom = LocalDate.now(),
                ).serialisertTilString()
            )
        ).get()

        await().atMost(10, TimeUnit.SECONDS).until {
            narmesteLederRepository.findByNarmesteLederId(narmesteLederId) == null
        }
        narmesteLederRepository.findByNarmesteLederId(narmesteLederId).shouldBeNull()
    }
}

fun getNarmesteLederLeesah(
    narmesteLederId: UUID,
    telefonnummer: String = "90909090",
    epost: String = "test@nav.no",
    aktivTom: LocalDate? = null
): NarmesteLederLeesah =
    NarmesteLederLeesah(
        narmesteLederId = narmesteLederId,
        fnr = "12345678910",
        orgnummer = "999999",
        narmesteLederFnr = "01987654321",
        narmesteLederTelefonnummer = telefonnummer,
        narmesteLederEpost = epost,
        aktivFom = LocalDate.now(),
        aktivTom = aktivTom,
        arbeidsgiverForskutterer = true,
        timestamp = OffsetDateTime.now(ZoneOffset.UTC)
    )
