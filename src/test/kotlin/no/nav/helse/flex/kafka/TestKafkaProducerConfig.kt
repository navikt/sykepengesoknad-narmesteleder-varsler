package no.nav.helse.flex.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TestKafkaProducerConfig(
    private val aivenKafkaConfig: KafkaConfig,
) {

    @Bean
    fun stringStringProducer(): KafkaProducer<String, String> {
        return KafkaProducer<String, String>(aivenKafkaConfig.producerConfig())
    }
}
