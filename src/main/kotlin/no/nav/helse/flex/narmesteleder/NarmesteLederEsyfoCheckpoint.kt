package no.nav.helse.flex.narmesteleder

import no.nav.helse.flex.narmesteleder.domain.NarmesteLederEsyfoOffset
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederEsyfoOffsetId
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
    private val repository: NarmesteLederEsyfoOffsetRepository,
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
        repository.save(NarmesteLederEsyfoOffset(NarmesteLederEsyfoOffsetId(topic, partition), offset))
    }
}
