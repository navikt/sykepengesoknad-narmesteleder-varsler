package no.nav.helse.flex.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TestKafkaConfig() {

    @Bean
    fun stringKafkaProducer(properties: KafkaProperties): KafkaProducer<String, String> {
        val config = mapOf(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 10,
            ProducerConfig.RETRY_BACKOFF_MS_CONFIG to 100,
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to properties.bootstrapServers[0],
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "PLAINTEXT"
        )

        return KafkaProducer(config)
    }
}
