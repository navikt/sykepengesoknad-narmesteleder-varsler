package no.nav.helse.flex

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.kafka.NARMESTELEDER_ESYFO_LEESAH_TOPIC
import no.nav.helse.flex.narmesteleder.NarmesteLederEsyfoRepository
import no.nav.helse.flex.narmesteleder.OppdateringAvNarmesteLeder
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederLeesah
import no.nav.helse.flex.varsler.VarselUtsendelse
import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.INGEN_LEDER
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.PLANLAGT
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus.SENDT
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.MANGLENDE_SYKEPENGESOKNAD
import no.nav.helse.flex.varsler.domain.PlanlagtVarselType.SENDT_SYKEPENGESOKNAD
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.listener.MessageListenerContainer
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
        val container = checkNotNull(listenerRegistry.getListenerContainer("narmesteleder-esyfo"))
        kontrollerConsumerInnstillinger(container)
        val leder = getNarmesteLederLeesah(UUID.randomUUID()).copy(orgnummer = "999999999")
        val sisteHistoriskeRecord = publiserHistorikkMensConsumerenErAv(leder)

        startEsyfoConsumer(container)
        kontrollerHistorikkLagretUtenVarsling(leder, sisteHistoriskeRecord)
        kontrollerAvslutningOgReaktivering(leder)
        kontrollerBeggeVarseltyperBrukerEksisterendeTabell(leder)
        kontrollerTombstone(leder)
        kontrollerGjenstartMedSammeGruppe(container, leder)
    }

    private fun kontrollerConsumerInnstillinger(container: MessageListenerContainer) {
        container.isRunning shouldBe false
        factory.isBatchListener shouldBe true
        checkNotNull(factory.consumerFactory).configurationProperties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] shouldBe "500"
        (eksisterendeFactory.isBatchListener == true) shouldBe false
        checkNotNull(eksisterendeFactory.consumerFactory).configurationProperties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] shouldBe "1"
    }

    private fun publiserHistorikkMensConsumerenErAv(leder: NarmesteLederLeesah): RecordMetadata {
        sendEsyfoMelding(leder.copy(aktivTom = LocalDate.now()))
        val sisteRecord = sendEsyfoMelding(leder, meldingMedUkjenteFelter(leder))
        esyfo.findByNarmesteLederId(leder.narmesteLederId) shouldBe null
        return sisteRecord
    }

    private fun startEsyfoConsumer(container: MessageListenerContainer) {
        container.start()
        await().atMost(15, TimeUnit.SECONDS).until { container.assignedPartitions?.isNotEmpty() == true }
        container.isRunning shouldBe true
        container.groupId shouldBe "narmesteleder-varsler-esyfo"
    }

    private fun kontrollerHistorikkLagretUtenVarsling(
        leder: NarmesteLederLeesah,
        sisteRecord: RecordMetadata,
    ) {
        await().atMost(15, TimeUnit.SECONDS).until { esyfo.findByNarmesteLederId(leder.narmesteLederId) != null }
        jdbc.queryForObject(
            "SELECT last_offset FROM narmeste_leder_esyfo_offset WHERE topic = ? AND partition = ?",
            Long::class.java,
            NARMESTELEDER_ESYFO_LEESAH_TOPIC,
            sisteRecord.partition(),
        ) shouldBe sisteRecord.offset()
        narmesteLederRepository.findByNarmesteLederId(leder.narmesteLederId) shouldBe null
        narmesteLederRepository.findByBrukerFnrAndOrgnummer(leder.fnr, leder.orgnummer).shouldBeEmpty()

        planlagtVarselRepository.findAll().toList().shouldBeEmpty()
        doknotifikasjonKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
        hendelseKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    private fun kontrollerAvslutningOgReaktivering(leder: NarmesteLederLeesah) {
        sendEsyfoMelding(leder.copy(aktivTom = LocalDate.now()))
        await().atMost(10, TimeUnit.SECONDS).until { esyfo.findByNarmesteLederId(leder.narmesteLederId)?.aktivTom != null }
        sendEsyfoMelding(leder, meldingMedUkjenteFelter(leder))
        await().atMost(10, TimeUnit.SECONDS).until { esyfo.findByNarmesteLederId(leder.narmesteLederId)?.aktivTom == null }
    }

    private fun kontrollerBeggeVarseltyperBrukerEksisterendeTabell(leder: NarmesteLederLeesah) {
        listOf(SENDT_SYKEPENGESOKNAD, MANGLENDE_SYKEPENGESOKNAD).forEach { type ->
            val varsel = lagPlanlagtVarsel(leder, type)
            kontrollerAtEsyfoLederIkkeBrukesTilVarsling(varsel)

            val gammelLeder = leder.copy(narmesteLederId = UUID.randomUUID())
            gammelOppdatering.behandleMeldingFraKafka(gammelLeder.serialisertTilString())
            val nyttVarsel = planlagtVarselRepository.save(varsel.copy(id = null))
            kontrollerAtVarselSendesTilEksisterendeLeder(nyttVarsel, gammelLeder)
            gammelOppdatering.behandleMeldingFraKafka(gammelLeder.copy(aktivTom = LocalDate.now()).serialisertTilString())
        }
    }

    private fun kontrollerAtEsyfoLederIkkeBrukesTilVarsling(varsel: PlanlagtVarsel) {
        varselUtsendelse.sendVarsler() shouldBe 0
        planlagtVarselRepository.findById(varsel.id!!).get().apply {
            status shouldBe INGEN_LEDER
            narmesteLederId shouldBe null
        }
        doknotifikasjonKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
        hendelseKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    private fun kontrollerAtVarselSendesTilEksisterendeLeder(
        varsel: PlanlagtVarsel,
        leder: NarmesteLederLeesah,
    ) {
        varselUtsendelse.sendVarsler() shouldBe 1
        planlagtVarselRepository.findById(varsel.id!!).get().apply {
            status shouldBe SENDT
            narmesteLederId shouldBe leder.narmesteLederId
        }
        doknotifikasjonKafkaConsumer.ventPåRecords(antall = 1, duration = Duration.ofSeconds(10))
        if (varsel.varselType == MANGLENDE_SYKEPENGESOKNAD) {
            hendelseKafkaConsumer.ventPåRecords(antall = 1, duration = Duration.ofSeconds(10))
        }
    }

    private fun kontrollerTombstone(leder: NarmesteLederLeesah) {
        sendEsyfoMelding(leder, null)
        await().atMost(10, TimeUnit.SECONDS).until { esyfo.findByNarmesteLederId(leder.narmesteLederId) == null }
        narmesteLederRepository.findByNarmesteLederId(leder.narmesteLederId) shouldBe null
    }

    private fun kontrollerGjenstartMedSammeGruppe(
        container: MessageListenerContainer,
        leder: NarmesteLederLeesah,
    ) {
        container.stop()
        val annenId = UUID.randomUUID()
        sendEsyfoMelding(leder.copy(narmesteLederId = annenId))
        esyfo.findByNarmesteLederId(annenId) shouldBe null
        container.start()
        await().atMost(15, TimeUnit.SECONDS).until { esyfo.findByNarmesteLederId(annenId) != null }
        narmesteLederRepository.findByNarmesteLederId(annenId) shouldBe null
    }

    private fun lagPlanlagtVarsel(
        leder: NarmesteLederLeesah,
        type: PlanlagtVarselType,
    ) = planlagtVarselRepository.save(
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

    private fun sendEsyfoMelding(
        leder: NarmesteLederLeesah,
        verdi: String? = leder.serialisertTilString(),
    ): RecordMetadata = kafkaProducer.send(ProducerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, leder.narmesteLederId.toString(), verdi)).get()

    private fun meldingMedUkjenteFelter(leder: NarmesteLederLeesah) =
        leder.serialisertTilString().dropLast(1) + ",\"status\":null,\"nyttFelt\":true}"
}
