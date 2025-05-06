package no.nav.helse.flex.kafka

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.serializers.*
import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import java.io.Serializable
import java.util.HashMap

@Configuration
class TestKafkaBeans(
    private val kafkaConfig: KafkaConfig,
) {
    @Bean
    fun stringStringProducer(): KafkaProducer<String, String> =
        KafkaProducer<String, String>(kafkaConfig.producerConfig(StringSerializer::class.java))

    @Bean
    fun kafkaConsumer() = KafkaConsumer<String, String>(consumerConfig())

    private fun consumerConfig() =
        mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to "testing-group-id",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        ) + kafkaConfig.commonConfig()

    private fun producerConfig(): Map<String, Serializable> =
        mapOf(
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
            ProducerConfig.MAX_BLOCK_MS_CONFIG to "15000",
            ProducerConfig.RETRIES_CONFIG to "100000",
        )

    private fun avroProducerConfig(): Map<String, Serializable> =
        mapOf(
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to "http://ikke.i.bruk.nav",
            SaslConfigs.SASL_MECHANISM to "PLAIN",
        ) + producerConfig() + kafkaConfig.commonConfig()

    @Bean
    fun mockSchemaRegistryClient(): MockSchemaRegistryClient {
        val mockSchemaRegistryClient = MockSchemaRegistryClient()

        mockSchemaRegistryClient.register(
            "$DOKNOTIFIKASJON_TOPIC-value",
            AvroSchema(NotifikasjonMedkontaktInfo.`SCHEMA$`),
        )

        return mockSchemaRegistryClient
    }

    fun kafkaAvroDeserializer(): KafkaAvroDeserializer {
        val config = HashMap<String, Any>()
        config[AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS] = false
        config[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = "http://ikke.i.bruk.nav"
        config[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = true
        return KafkaAvroDeserializer(mockSchemaRegistryClient(), config)
    }

    fun testConsumerProps(groupId: String) =
        mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
            KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to "http://ikke.i.bruk.nav",
            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
        ) + kafkaConfig.commonConfig()

    @Bean
    fun oppgaveKafkaConsumer(): Consumer<String, NotifikasjonMedkontaktInfo> {
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaConsumerFactory(
            testConsumerProps("oppgave-consumer"),
            StringDeserializer(),
            kafkaAvroDeserializer() as Deserializer<NotifikasjonMedkontaktInfo>,
        ).createConsumer()
    }

    @Bean
    fun oppgaveKafkaProducer(mockSchemaRegistryClient: MockSchemaRegistryClient): Producer<String, NotifikasjonMedkontaktInfo> {
        val kafkaAvroSerializer = KafkaAvroSerializer(mockSchemaRegistryClient)
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaProducerFactory(
            avroProducerConfig(),
            StringSerializer(),
            kafkaAvroSerializer as Serializer<NotifikasjonMedkontaktInfo>,
        ).createProducer()
    }
}
