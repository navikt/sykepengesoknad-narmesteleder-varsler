package no.nav.helse.flex.kafka

import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class NarmestelederEndringer() {

    private val log = logger()

    @KafkaListener(
        topics = ["teamsykmelding.syfo-narmesteleder-leesah"],
        containerFactory = "brukDenneContainerFactory",
        groupId = "finn-endringer-i-skjema",
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        try {
            log.info("MEDLING: ${cr.value()}")
        } catch (e: Exception) {
            log.error(
                "Feil ved mottak av record med key: ${cr.key()} offset: ${cr.offset()} partition: ${cr.partition()}",
                e
            )
            throw e
        }
    }
}
