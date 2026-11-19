package no.nav.helse.flex

import io.kotest.matchers.collections.shouldBeEmpty
import mockwebserver3.MockWebServer
import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import no.nav.helse.flex.fakes.FakesTestConfig
import no.nav.helse.flex.fakes.PdlClientFake
import no.nav.helse.flex.kafka.DINE_SYKMELDTE_HENDELSER_TOPIC
import no.nav.helse.flex.kafka.DOKNOTIFIKASJON_TOPIC
import no.nav.helse.flex.kafka.FLEX_SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.kafka.NARMESTELEDER_ESYFO_LEESAH_TOPIC
import no.nav.helse.flex.mockdispatcher.FellesQueueDispatcher
import no.nav.helse.flex.mockdispatcher.PdlMockDispatcher
import no.nav.helse.flex.narmesteleder.NarmesteLederRepository
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederLeesah
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.varsler.PlanlagtVarselRepository
import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Import
import org.testcontainers.containers.GenericContainer
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.temporal.ChronoUnit

private class PostgreSQLContainer18 : PostgreSQLContainer("postgres:18-alpine")

private class ValkeyContainer : GenericContainer<ValkeyContainer>(DockerImageName.parse("valkey/valkey:8.1-alpine"))

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@EnableMockOAuth2Server
@Import(FakesTestConfig::class)
abstract class FellesTestOppsett {
    @Autowired
    lateinit var kafkaProducer: Producer<String, String>

    @Autowired
    lateinit var doknotifikasjonKafkaConsumer: Consumer<String, NotifikasjonMedkontaktInfo>

    @Autowired
    lateinit var hendelseKafkaConsumer: Consumer<String, String>

    @Autowired
    lateinit var pdlClientFake: PdlClientFake

    @Autowired
    lateinit var cacheManager: CacheManager

    @Autowired
    lateinit var narmesteLederRepository: NarmesteLederRepository

    @Autowired
    lateinit var planlagtVarselRepository: PlanlagtVarselRepository

    final val fnr = "12345678901"
    final val aktorId = "aktorid123"

    companion object {
        val pdlMockWebServer = MockWebServer()

        init {
            PostgreSQLContainer18().also {
                it.start()
                System.setProperty("spring.datasource.url", it.jdbcUrl)
                System.setProperty("spring.datasource.username", it.username)
                System.setProperty("spring.datasource.password", it.password)
            }

            KafkaContainer(DockerImageName.parse("apache/kafka-native:4.4.0")).also {
                it.start()
                System.setProperty("KAFKA_BROKERS", it.bootstrapServers)
            }

            pdlMockWebServer.dispatcher = PdlMockDispatcher
            pdlMockWebServer.start()
            System.setProperty("PDL_BASE_URL", "http://${pdlMockWebServer.hostName}:${pdlMockWebServer.port}")

            ValkeyContainer().withExposedPorts(6379).also {
                it.start()
                System.setProperty("VALKEY_URI_IDENTER", "redis://${it.host}:${it.getMappedPort(6379)}")
                System.setProperty("VALKEY_USERNAME_IDENTER", "default")
                System.setProperty("VALKEY_PASSWORD_IDENTER", "")
            }
        }
    }

    @BeforeEach
    fun nullstillPdlOgIdentCache() {
        pdlClientFake.reset()
        cacheManager.getCache("flex-folkeregister-identer-med-historikk")?.clear()
    }

    @AfterEach
    fun `Alle svar som er lagt i kø i mock-dispatcherne er brukt`() {
        FellesQueueDispatcher.alle().forEach { dispatcher ->
            val harResponserIgjen = dispatcher.harResponserIgjen()
            dispatcher.clear()
            check(!harResponserIgjen) { "Det er ubrukte svar igjen i ${dispatcher::class.simpleName}" }
        }
    }

    @AfterAll
    fun `Vi tømmer databasen`() {
        narmesteLederRepository.deleteAll()
        planlagtVarselRepository.deleteAll()
    }

    @BeforeAll
    fun `Vi leser sykepengesoknad kafka topicet og feiler om noe eksisterer`() {
        doknotifikasjonKafkaConsumer.subscribeHvisIkkeSubscribed(DOKNOTIFIKASJON_TOPIC)
        doknotifikasjonKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @BeforeAll
    fun `Vi leser hendelse kafka topicet og feiler om noe eksisterer`() {
        hendelseKafkaConsumer.subscribeHvisIkkeSubscribed(DINE_SYKMELDTE_HENDELSER_TOPIC)
        hendelseKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @AfterAll
    fun `Vi leser sykepengesoknad topicet og feiler hvis noe finnes og slik at subklassetestene leser alt`() {
        doknotifikasjonKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @AfterAll
    fun `Vi leser hendeklse topicet og feiler hvis noe finnes og slik at subklassetestene leser alt`() {
        hendelseKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    fun sendNarmesteLederLeesah(nl: NarmesteLederLeesah) {
        kafkaProducer
            .send(
                ProducerRecord(
                    NARMESTELEDER_ESYFO_LEESAH_TOPIC,
                    null,
                    nl.narmesteLederId.toString(),
                    nl.serialisertTilString(),
                ),
            ).get()
    }

    fun sendSykepengesoknad(soknad: SykepengesoknadDTO) {
        kafkaProducer
            .send(
                ProducerRecord(
                    FLEX_SYKEPENGESOKNAD_TOPIC,
                    null,
                    soknad.id,
                    soknad.serialisertTilString(),
                ),
            ).get()
    }

    fun planlagteVarslerSomSendesFor(dager: Int): List<PlanlagtVarsel> =
        planlagtVarselRepository.findFirst300ByStatusAndSendesIsBefore(
            PlanlagtVarselStatus.PLANLAGT,
            Instant.now().plus(dager.toLong(), ChronoUnit.DAYS),
        )
}
