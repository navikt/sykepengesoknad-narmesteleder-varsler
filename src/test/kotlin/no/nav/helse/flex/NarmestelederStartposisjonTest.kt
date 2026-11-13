package no.nav.helse.flex

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.kafka.KafkaConfig
import no.nav.helse.flex.kafka.SeekTilTidspunktUtenCommittedOffset
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.util.UUID

class NarmestelederStartposisjonTest : FellesTestOppsett() {
    @Autowired
    lateinit var kafkaConfig: KafkaConfig

    private val seek = SeekTilTidspunktUtenCommittedOffset(Duration.ofDays(2))
    private val na = Instant.now()

    @Test
    fun `Starter fra første melding i tidsvinduet når committed offset mangler`() {
        val partisjon = nyPartisjon()
        send(partisjon, na.minus(Duration.ofDays(3)))
        send(partisjon, na.minus(Duration.ofDays(1)))
        send(partisjon, na)

        lagConsumer().use { consumer ->
            consumer.assign(listOf(partisjon))
            seek.onPartitionsAssigned(consumer, listOf(partisjon))
            consumer.position(partisjon) shouldBe 1
        }
    }

    @Test
    fun `Starter fra slutten når ingen meldinger er i tidsvinduet`() {
        val partisjon = nyPartisjon()
        send(partisjon, na.minus(Duration.ofDays(4)))
        send(partisjon, na.minus(Duration.ofDays(3)))

        lagConsumer().use { consumer ->
            consumer.assign(listOf(partisjon))
            seek.onPartitionsAssigned(consumer, listOf(partisjon))
            consumer.position(partisjon) shouldBe 2
        }
    }

    @Test
    fun `Starter fra slutten når partisjonen er tom`() {
        val partisjon = nyPartisjon()
        Admin.create(kafkaConfig.commonConfig()).use {
            it.createTopics(listOf(NewTopic(partisjon.topic(), 1, 1))).all().get()
        }

        lagConsumer().use { consumer ->
            consumer.assign(listOf(partisjon))
            seek.onPartitionsAssigned(consumer, listOf(partisjon))
            consumer.position(partisjon) shouldBe 0
        }
    }

    @Test
    fun `Beholder committed offset`() {
        val partisjon = nyPartisjon()
        send(partisjon, na)
        send(partisjon, na)
        send(partisjon, na)
        val gruppe = UUID.randomUUID().toString()
        lagConsumer(gruppe).use { consumer ->
            consumer.assign(listOf(partisjon))
            consumer.commitSync(mapOf(partisjon to OffsetAndMetadata(2)))
        }

        lagConsumer(gruppe).use { consumer ->
            consumer.assign(listOf(partisjon))
            seek.onPartitionsAssigned(consumer, listOf(partisjon))
            consumer.position(partisjon) shouldBe 2
        }
    }

    private fun nyPartisjon() = TopicPartition("narmesteleder-startposisjon-${UUID.randomUUID()}", 0)

    private fun send(
        partisjon: TopicPartition,
        tidspunkt: Instant,
    ) {
        kafkaProducer
            .send(ProducerRecord(partisjon.topic(), partisjon.partition(), tidspunkt.toEpochMilli(), "nokkel", "verdi"))
            .get()
    }

    private fun lagConsumer(gruppe: String = UUID.randomUUID().toString()) =
        KafkaConsumer<String, String>(
            mapOf(
                ConsumerConfig.GROUP_ID_CONFIG to gruppe,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ) + kafkaConfig.commonConfig(),
        )
}
