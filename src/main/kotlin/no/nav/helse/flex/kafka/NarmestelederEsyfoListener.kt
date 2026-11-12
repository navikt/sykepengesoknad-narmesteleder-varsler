package no.nav.helse.flex.kafka

import no.nav.helse.flex.narmesteleder.OppdateringAvNarmesteLederEsyfo
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class NarmestelederEsyfoListener(
    private val oppdatering: OppdateringAvNarmesteLederEsyfo,
) {
    @KafkaListener(
        id = "narmesteleder-esyfo",
        groupId = "\${narmeste-leder.esyfo-gruppe}",
        topics = [NARMESTELEDER_ESYFO_LEESAH_TOPIC],
        containerFactory = "esyfoKafkaListenerContainerFactory",
        autoStartup = "\${spring.kafka.listener.auto-startup:true}",
        concurrency = "\${ESYFO_KAFKA_CONCURRENCY:1}",
    )
    fun listen(
        records: List<ConsumerRecord<String, String?>>,
        acknowledgment: Acknowledgment,
    ) {
        if (records.isEmpty()) return
        try {
            oppdatering.behandleBatch(records)
        } catch (_: Exception) {
            // Ingen cause: parser- og SQL-unntak kan inneholde Kafka-payload eller persondata.
            throw IllegalStateException("Nærmeste leder-batch feilet")
        }
        acknowledgment.acknowledge()
    }
}

const val NARMESTELEDER_ESYFO_LEESAH_TOPIC = "team-esyfo.syfo-narmesteleder-leesah"
