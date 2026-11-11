package no.nav.helse.flex

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.kafka.NARMESTELEDER_ESYFO_LEESAH_TOPIC
import no.nav.helse.flex.kafka.NARMESTELEDER_LEESAH_TOPIC
import no.nav.helse.flex.narmesteleder.NarmesteLederEsyfoRepository
import no.nav.helse.flex.narmesteleder.OppdateringAvNarmesteLeder
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederLeesah
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OppdaterNarmesteLederTest : FellesTestOppsett() {
    @Autowired
    lateinit var oppdateringAvNarmesteLeder: OppdateringAvNarmesteLeder

    @Autowired
    lateinit var kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry

    @Autowired
    lateinit var esyfo: NarmesteLederEsyfoRepository

    @Test
    fun `Oppretter ny nærmeste leder hvis den ikke finnes fra før og er aktiv`() {
        val narmesteLederId = UUID.randomUUID()
        narmesteLederRepository.findByNarmesteLederId(narmesteLederId).shouldBeNull()

        val narmesteLederLeesah = getNarmesteLederLeesah(narmesteLederId)
        sendNarmesteLederLeesah(narmesteLederLeesah)

        await().atMost(10, TimeUnit.SECONDS).until {
            narmesteLederRepository.findByNarmesteLederId(narmesteLederId) != null
        }

        val narmesteLeder = narmesteLederRepository.findByNarmesteLederId(narmesteLederId)
        narmesteLeder.shouldNotBeNull()
        narmesteLeder.narmesteLederId shouldBe narmesteLederId
        narmesteLeder.brukerFnr shouldBe narmesteLederLeesah.fnr
        narmesteLeder.orgnummer shouldBe narmesteLederLeesah.orgnummer
        narmesteLeder.narmesteLederTelefonnummer shouldBe narmesteLederLeesah.narmesteLederTelefonnummer
        narmesteLeder.narmesteLederEpost shouldBe narmesteLederLeesah.narmesteLederEpost
    }

    @Test
    fun `Ignorerer inaktiv nærmeste leder når den ikke finnes fra før`() {
        val narmesteLederId = UUID.randomUUID()
        val narmesteLederLeesah = getNarmesteLederLeesah(narmesteLederId, aktivTom = LocalDate.now())

        narmesteLederRepository.findByNarmesteLederId(narmesteLederId).shouldBeNull()
        oppdateringAvNarmesteLeder.behandleMeldingFraKafka(narmesteLederLeesah.serialisertTilString())

        narmesteLederRepository.findByNarmesteLederId(narmesteLederId).shouldBeNull()
    }

    @Test
    fun `Oppdaterer nærmeste leder hvis den finnes fra før og er aktiv`() {
        val narmesteLederId = UUID.randomUUID()
        val narmesteLederLeesah = getNarmesteLederLeesah(narmesteLederId)

        sendNarmesteLederLeesah(narmesteLederLeesah)
        await().atMost(10, TimeUnit.SECONDS).until {
            narmesteLederRepository.findByNarmesteLederId(narmesteLederId) != null
        }

        val narmesteLeder = narmesteLederRepository.findByNarmesteLederId(narmesteLederId)!!
        narmesteLeder.narmesteLederTelefonnummer shouldBe "90909090"
        narmesteLeder.narmesteLederEpost shouldBe "test@nav.no"

        sendNarmesteLederLeesah(
            getNarmesteLederLeesah(
                narmesteLederId,
                telefonnummer = "98989898",
                epost = "mail@banken.no",
            ),
        )

        await().atMost(10, TimeUnit.SECONDS).until {
            narmesteLederRepository.findByNarmesteLederId(narmesteLederId)!!.narmesteLederEpost == "mail@banken.no"
        }

        val oppdaterNl = narmesteLederRepository.findByNarmesteLederId(narmesteLederId)!!
        oppdaterNl.id shouldBe narmesteLeder.id
        oppdaterNl.narmesteLederTelefonnummer shouldBe "98989898"
        oppdaterNl.narmesteLederEpost shouldBe "mail@banken.no"
    }

    @Test
    fun `Sletter nærmeste leder hvis den blir inaktiv`() {
        val narmesteLederId = UUID.randomUUID()
        val narmesteLederLeesah = getNarmesteLederLeesah(narmesteLederId)
        sendNarmesteLederLeesah(narmesteLederLeesah)

        await().atMost(10, TimeUnit.SECONDS).until {
            narmesteLederRepository.findByNarmesteLederId(narmesteLederId) != null
        }

        narmesteLederRepository.findByNarmesteLederId(narmesteLederId).shouldNotBeNull()

        sendNarmesteLederLeesah(
            getNarmesteLederLeesah(
                narmesteLederId,
                aktivTom = LocalDate.now(),
            ),
        )

        await().atMost(10, TimeUnit.SECONDS).until {
            narmesteLederRepository.findByNarmesteLederId(narmesteLederId) == null
        }
        narmesteLederRepository.findByNarmesteLederId(narmesteLederId).shouldBeNull()
    }

    @Test
    fun `Eksisterende ledertabell oppdateres også ved like og eldre tidsstempler`() {
        val id = UUID.randomUUID()
        val tid = OffsetDateTime.parse("2026-01-01T10:00:00Z")
        val aktiv = getNarmesteLederLeesah(id).copy(timestamp = tid)
        oppdateringAvNarmesteLeder.behandleMeldingFraKafka(aktiv.serialisertTilString())
        val lagret = narmesteLederRepository.findByNarmesteLederId(id).shouldNotBeNull()

        val likeTid = aktiv.copy(narmesteLederEpost = "annen@nav.no")
        oppdateringAvNarmesteLeder.behandleMeldingFraKafka(likeTid.serialisertTilString())
        narmesteLederRepository.findByNarmesteLederId(id).shouldNotBeNull().apply {
            this.id shouldBe lagret.id
            narmesteLederEpost shouldBe likeTid.narmesteLederEpost
        }

        val eldreTid = likeTid.copy(timestamp = tid.minusSeconds(1), narmesteLederTelefonnummer = "10101010")
        oppdateringAvNarmesteLeder.behandleMeldingFraKafka(eldreTid.serialisertTilString())
        narmesteLederRepository.findByNarmesteLederId(id).shouldNotBeNull().apply {
            timestamp shouldBe eldreTid.timestamp.toInstant()
            narmesteLederTelefonnummer shouldBe eldreTid.narmesteLederTelefonnummer
        }

        oppdateringAvNarmesteLeder.behandleMeldingFraKafka(eldreTid.copy(aktivTom = LocalDate.now()).serialisertTilString())
        narmesteLederRepository.findByNarmesteLederId(id).shouldBeNull()
    }

    @Test
    fun `Begge consumers oppdaterer bare sin egen tabell for samme relasjon`() {
        val container = checkNotNull(kafkaListenerEndpointRegistry.getListenerContainer("narmesteleder-esyfo"))
        val gammel = getNarmesteLederLeesah(UUID.randomUUID()).copy(timestamp = OffsetDateTime.parse("2026-01-01T10:00:00Z"))
        val ny = gammel.copy(narmesteLederEpost = "esyfo@nav.no")
        try {
            container.start()
            publiserTilBeggeTabeller(gammel, ny)
            kontrollerGammelOppdateringMedAktivEsyfoConsumer(gammel, ny)
            kontrollerEsyfoOppdateringMedAktivGammelConsumer(gammel, ny)
        } finally {
            val stoppet = CountDownLatch(1)
            container.stop { stoppet.countDown() }
            check(stoppet.await(20, TimeUnit.SECONDS))
            esyfo.deleteByNarmesteLederId(gammel.narmesteLederId)
        }
    }

    private fun publiserTilBeggeTabeller(
        gammel: NarmesteLederLeesah,
        ny: NarmesteLederLeesah,
    ) {
        kafkaProducer.send(ProducerRecord(NARMESTELEDER_LEESAH_TOPIC, gammel.narmesteLederId.toString(), null)).get()
        sendNarmesteLederLeesah(gammel)
        sendEsyfoMelding(ny)
        await().atMost(20, TimeUnit.SECONDS).untilAsserted {
            narmesteLederRepository.findByNarmesteLederId(gammel.narmesteLederId)?.narmesteLederEpost shouldBe gammel.narmesteLederEpost
            esyfo.findByNarmesteLederId(ny.narmesteLederId)?.narmesteLederEpost shouldBe ny.narmesteLederEpost
        }
    }

    private fun kontrollerGammelOppdateringMedAktivEsyfoConsumer(
        gammel: NarmesteLederLeesah,
        ny: NarmesteLederLeesah,
    ) {
        val lagretEsyfo = esyfo.findByNarmesteLederId(ny.narmesteLederId).shouldNotBeNull()
        val eldre = gammel.copy(timestamp = gammel.timestamp.minusDays(1), narmesteLederEpost = "oppdatert@nav.no")
        sendNarmesteLederLeesah(eldre)
        await().atMost(20, TimeUnit.SECONDS).untilAsserted {
            narmesteLederRepository.findByNarmesteLederId(gammel.narmesteLederId)?.narmesteLederEpost shouldBe eldre.narmesteLederEpost
        }
        esyfo.findByNarmesteLederId(ny.narmesteLederId) shouldBe lagretEsyfo
    }

    private fun kontrollerEsyfoOppdateringMedAktivGammelConsumer(
        gammel: NarmesteLederLeesah,
        ny: NarmesteLederLeesah,
    ) {
        val lagretGammel = narmesteLederRepository.findByNarmesteLederId(gammel.narmesteLederId).shouldNotBeNull()
        val oppdatert = ny.copy(narmesteLederEpost = "oppdatert-esyfo@nav.no")
        sendEsyfoMelding(oppdatert)
        await().atMost(20, TimeUnit.SECONDS).untilAsserted {
            esyfo.findByNarmesteLederId(ny.narmesteLederId)?.narmesteLederEpost shouldBe oppdatert.narmesteLederEpost
        }
        narmesteLederRepository.findByNarmesteLederId(gammel.narmesteLederId) shouldBe lagretGammel
    }

    private fun sendEsyfoMelding(melding: NarmesteLederLeesah) {
        kafkaProducer
            .send(
                ProducerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, 0, melding.narmesteLederId.toString(), melding.serialisertTilString()),
            ).get()
    }
}

fun getNarmesteLederLeesah(
    narmesteLederId: UUID,
    telefonnummer: String = "90909090",
    epost: String = "test@nav.no",
    aktivTom: LocalDate? = null,
): NarmesteLederLeesah =
    NarmesteLederLeesah(
        narmesteLederId = narmesteLederId,
        fnr = "12345678910",
        orgnummer = "999999999",
        narmesteLederFnr = "01987654321",
        narmesteLederTelefonnummer = telefonnummer,
        narmesteLederEpost = epost,
        aktivFom = LocalDate.now(),
        aktivTom = aktivTom,
        arbeidsgiverForskutterer = true,
        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
    )
