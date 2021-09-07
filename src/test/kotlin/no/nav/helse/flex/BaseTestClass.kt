package no.nav.helse.flex

import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import no.nav.helse.flex.kafka.doknotifikasjonTopic
import no.nav.helse.flex.narmesteleder.NarmesteLederRepository
import no.nav.helse.flex.varsler.PlanlagtVarselRepository
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
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
import javax.annotation.PostConstruct

private class PostgreSQLContainer12 : PostgreSQLContainer<PostgreSQLContainer12>("postgres:12-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@EnableMockOAuth2Server
abstract class BaseTestClass {

    @Autowired
    lateinit var doknotifikasjonKafkaConsumer: Consumer<String, NotifikasjonMedkontaktInfo>

    var pdlMockServer: MockRestServiceServer? = null
    var syfoServiceStanglerMockServer: MockRestServiceServer? = null

    @Autowired
    lateinit var pdlRestTemplate: RestTemplate

    @Autowired
    lateinit var flexFssProxyRestTemplate: RestTemplate

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
        if (syfoServiceStanglerMockServer == null) {
            syfoServiceStanglerMockServer = MockRestServiceServer.createServer(flexFssProxyRestTemplate)
        }
    }

    @AfterAll
    fun `Vi tømmer databasen`() {
        narmesteLederRepository.deleteAll()
        planlagtVarselRepository.deleteAll()
    }

    @AfterAll
    fun `Vi leser sykepengesoknad topicet og feiler hvis noe finnes og slik at subklassetestene leser alt`() {
        doknotifikasjonKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @BeforeAll
    fun `Vi leser sykepengesoknad kafka topicet og feiler om noe eksisterer`() {
        doknotifikasjonKafkaConsumer.subscribeHvisIkkeSubscribed(doknotifikasjonTopic)
        doknotifikasjonKafkaConsumer.hentProduserteRecords().shouldBeEmpty()
    }
}
