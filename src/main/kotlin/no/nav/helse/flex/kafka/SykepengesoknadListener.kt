package no.nav.helse.flex.kafka

import no.nav.helse.flex.brukeroppgave.BrukeroppgaveOpprettelse
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class SykepengesoknadListener(
    private val brukeroppgaveOpprettelse: BrukeroppgaveOpprettelse
) {

    private val log = logger()

    @KafkaListener(
        topics = [FLEX_SYKEPENGESOKNAD_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        try {
            brukeroppgaveOpprettelse.opprettBrukeroppgave(cr.value())
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            log.error("Feil ved mottak av record med key: ${cr.key()} offset: ${cr.offset()} partition: ${cr.partition()}", e)
            throw e
        }
    }
}
const val FLEX_SYKEPENGESOKNAD_TOPIC = "flex.sykepengesoknad"
