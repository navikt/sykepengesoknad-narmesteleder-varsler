package no.nav.helse.flex.narmesteleder

import org.springframework.jdbc.core.JdbcTemplate
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
    private val jdbcTemplate: JdbcTemplate,
) : NarmesteLederEsyfoCheckpoint {
    override fun lasOgHentOffset(
        topic: String,
        partition: Int,
    ): Long {
        jdbcTemplate.update(
            "INSERT INTO narmeste_leder_esyfo_offset (topic, partition, last_offset) VALUES (?, ?, -1) ON CONFLICT DO NOTHING",
            topic,
            partition,
        )
        return jdbcTemplate.queryForObject(
            "SELECT last_offset FROM narmeste_leder_esyfo_offset WHERE topic = ? AND partition = ? FOR UPDATE",
            Long::class.java,
            topic,
            partition,
        ) ?: error("Mangler checkpoint")
    }

    override fun lagreOffset(
        topic: String,
        partition: Int,
        offset: Long,
    ) {
        jdbcTemplate.update(
            "UPDATE narmeste_leder_esyfo_offset SET last_offset = ? WHERE topic = ? AND partition = ?",
            offset,
            topic,
            partition,
        )
    }
}
