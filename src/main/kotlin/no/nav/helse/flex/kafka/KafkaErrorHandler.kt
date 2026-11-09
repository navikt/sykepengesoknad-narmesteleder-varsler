package no.nav.helse.flex.kafka

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import org.springframework.util.backoff.ExponentialBackOff
import no.nav.helse.flex.logger as slf4jLogger

@Component
class KafkaErrorHandler :
    DefaultErrorHandler(
        ExponentialBackOff(1000L, 1.5).apply {
            // 8 minutter er maksimal pause, ikke slutten på retry-vinduet.
            maxInterval = 60_000L * 8
        },
    ) {
    // Bruker aliased logger for unngå kollisjon med CommonErrorHandler.logger(): LogAccessor.
    val log = slf4jLogger()

    override fun handleRemaining(
        thrownException: java.lang.Exception,
        records: MutableList<ConsumerRecord<*, *>>,
        consumer: Consumer<*, *>,
        container: MessageListenerContainer,
    ) {
        records.forEach { record ->
            if (record.topic() in setOf(NARMESTELEDER_LEESAH_TOPIC, NARMESTELEDER_ESYFO_LEESAH_TOPIC)) {
                log.error(
                    "Feil i prosessering av nærmeste leder-record på topic ${record.topic()}, " +
                        "partisjon ${record.partition()}, offset ${record.offset()}",
                )
            } else {
                log.error(
                    "Feil i prosessering av record på topic ${record.topic()}, " +
                        "partisjon ${record.partition()}, offset ${record.offset()}",
                )
            }
        }
        if (records.isEmpty()) {
            log.error("Feil i listener uten records")
        }
        super.handleRemaining(thrownException, records, consumer, container)
    }
}
