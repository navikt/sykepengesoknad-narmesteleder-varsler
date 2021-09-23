package no.nav.helse.flex.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.logger
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederLeesah
import no.nav.helse.flex.objectMapper
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
            val narmesteLederLeesah = cr.value().tilNarmesteLederLeesah()
            if (narmesteLederLeesah.narmesteLederId.toString() == "e9cfe117-f882-422f-809c-d362ed20435e") {
                val timestamp = narmesteLederLeesah.timestamp
                val fom = narmesteLederLeesah.aktivFom
                val tom = narmesteLederLeesah.aktivTom
                val forskutterer = narmesteLederLeesah.arbeidsgiverForskutterer
                log.info("NL ENDRING: $timestamp # $fom # $tom # $forskutterer")
            }
        } catch (e: Exception) {
            log.error(
                "Feil ved mottak av record med key: ${cr.key()} offset: ${cr.offset()} partition: ${cr.partition()}",
                e
            )
            throw e
        }
    }

    fun String.tilNarmesteLederLeesah(): NarmesteLederLeesah = objectMapper.readValue(this)
}
