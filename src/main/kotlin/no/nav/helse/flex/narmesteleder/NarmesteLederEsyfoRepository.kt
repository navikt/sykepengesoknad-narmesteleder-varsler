package no.nav.helse.flex.narmesteleder

import no.nav.helse.flex.narmesteleder.domain.NarmesteLederEsyfo
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NarmesteLederEsyfoRepository : CrudRepository<NarmesteLederEsyfo, String> {
    fun deleteByNarmesteLederId(narmesteLederId: UUID)

    fun findByNarmesteLederId(narmesteLederId: UUID): NarmesteLederEsyfo?

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

    @Modifying
    @Query(
        """
        UPDATE narmeste_leder_esyfo_offset SET last_offset = :offset
        WHERE topic = :topic AND partition = :partition
        """,
    )
    fun lagreOffset(
        topic: String,
        partition: Int,
        offset: Long,
    ): Int
}
