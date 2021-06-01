package no.nav.helse.flex.kafka

import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.*
import org.springframework.stereotype.Component
import org.springframework.util.backoff.ExponentialBackOff

@Component
class KafkaErrorHandler : SeekToCurrentErrorHandler(
    null,
    ExponentialBackOff(1000L, 1.5).also {
        it.maxInterval = 60_000L * 10
    }
) {
    val log = logger()

    override fun handle(
        thrownException: Exception,
        records: List<ConsumerRecord<*, *>>?,
        consumer: Consumer<*, *>?,
        container: MessageListenerContainer
    ) {
        log.error("Feil i listener:", thrownException)

        records?.forEach { record ->
            log.error(
                "Feil i prossesseringen av record med offset: ${record.offset()}, key: ${record.key()}",
            )
        }

        super.handle(thrownException, records, consumer, container)
    }
}
