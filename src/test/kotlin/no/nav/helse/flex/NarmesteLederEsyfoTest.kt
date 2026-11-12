package no.nav.helse.flex

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.kafka.GAMMEL_KAFKA_GRUPPE
import no.nav.helse.flex.kafka.KafkaConfig
import no.nav.helse.flex.kafka.NARMESTELEDER_ESYFO_LEESAH_TOPIC
import no.nav.helse.flex.kafka.NarmestelederEsyfoListener
import no.nav.helse.flex.narmesteleder.NarmesteLederEsyfoRepository
import no.nav.helse.flex.narmesteleder.OppdateringAvNarmesteLeder
import no.nav.helse.flex.narmesteleder.OppdateringAvNarmesteLederEsyfo
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.support.Acknowledgment
import tools.jackson.core.JacksonException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NarmesteLederEsyfoTest : FellesTestOppsett() {
    @Autowired lateinit var esyfo: NarmesteLederEsyfoRepository

    @Autowired lateinit var oppdatering: OppdateringAvNarmesteLederEsyfo

    @Autowired lateinit var gammelOppdatering: OppdateringAvNarmesteLeder

    @Autowired lateinit var listener: NarmestelederEsyfoListener

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    private val topic = NARMESTELEDER_ESYFO_LEESAH_TOPIC

    @AfterAll
    fun ryddEsyfo() {
        esyfo.deleteAll()
        jdbc.update("DELETE FROM narmeste_leder_esyfo_offset")
    }

    private fun behandle(
        partisjon: Int,
        offset: Long,
        id: UUID,
        melding: String?,
    ): Boolean = oppdatering.behandle(ConsumerRecord(topic, partisjon, offset, id.toString(), melding))

    private fun checkpoint(partisjon: Int): Long? {
        val offseter =
            jdbc.query(
                "SELECT last_offset FROM narmeste_leder_esyfo_offset WHERE topic = ? AND partition = ?",
                { rs, _ -> rs.getLong(1) },
                topic,
                partisjon,
            )
        return offseter.singleOrNull()
    }

    @Test
    fun `Batch kvitteres én gang etter commit og bevarer rekkefølge og tombstones`() {
        val første = UUID.randomUUID()
        val andre = UUID.randomUUID()
        val batch =
            listOf<ConsumerRecord<String, String?>>(
                ConsumerRecord(topic, 917, 0L, første.toString(), getNarmesteLederLeesah(første).serialisertTilString()),
                ConsumerRecord(topic, 916, 0L, andre.toString(), getNarmesteLederLeesah(andre).serialisertTilString()),
                ConsumerRecord<String, String?>(topic, 917, 1L, første.toString(), null),
            )
        var kvitteringer = 0
        listener.listen(batch) {
            esyfo.findByNarmesteLederId(første).shouldBeNull()
            esyfo.findByNarmesteLederId(andre).shouldNotBeNull()
            checkpoint(916) shouldBe 0L
            checkpoint(917) shouldBe 1L
            kvitteringer++
        }
        kvitteringer shouldBe 1
        val lagret = esyfo.findByNarmesteLederId(andre)
        listener.listen(batch) { kvitteringer++ }
        kvitteringer shouldBe 2
        esyfo.findByNarmesteLederId(andre) shouldBe lagret
    }

    @Test
    fun `Feil i batch ruller tilbake alle partisjoner uten kvittering og kan forsøkes igjen`() {
        val første = UUID.randomUUID()
        val andre = UUID.randomUUID()
        val gyldig =
            ConsumerRecord<String, String?>(topic, 918, 0L, første.toString(), getNarmesteLederLeesah(første).serialisertTilString())
        var kvitteringer = 0
        val feil =
            shouldThrow<IllegalStateException> {
                listener.listen(listOf(gyldig, ConsumerRecord(topic, 919, 0L, andre.toString(), "{"))) { kvitteringer++ }
            }
        feil.cause.shouldBeNull()
        kvitteringer shouldBe 0
        esyfo.findByNarmesteLederId(første).shouldBeNull()
        checkpoint(918).shouldBeNull()
        checkpoint(919).shouldBeNull()

        listener.listen(
            listOf(ConsumerRecord(topic, 919, 0L, andre.toString(), getNarmesteLederLeesah(andre).serialisertTilString()), gyldig),
        ) { kvitteringer++ }
        kvitteringer shouldBe 1
        checkpoint(918) shouldBe 0L
        checkpoint(919) shouldBe 0L
    }

    @Test
    fun `Tom batch kvitteres ikke`() {
        var kvitteringer = 0
        listener.listen(emptyList()) { kvitteringer++ }
        kvitteringer shouldBe 0
    }

    @Test
    fun `Lagring oppdaterer alle relasjonsfeltene uten å endre radens ID`() {
        val id = UUID.randomUUID()
        val første = getNarmesteLederLeesah(id)
        behandle(921, 0, id, første.serialisertTilString()) shouldBe true
        val opprinnelig = esyfo.findByNarmesteLederId(id).shouldNotBeNull()
        val oppdatert =
            første.copy(
                orgnummer = "123456789",
                fnr = "00000000000",
                narmesteLederFnr = "00000000000",
                narmesteLederTelefonnummer = "00000000",
                narmesteLederEpost = "leder@example.test",
                aktivFom = LocalDate.parse("2026-01-01"),
                aktivTom = LocalDate.parse("2026-02-01"),
                arbeidsgiverForskutterer = false,
                timestamp = OffsetDateTime.parse("2026-02-01T10:00:00Z"),
            )

        behandle(921, 1, id, oppdatert.serialisertTilString()) shouldBe true

        val lagret = esyfo.findByNarmesteLederId(id).shouldNotBeNull()
        lagret shouldBe
            opprinnelig.copy(
                oppdatert = lagret.oppdatert,
                timestamp = oppdatert.timestamp.toInstant(),
                orgnummer = oppdatert.orgnummer,
                brukerFnr = oppdatert.fnr,
                narmesteLederFnr = oppdatert.narmesteLederFnr,
                narmesteLederTelefonnummer = oppdatert.narmesteLederTelefonnummer,
                narmesteLederEpost = oppdatert.narmesteLederEpost,
                aktivFom = oppdatert.aktivFom,
                aktivTom = oppdatert.aktivTom,
                arbeidsgiverForskutterer = oppdatert.arbeidsgiverForskutterer,
            )
    }

    @Test
    fun `Checkpoint følger siste offset ikke timestamp og tombstone forblir slettet ved omlevering`() {
        val id = UUID.randomUUID()
        val aktiv = getNarmesteLederLeesah(id)
        val inaktiv = aktiv.copy(aktivTom = LocalDate.now(), timestamp = aktiv.timestamp.minusDays(1))
        behandle(901, 4, id, aktiv.serialisertTilString()) shouldBe true
        behandle(901, 5, id, inaktiv.serialisertTilString()) shouldBe true
        esyfo.findByNarmesteLederId(id).shouldNotBeNull().aktivTom shouldBe inaktiv.aktivTom
        behandle(901, 5, id, aktiv.serialisertTilString()) shouldBe false
        behandle(901, 3, id, aktiv.serialisertTilString()) shouldBe false
        behandle(901, 6, id, null) shouldBe true
        esyfo.findByNarmesteLederId(id).shouldBeNull()
        behandle(901, 4, id, aktiv.serialisertTilString()) shouldBe false
        behandle(901, 6, id, null) shouldBe false
        checkpoint(901) shouldBe 6
        // En nyere Kafka-offset med eldre publiseringstid kan reaktivere en foreldet tilstand.
        behandle(901, 7, id, aktiv.copy(timestamp = aktiv.timestamp.minusDays(2)).serialisertTilString()) shouldBe true
        esyfo
            .findByNarmesteLederId(id)
            .shouldNotBeNull()
            .aktivTom
            .shouldBeNull()
        behandle(901, 8, id, null) shouldBe true
        esyfo.findByNarmesteLederId(id).shouldBeNull()
    }

    @Test
    fun `Ukjente felter og status tåles mens ugyldig key orgnummer og mismatch ikke flytter checkpoint`() {
        val id = UUID.randomUUID()
        val melding =
            getNarmesteLederLeesah(id).serialisertTilString().dropLast(1) +
                ",\"status\":\"NY_STATUS\",\"ekstra\":1}"
        behandle(902, 1, id, melding) shouldBe true
        esyfo.findByNarmesteLederId(id).shouldNotBeNull()
        shouldThrow<IllegalArgumentException> { oppdatering.behandle(ConsumerRecord(topic, 902, 2L, "ugyldig", null)) }
        shouldThrow<IllegalArgumentException> { behandle(902, 2, UUID.randomUUID(), melding) }
        shouldThrow<IllegalArgumentException> { behandle(902, 2, id, melding.replace("999999999", "999")) }
        shouldThrow<JacksonException> { behandle(902, 2, id, "{ugyldig") }
        checkpoint(902) shouldBe 1
        behandle(902, 2, id, null) shouldBe true
        checkpoint(902) shouldBe 2
    }

    @Test
    fun `Null og ugyldig Kafka-key avvises uten ack eller endring i projeksjon og checkpoint`() {
        val id = UUID.randomUUID()
        val melding = getNarmesteLederLeesah(id).serialisertTilString()
        behandle(913, 1, id, melding) shouldBe true
        val lagret = esyfo.findByNarmesteLederId(id).shouldNotBeNull()
        var antallAck = 0
        val ack =
            object : Acknowledgment {
                override fun acknowledge() {
                    antallAck++
                }
            }
        listOf<String?>(
            null,
            "ugyldig-uuid",
            id.toString().replace("-", ""),
            id.toString().replaceFirst("-", "_"),
            id.toString().dropLast(1) + "g",
            " $id",
            "$id ",
            "urn:uuid:$id",
            "{$id}",
        ).forEach { key ->
            listOf<String?>(null, melding).forEach { verdi ->
                val feil =
                    shouldThrow<IllegalStateException> {
                        listener.listen(listOf(ConsumerRecord(topic, 913, 2L, key, verdi)), ack)
                    }
                feil.cause shouldBe null
                antallAck shouldBe 0
                checkpoint(913) shouldBe 1
                esyfo.findByNarmesteLederId(id) shouldBe lagret
            }
            shouldThrow<IllegalStateException> {
                listener.listen(listOf(ConsumerRecord(topic, 914, 0L, key, melding)), ack)
            }
            checkpoint(914).shouldBeNull()
        }
        antallAck shouldBe 0
    }

    @Test
    fun `Kafka-key godtas med store og små heksadesimale bokstaver`() {
        val id = UUID.fromString("12345678-9abc-4def-8abc-123456789abc")
        val melding = getNarmesteLederLeesah(id).serialisertTilString()

        listOf(id.toString(), id.toString().uppercase()).forEachIndexed { offset, key ->
            oppdatering.behandle(ConsumerRecord(topic, 915, offset.toLong(), key, melding)) shouldBe true
            esyfo.findByNarmesteLederId(id).shouldNotBeNull().narmesteLederId shouldBe id
        }
        checkpoint(915) shouldBe 1
        oppdatering.behandle(ConsumerRecord(topic, 915, 2L, id.toString().uppercase(), null)) shouldBe true
        esyfo.findByNarmesteLederId(id).shouldBeNull()
        checkpoint(915) shouldBe 2
    }

    @Test
    fun `Allerede behandlet offset ignoreres før nøkkel og payload leses`() {
        val id = UUID.randomUUID()
        behandle(920, 1, id, getNarmesteLederLeesah(id).serialisertTilString()) shouldBe true
        val lagret = esyfo.findByNarmesteLederId(id).shouldNotBeNull()

        oppdatering.behandle(ConsumerRecord(topic, 920, 1L, null, "{ugyldig")) shouldBe false

        esyfo.findByNarmesteLederId(id) shouldBe lagret
        checkpoint(920) shouldBe 1L
    }

    @Test
    fun `Consumeren for eSyfo kan ikke bruke tom gruppe eller gruppen for team sykmeldings topic`() {
        fun config(gruppe: String) = KafkaConfig("", "none", "PLAINTEXT", "", "", "", "", "", "", gruppe)

        listOf("", "   ", GAMMEL_KAFKA_GRUPPE).forEach { gruppe ->
            shouldThrow<IllegalArgumentException> { config(gruppe) }.cause shouldBe null
        }
        config("egen-esyfo-bootstrap-gruppe")
    }

    @Test
    fun `Første checkpoint serialiseres ved samtidige skriv og eldre offset kan ikke overskrive`() {
        val id = UUID.randomUUID()
        val aktiv = getNarmesteLederLeesah(id)
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val resultater =
                listOf(20L, 10L).map { offset ->
                    pool.submit<Boolean> {
                        start.await()
                        val verdi = aktiv.copy(aktivTom = if (offset == 20L) LocalDate.now() else null)
                        behandle(903, offset, id, verdi.serialisertTilString())
                    }
                }
            start.countDown()
            resultater.forEach { it.get(10, TimeUnit.SECONDS) }
            checkpoint(903) shouldBe 20
            esyfo.findByNarmesteLederId(id).shouldNotBeNull().aktivTom shouldBe LocalDate.now()
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `Feil i SQL ruller tilbake både oppdatering og checkpoint og gir ikke ack`() {
        val id = UUID.randomUUID()
        val melding = getNarmesteLederLeesah(id).serialisertTilString()
        behandle(904, 1, id, melding)
        val antallAck = intArrayOf(0)
        val ack =
            object : Acknowledgment {
                override fun acknowledge() {
                    antallAck[0]++
                }
            }
        val record = ConsumerRecord(topic, 904, 2L, id.toString(), melding)
        jdbc.execute("ALTER TABLE narmeste_leder_esyfo ADD CONSTRAINT esyfo_test_feil CHECK (orgnummer <> '999999999') NOT VALID")
        try {
            shouldThrow<IllegalStateException> { listener.listen(listOf(record), ack) }
            antallAck[0] shouldBe 0
            checkpoint(904) shouldBe 1
            esyfo.findByNarmesteLederId(id).shouldNotBeNull()
        } finally {
            jdbc.execute("ALTER TABLE narmeste_leder_esyfo DROP CONSTRAINT esyfo_test_feil")
        }
        listener.listen(listOf(record), ack)
        antallAck[0] shouldBe 1
        checkpoint(904) shouldBe 2
        listener.listen(listOf(record), ack)
        antallAck[0] shouldBe 2
        checkpoint(904) shouldBe 2
    }

    @Test
    fun `Feil etter tombstone ruller tilbake slettingen og checkpoint`() {
        val id = UUID.randomUUID()
        behandle(906, 1, id, getNarmesteLederLeesah(id).serialisertTilString())
        jdbc.execute(
            "ALTER TABLE narmeste_leder_esyfo_offset ADD CONSTRAINT esyfo_test_checkpoint CHECK (last_offset <> 2) NOT VALID",
        )
        try {
            shouldThrow<Exception> { behandle(906, 2, id, null) }
            esyfo.findByNarmesteLederId(id).shouldNotBeNull()
            checkpoint(906) shouldBe 1
        } finally {
            jdbc.execute("ALTER TABLE narmeste_leder_esyfo_offset DROP CONSTRAINT esyfo_test_checkpoint")
        }
        behandle(906, 2, id, null) shouldBe true
        esyfo.findByNarmesteLederId(id).shouldBeNull()
        checkpoint(906) shouldBe 2
    }

    @Test
    fun `Innlesing endrer ikke eksisterende ledertabell og oppretter ingen varsler eller oppgaver`() {
        listenerRegistry.getListenerContainer("narmesteleder-esyfo")?.isRunning shouldBe false
        val id = UUID.randomUUID()
        val melding = getNarmesteLederLeesah(id)
        behandle(905, 1, id, melding.serialisertTilString())
        behandle(905, 2, id, melding.copy(aktivTom = LocalDate.now()).serialisertTilString())
        behandle(905, 3, id, null)
        narmesteLederRepository.findByBrukerFnrAndOrgnummer(melding.fnr, melding.orgnummer).shouldBeEmpty()
        narmesteLederRepository.findByNarmesteLederId(id).shouldBeNull()
        planlagtVarselRepository.findAll().toList().shouldBeEmpty()
        doknotifikasjonKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
        hendelseKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @Test
    fun `Inaktiv relasjon slettes fra eksisterende tabell og beholdes i eSyfo-tabellen`() {
        val id = UUID.randomUUID()
        val aktiv = getNarmesteLederLeesah(id).copy(orgnummer = "123123123")
        val inaktiv = aktiv.copy(aktivTom = LocalDate.now())
        gammelOppdatering.behandleMeldingFraKafka(aktiv.serialisertTilString())
        narmesteLederRepository.findByNarmesteLederId(id).shouldNotBeNull()

        gammelOppdatering.behandleMeldingFraKafka(inaktiv.serialisertTilString())
        behandle(909, 1, id, inaktiv.serialisertTilString()) shouldBe true
        narmesteLederRepository.findByNarmesteLederId(id).shouldBeNull()
        esyfo.findByNarmesteLederId(id).shouldNotBeNull().aktivTom shouldBe inaktiv.aktivTom
        narmesteLederRepository.findByBrukerFnrAndOrgnummer(aktiv.fnr, aktiv.orgnummer).shouldBeEmpty()
        planlagtVarselRepository.findAll().toList().shouldBeEmpty()
        doknotifikasjonKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
        hendelseKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @Test
    fun `Flere aktive relasjoner lagres i eSyfo-tabellen uten å endre eksisterende tabell`() {
        val en = getNarmesteLederLeesah(UUID.randomUUID())
        val to = en.copy(narmesteLederId = UUID.randomUUID())
        behandle(907, 1, en.narmesteLederId, en.serialisertTilString())
        behandle(907, 2, to.narmesteLederId, to.serialisertTilString())
        esyfo.findByNarmesteLederId(en.narmesteLederId).shouldNotBeNull()
        esyfo.findByNarmesteLederId(to.narmesteLederId).shouldNotBeNull()
        narmesteLederRepository.findByBrukerFnrAndOrgnummer(en.fnr, en.orgnummer).shouldBeEmpty()
    }
}
