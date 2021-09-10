package no.nav.helse.flex.kafka

import no.nav.helse.flex.logger
import no.nav.helse.flex.narmesteleder.OppdateringAvNarmesteLeder
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class NarmestelederListener(
    private val oppdateringAvNarmesteLeder: OppdateringAvNarmesteLeder
) {

    private val log = logger()

    @KafkaListener(
        topics = [NARMESTELEDER_LEESAH_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        try {
            oppdateringAvNarmesteLeder.behandleMeldingFraKafka(cr.value())
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            log.error(
                "Feil ved mottak av record med key: ${cr.key()} offset: ${cr.offset()} partition: ${cr.partition()}",
                e
            )
            throw e
        }
    }
}

const val NARMESTELEDER_LEESAH_TOPIC = "teamsykmelding.syfo-narmesteleder-leesah"
