package no.nav.helse.flex.kafka

import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.config.ContainerPostProcessor
import org.springframework.kafka.listener.AbstractMessageListenerContainer
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

const val NARMESTELEDER_STARTPOSISJON = "narmestelederStartposisjon"

@Component(NARMESTELEDER_STARTPOSISJON)
class NarmestelederStartposisjon : ContainerPostProcessor<String, String, AbstractMessageListenerContainer<String, String>> {
    override fun postProcess(container: AbstractMessageListenerContainer<String, String>) {
        container.containerProperties.assignmentCommitOption = ContainerProperties.AssignmentCommitOption.NEVER
        container.containerProperties.setConsumerRebalanceListener(SeekTilTidspunktUtenCommittedOffset(Duration.ofDays(1)))
    }
}

internal class SeekTilTidspunktUtenCommittedOffset(
    private val tilbakeITid: Duration,
) : ConsumerAwareRebalanceListener {
    private val log = logger()

    override fun onPartitionsAssigned(
        consumer: Consumer<*, *>,
        partitions: Collection<TopicPartition>,
    ) {
        val committed = consumer.committed(partitions.toSet())
        val utenOffset = partitions.filter { committed[it] == null }
        if (utenOffset.isEmpty()) return

        val tidspunkt = Instant.now().minus(tilbakeITid)
        val offsetsForTidspunkt = consumer.offsetsForTimes(utenOffset.associateWith { tidspunkt.toEpochMilli() })
        val sluttOffsets = consumer.endOffsets(utenOffset)
        utenOffset.forEach { partisjon ->
            val offset = offsetsForTidspunkt[partisjon]?.offset() ?: sluttOffsets.getValue(partisjon)
            consumer.seek(partisjon, offset)
            log.info("Mangler committed offset for $partisjon. Starter fra offset $offset ($tidspunkt)")
        }
    }
}
