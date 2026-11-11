package no.nav.helse.flex.narmesteleder

import org.springframework.stereotype.Repository

interface NarmesteLederEsyfoCheckpoint {
    fun lasOgHentOffset(
        topic: String,
        partition: Int,
    ): Long

    fun lagreOffset(
        topic: String,
        partition: Int,
        offset: Long,
    )
}

@Repository("narmesteLederEsyfoCheckpoint")
class DatabaseNarmesteLederEsyfoCheckpoint(
    private val repository: NarmesteLederEsyfoRepository,
) : NarmesteLederEsyfoCheckpoint {
    override fun lasOgHentOffset(
        topic: String,
        partition: Int,
    ): Long {
        repository.opprettCheckpoint(topic, partition)
        return repository.hentOffsetForOppdatering(topic, partition) ?: error("Mangler checkpoint")
    }

    override fun lagreOffset(
        topic: String,
        partition: Int,
        offset: Long,
    ) {
        repository.lagreOffset(topic, partition, offset)
    }
}
