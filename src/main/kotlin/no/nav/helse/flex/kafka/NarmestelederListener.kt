package no.nav.helse.flex.kafka

import no.nav.helse.flex.narmesteleder.OppdateringAvNarmesteLeder
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class NarmestelederListener(
    private val oppdateringAvNarmesteLeder: OppdateringAvNarmesteLeder,
) {
    @KafkaListener(
        topics = [NARMESTELEDER_LEESAH_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(
        cr: ConsumerRecord<String, String?>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            cr.value()?.let(oppdateringAvNarmesteLeder::behandleMeldingFraKafka)
        } catch (_: Exception) {
            throw IllegalStateException("Nærmeste leder-record feilet")
        }
        acknowledgment.acknowledge()
    }
}

const val NARMESTELEDER_LEESAH_TOPIC = "teamsykmelding.syfo-narmesteleder-leesah"
const val NARMESTELEDER_ESYFO_LEESAH_TOPIC = "team-esyfo.syfo-narmesteleder-leesah"
