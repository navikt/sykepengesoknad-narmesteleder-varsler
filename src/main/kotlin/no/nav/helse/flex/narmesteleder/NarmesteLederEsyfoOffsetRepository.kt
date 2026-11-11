package no.nav.helse.flex.narmesteleder

import no.nav.helse.flex.narmesteleder.domain.NarmesteLederEsyfoOffset
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederEsyfoOffsetId
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface NarmesteLederEsyfoOffsetRepository : CrudRepository<NarmesteLederEsyfoOffset, NarmesteLederEsyfoOffsetId> {
    @Modifying
    @Query(
        """
        INSERT INTO narmeste_leder_esyfo_offset (topic, partition, last_offset)
        VALUES (:topic, :partition, -1) ON CONFLICT DO NOTHING
        """,
    )
    fun opprettCheckpoint(
        topic: String,
        partition: Int,
    ): Int

    @Query(
        """
        SELECT last_offset FROM narmeste_leder_esyfo_offset
        WHERE topic = :topic AND partition = :partition FOR UPDATE
        """,
    )
    fun hentOffsetForOppdatering(
        topic: String,
        partition: Int,
    ): Long?
}
