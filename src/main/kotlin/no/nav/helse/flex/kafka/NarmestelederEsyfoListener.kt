package no.nav.helse.flex.kafka

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.narmesteleder.OppdateringAvNarmesteLederEsyfo
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class NarmestelederEsyfoListener(
    private val oppdatering: OppdateringAvNarmesteLederEsyfo,
    private val registry: MeterRegistry,
) {
    @KafkaListener(
        id = "narmesteleder-esyfo",
        groupId = "\${narmeste-leder.esyfo-gruppe}",
        topics = [NARMESTELEDER_ESYFO_LEESAH_TOPIC],
        containerFactory = "esyfoKafkaListenerContainerFactory",
        autoStartup = "\${narmeste-leder.esyfo-consumer-aktiv:false}",
        concurrency = "1",
    )
    fun listen(
        cr: ConsumerRecord<String, String?>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            oppdatering.behandle(cr.topic(), cr.partition(), cr.offset(), cr.key(), cr.value())
        } catch (_: Exception) {
            registry.counter("narmeste_leder_esyfo_record", "resultat", "feil").increment()
            // Ingen cause: parser- og SQL-unntak kan inneholde Kafka-payload eller persondata.
            throw IllegalStateException("Nærmeste leder-record feilet")
        }
        acknowledgment.acknowledge()
    }
}
