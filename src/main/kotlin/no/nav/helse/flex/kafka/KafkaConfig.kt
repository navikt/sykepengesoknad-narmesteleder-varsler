package no.nav.helse.flex.kafka

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import no.nav.doknotifikasjon.schemas.NotifikasjonMedkontaktInfo
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

private const val JAVA_KEYSTORE = "JKS"
private const val PKCS12 = "PKCS12"

@Configuration
class KafkaConfig(
    @param:Value("\${KAFKA_BROKERS}") private val kafkaBrokers: String,
    @param:Value("\${aiven-kafka.auto-offset-reset}") private val kafkaAutoOffsetReset: String,
    @param:Value("\${aiven-kafka.security-protocol}") private val kafkaSecurityProtocol: String,
    @param:Value("\${KAFKA_TRUSTSTORE_PATH}") private val kafkaTruststorePath: String,
    @param:Value("\${KAFKA_CREDSTORE_PASSWORD}") private val kafkaCredstorePassword: String,
    @param:Value("\${KAFKA_KEYSTORE_PATH}") private val kafkaKeystorePath: String,
    @param:Value("\${KAFKA_SCHEMA_REGISTRY}") private val schemaRegistryUrl: String,
    @param:Value("\${KAFKA_SCHEMA_REGISTRY_USER}") private val kafkaSchemaRegistryUsername: String,
    @param:Value("\${KAFKA_SCHEMA_REGISTRY_PASSWORD}") private val kafkaSchemaRegistryPassword: String,
) {
    fun commonConfig() =
        mapOf(
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
        ) + securityConfig()

    private fun securityConfig() =
        mapOf(
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
            // Disables server host name verification.
            SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
            SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to JAVA_KEYSTORE,
            SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to PKCS12,
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to kafkaTruststorePath,
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to kafkaKeystorePath,
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEY_PASSWORD_CONFIG to kafkaCredstorePassword,
        )

    @Bean
    fun aivenKafkaListenerContainerFactory(kafkaErrorHandler: KafkaErrorHandler): ConcurrentKafkaListenerContainerFactory<String, String> {
        val config =
            mapOf(
                ConsumerConfig.GROUP_ID_CONFIG to "sykepengesoknad-narmesteleder-varsler",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
                ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to "600000",
            ) + commonConfig()
        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(config)

        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory
        factory.setCommonErrorHandler(kafkaErrorHandler)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        return factory
    }

    @Bean
    @Profile("default")
    fun doknotifikasjonProducer(): KafkaProducer<String, NotifikasjonMedkontaktInfo> =
        KafkaProducer<String, NotifikasjonMedkontaktInfo>(
            producerConfig(KafkaAvroSerializer::class.java) +
                mapOf(
                    KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to schemaRegistryUrl,
                    KafkaAvroSerializerConfig.USER_INFO_CONFIG to "$kafkaSchemaRegistryUsername:$kafkaSchemaRegistryPassword",
                    KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
                ),
        )

    @Bean
    @Profile("default")
    fun dineSykmeldteHendelserProducer(): KafkaProducer<String, String> =
        KafkaProducer<String, String>(producerConfig(StringSerializer::class.java))

    fun producerConfig(valueSerializer: Class<*>) =
        mapOf(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to valueSerializer,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 10,
            ProducerConfig.RETRY_BACKOFF_MS_CONFIG to 100,
        ) + commonConfig()
}

const val DOKNOTIFIKASJON_TOPIC = "teamdokumenthandtering.privat-dok-notifikasjon-med-kontakt-info"
const val DINE_SYKMELDTE_HENDELSER_TOPIC = "team-esyfo.dinesykmeldte-hendelser-v2"
