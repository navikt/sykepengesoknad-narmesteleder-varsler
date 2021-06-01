package no.nav.helse.flex

import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractContainerBaseTest {

    companion object {
        init {
            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.1.1")).also {
                it.start()
                System.setProperty("KAFKA_BROKERS", it.bootstrapServers)
            }
        }
    }
}
