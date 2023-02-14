package no.nav.helse.flex

import jakarta.annotation.PostConstruct
import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import no.nav.helse.flex.kafka.FLEX_SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.kafka.NARMESTELEDER_LEESAH_TOPIC
import no.nav.helse.flex.kafka.dineSykmeldteHendelserTopic
import no.nav.helse.flex.kafka.doknotifikasjonTopic
import no.nav.helse.flex.narmesteleder.NarmesteLederRepository
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederLeesah
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.varsler.PlanlagtVarselRepository
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
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

private class PostgreSQLContainer12 : PostgreSQLContainer<PostgreSQLContainer12>("postgres:12-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@EnableMockOAuth2Server
abstract class Testoppsett {

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
            PostgreSQLContainer12().also {
                it.start()
                System.setProperty("spring.datasource.url", it.jdbcUrl)
                System.setProperty("spring.datasource.username", it.username)
                System.setProperty("spring.datasource.password", it.password)
            }

            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.1.1")).also {
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
        doknotifikasjonKafkaConsumer.subscribeHvisIkkeSubscribed(doknotifikasjonTopic)
        doknotifikasjonKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @BeforeAll
    fun `Vi leser hendelse kafka topicet og feiler om noe eksisterer`() {
        hendelseKafkaConsumer.subscribeHvisIkkeSubscribed(dineSykmeldteHendelserTopic)
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
        kafkaProducer.send(
            ProducerRecord(
                NARMESTELEDER_LEESAH_TOPIC,
                null,
                nl.narmesteLederId.toString(),
                nl.serialisertTilString()
            )
        ).get()
    }

    fun sendSykepengesoknad(soknad: SykepengesoknadDTO) {
        kafkaProducer.send(
            ProducerRecord(
                FLEX_SYKEPENGESOKNAD_TOPIC,
                null,
                soknad.id,
                soknad.serialisertTilString()
            )
        ).get()
    }
}
