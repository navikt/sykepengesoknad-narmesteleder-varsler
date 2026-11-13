package no.nav.helse.flex.kafka

import no.nav.helse.flex.narmesteleder.OppdateringAvNarmesteLeder
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class NarmestelederEsyfoListener(
    private val oppdateringAvNarmesteLeder: OppdateringAvNarmesteLeder,
) {
    @KafkaListener(
        topics = [NARMESTELEDER_ESYFO_LEESAH_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
        containerPostProcessor = NARMESTELEDER_STARTPOSISJON,
        autoStartup = "\${narmeste-leder.esyfo-listener-aktiv:true}",
    )
    fun listen(
        cr: ConsumerRecord<String, String?>,
        acknowledgment: Acknowledgment,
    ) {
        val verdi = cr.value()
        if (verdi == null) {
            oppdateringAvNarmesteLeder.behandleTombstone(UUID.fromString(cr.key()))
        } else {
            oppdateringAvNarmesteLeder.behandleMeldingFraKafka(verdi)
        }
        acknowledgment.acknowledge()
    }
}

const val NARMESTELEDER_ESYFO_LEESAH_TOPIC = "team-esyfo.syfo-narmesteleder-leesah"
