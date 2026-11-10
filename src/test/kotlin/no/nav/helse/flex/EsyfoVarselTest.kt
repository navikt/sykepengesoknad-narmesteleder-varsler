package no.nav.helse.flex

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.kafka.NARMESTELEDER_ESYFO_LEESAH_TOPIC
import no.nav.helse.flex.narmesteleder.NarmesteLederEsyfoRepository
import no.nav.helse.flex.narmesteleder.OppdateringAvNarmesteLeder
import no.nav.helse.flex.varsler.VarselUtsendelse
import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.INGEN_LEDER
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.PLANLAGT
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.SENDT
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.SENDT_SYKEPENGESOKNAD
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

class EsyfoVarselTest : FellesTestOppsett() {
    @Autowired lateinit var esyfo: NarmesteLederEsyfoRepository

    @Autowired lateinit var gammelOppdatering: OppdateringAvNarmesteLeder

    @Autowired lateinit var varselUtsendelse: VarselUtsendelse

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @Autowired
    @Qualifier("esyfoKafkaListenerContainerFactory")
    lateinit var factory: ConcurrentKafkaListenerContainerFactory<String, String>

    @Autowired
    @Qualifier("aivenKafkaListenerContainerFactory")
    lateinit var eksisterendeFactory: ConcurrentKafkaListenerContainerFactory<String, String>

    @AfterAll
    fun ryddEsyfo() {
        listenerRegistry.getListenerContainer("narmesteleder-esyfo")?.stop()
        esyfo.deleteAll()
        jdbc.update("DELETE FROM narmeste_leder_esyfo_offset")
    }

    @Test
    fun `Separat Kafka-gruppe bygger ny tabell mens begge varseltyper bruker eksisterende tabell`() {
        val container = listenerRegistry.getListenerContainer("narmesteleder-esyfo")!!
        container.isRunning shouldBe false
        factory.isBatchListener shouldBe true
        checkNotNull(factory.consumerFactory).configurationProperties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] shouldBe "500"
        (eksisterendeFactory.isBatchListener == true) shouldBe false
        checkNotNull(eksisterendeFactory.consumerFactory).configurationProperties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] shouldBe "1"

        val id = UUID.randomUUID()
        val leder = getNarmesteLederLeesah(id)
        val melding = leder.serialisertTilString().dropLast(1) + ",\"status\":null,\"nyttFelt\":true}"
        kafkaProducer
            .send(
                ProducerRecord(
                    NARMESTELEDER_ESYFO_LEESAH_TOPIC,
                    id.toString(),
                    leder.copy(aktivTom = LocalDate.now()).serialisertTilString(),
                ),
            ).get()
        val record = kafkaProducer.send(ProducerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, id.toString(), melding)).get()
        esyfo.findByNarmesteLederId(id) shouldBe null

        container.start()
        await().atMost(15, TimeUnit.SECONDS).until { container.assignedPartitions?.isNotEmpty() == true }
        container.isRunning shouldBe true
        container.groupId shouldBe "sykepengesoknad-narmesteleder-varsler-esyfo-test-bootstrap-v1"

        await().atMost(15, TimeUnit.SECONDS).until { esyfo.findByNarmesteLederId(id) != null }
        jdbc.queryForObject(
            "SELECT last_offset FROM narmeste_leder_esyfo_offset WHERE topic = ? AND partition = ?",
            Long::class.java,
            NARMESTELEDER_ESYFO_LEESAH_TOPIC,
            record.partition(),
        ) shouldBe record.offset()
        narmesteLederRepository.findByNarmesteLederId(id) shouldBe null
        narmesteLederRepository.findAktiveByBrukerFnrAndOrgnummer(leder.fnr, leder.orgnummer).shouldBeEmpty()

        // Replay alene oppretter aldri planlagte varsler, doknotifikasjoner eller oppgaver.
        planlagtVarselRepository.findAll().toList().shouldBeEmpty()
        doknotifikasjonKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
        hendelseKafkaConsumer.hentProduserteRecords().shouldBeEmpty()

        val inaktiv = leder.copy(aktivTom = LocalDate.now())
        kafkaProducer.send(ProducerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, id.toString(), inaktiv.serialisertTilString())).get()
        await().atMost(10, TimeUnit.SECONDS).until { esyfo.findByNarmesteLederId(id)?.aktivTom != null }
        kafkaProducer.send(ProducerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, id.toString(), melding)).get()
        await().atMost(10, TimeUnit.SECONDS).until { esyfo.findByNarmesteLederId(id)?.aktivTom == null }

        listOf(SENDT_SYKEPENGESOKNAD, MANGLENDE_SYKEPENGESOKNAD).forEach { type ->
            val varsel =
                planlagtVarselRepository.save(
                    PlanlagtVarsel(
                        oppdatert = Instant.now(),
                        orgnummer = leder.orgnummer,
                        brukerFnr = leder.fnr,
                        sykepengesoknadId = UUID.randomUUID().toString(),
                        status = PLANLAGT,
                        varselType = type,
                        sendes = Instant.now().minusSeconds(10),
                        narmesteLederId = null,
                    ),
                )
            varselUtsendelse.sendVarsler() shouldBe 0
            planlagtVarselRepository.findById(varsel.id!!).get().apply {
                status shouldBe INGEN_LEDER
                narmesteLederId shouldBe null
            }
            doknotifikasjonKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
            hendelseKafkaConsumer.hentProduserteRecords().shouldBeEmpty()

            val gammelLeder = leder.copy(narmesteLederId = UUID.randomUUID())
            gammelOppdatering.behandleMeldingFraKafka(gammelLeder.serialisertTilString())
            val nyttVarsel = planlagtVarselRepository.save(varsel.copy(id = null))
            varselUtsendelse.sendVarsler() shouldBe 1
            planlagtVarselRepository.findById(nyttVarsel.id!!).get().apply {
                status shouldBe SENDT
                narmesteLederId shouldBe gammelLeder.narmesteLederId
            }
            doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1, duration = Duration.ofSeconds(10))
            if (type == MANGLENDE_SYKEPENGESOKNAD) {
                hendelseKafkaConsumer.ventPåRecords(antall = 1, duration = Duration.ofSeconds(10))
            }
            gammelOppdatering.behandleMeldingFraKafka(gammelLeder.copy(aktivTom = LocalDate.now()).serialisertTilString())
        }

        kafkaProducer.send(ProducerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, id.toString(), null)).get()
        await().atMost(10, TimeUnit.SECONDS).until { esyfo.findByNarmesteLederId(id) == null }
        narmesteLederRepository.findByNarmesteLederId(id) shouldBe null

        // Gjenstart med samme gruppe: en upåført record på en annen key må fremdeles behandles.
        container.stop()
        val annenId = UUID.randomUUID()
        val annenLeder = leder.copy(narmesteLederId = annenId)
        kafkaProducer.send(ProducerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, annenId.toString(), annenLeder.serialisertTilString())).get()
        esyfo.findByNarmesteLederId(annenId) shouldBe null
        container.start()
        await().atMost(15, TimeUnit.SECONDS).until { esyfo.findByNarmesteLederId(annenId) != null }
        narmesteLederRepository.findByNarmesteLederId(annenId) shouldBe null
    }
}
