package no.nav.helse.flex

import jakarta.annotation.PostConstruct
import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import no.nav.helse.flex.kafka.DINE_SYKMELDTE_HENDELSER_TOPIC
import no.nav.helse.flex.kafka.DOKNOTIFIKASJON_TOPIC
import no.nav.helse.flex.kafka.FLEX_SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.kafka.NARMESTELEDER_LEESAH_TOPIC
import no.nav.helse.flex.narmesteleder.NarmesteLederRepository
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederLeesah
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.varsler.PlanlagtVarselRepository
import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.temporal.ChronoUnit

private class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@EnableMockOAuth2Server
abstract class FellesTestOppsett {
    @Autowired
    lateinit var kafkaProducer: Producer<String, String>

    @Autowired
    lateinit var doknotifikasjonKafkaConsumer: Consumer<String, NotifikasjonMedkontaktInfo>

    @Autowired
    lateinit var hendelseKafkaConsumer: Consumer<String, String>

    var pdlMockServer: MockRestServiceServer? = null

    @Autowired
    lateinit var pdlRestTemplate: RestTemplate

    @Autowired
    lateinit var narmesteLederRepository: NarmesteLederRepository

    @Autowired
    lateinit var planlagtVarselRepository: PlanlagtVarselRepository

    final val fnr = "12345678901"
    final val aktorId = "aktorid123"

    companion object {
        init {
            PostgreSQLContainer14().also {
                it.start()
                System.setProperty("spring.datasource.url", it.jdbcUrl)
                System.setProperty("spring.datasource.username", it.username)
                System.setProperty("spring.datasource.password", it.password)
            }

            KafkaContainer(DockerImageName.parse("apache/kafka-native:3.9.1")).also {
                it.start()
                System.setProperty("KAFKA_BROKERS", it.bootstrapServers)
            }
        }
    }

    @PostConstruct
    fun setupRestServiceServers() {
        if (pdlMockServer == null) {
            pdlMockServer = MockRestServiceServer.createServer(pdlRestTemplate)
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
                    NARMESTELEDER_LEESAH_TOPIC,
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
