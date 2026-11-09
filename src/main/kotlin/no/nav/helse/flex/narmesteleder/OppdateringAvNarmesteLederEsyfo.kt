package no.nav.helse.flex.narmesteleder

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import no.nav.helse.flex.kafka.NARMESTELEDER_ESYFO_LEESAH_TOPIC
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederLeesah
import no.nav.helse.flex.objectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.readValue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val KANONISK_UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

@Component
class OppdateringAvNarmesteLederEsyfo(
    private val jdbcTemplate: JdbcTemplate,
    private val registry: MeterRegistry,
) {
    private val registrertePartisjoner = ConcurrentHashMap.newKeySet<Int>()

    @PostConstruct
    fun registrerMetrikker() {
        listOf("aktiv" to "IS NULL", "inaktiv" to "IS NOT NULL").forEach { (tilstand, betingelse) ->
            Gauge
                .builder("narmeste_leder_esyfo_antall", this) {
                    jdbcTemplate
                        .queryForObject(
                            "SELECT count(*) FROM narmeste_leder_esyfo WHERE aktiv_tom $betingelse",
                            Long::class.java,
                        )?.toDouble() ?: 0.0
                }.tag("tilstand", tilstand)
                .register(registry)
        }
        val partisjoner =
            jdbcTemplate.query(
                "SELECT partition FROM narmeste_leder_esyfo_offset WHERE topic = ?",
                { rs, _ -> rs.getInt(1) },
                NARMESTELEDER_ESYFO_LEESAH_TOPIC,
            )
        partisjoner.forEach(::registrerCheckpoint)
    }

    private fun registrerCheckpoint(partition: Int) {
        if (registrertePartisjoner.add(partition)) {
            Gauge
                .builder("narmeste_leder_esyfo_checkpoint", this) {
                    jdbcTemplate
                        .query(
                            "SELECT last_offset FROM narmeste_leder_esyfo_offset WHERE topic = ? AND partition = ?",
                            { rs, _ -> rs.getLong(1).toDouble() },
                            NARMESTELEDER_ESYFO_LEESAH_TOPIC,
                            partition,
                        ).singleOrNull() ?: -1.0
                }.tag("partition", partition.toString())
                .register(registry)
        }
    }

    /** Kun kalt synkront av en Kafka-listener med én behandlingsbane per partisjon. */
    @Transactional
    fun behandle(
        topic: String,
        partition: Int,
        offset: Long,
        key: String?,
        value: String?,
    ): Boolean {
        require(topic == NARMESTELEDER_ESYFO_LEESAH_TOPIC && partition >= 0 && offset >= 0) { "Ugyldig Kafka-posisjon" }
        require(key != null && KANONISK_UUID.matches(key)) { "Ugyldig nøkkel i nærmeste leder-record" }
        val id = UUID.fromString(key)
        val leder =
            value?.let {
                try {
                    objectMapper.readValue<NarmesteLederLeesah>(it)
                } catch (_: JacksonException) {
                    throw IllegalArgumentException("Ugyldig nærmeste leder-record")
                }
            }
        require(leder == null || (leder.narmesteLederId == id && leder.orgnummer.matches(Regex("[0-9]{9}")))) {
            "Ugyldig nærmeste leder-record"
        }

        // Konflikttrygg init og radlås gjelder også når to pods ser en helt tom checkpoint-tabell.
        jdbcTemplate.update(
            "INSERT INTO narmeste_leder_esyfo_offset (topic, partition, last_offset) VALUES (?, ?, -1) ON CONFLICT DO NOTHING",
            topic,
            partition,
        )
        val siste =
            jdbcTemplate.queryForObject(
                "SELECT last_offset FROM narmeste_leder_esyfo_offset WHERE topic = ? AND partition = ? FOR UPDATE",
                Long::class.java,
                topic,
                partition,
            ) ?: error("Mangler checkpoint")
        if (offset <= siste) {
            registry.counter("narmeste_leder_esyfo_record", "resultat", "duplikat").increment()
            return false
        }
        if (leder == null) {
            jdbcTemplate.update("DELETE FROM narmeste_leder_esyfo WHERE narmeste_leder_id = ?", id)
        } else {
            jdbcTemplate.update(
                """
                INSERT INTO narmeste_leder_esyfo
                    (oppdatert, narmeste_leder_id, orgnummer, bruker_fnr, narmeste_leder_fnr,
                     narmeste_leder_telefonnummer, narmeste_leder_epost, aktiv_fom, aktiv_tom,
                     arbeidsgiver_forskutterer, timestamp)
                VALUES (now(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (narmeste_leder_id) DO UPDATE SET
                    oppdatert = EXCLUDED.oppdatert,
                    orgnummer = EXCLUDED.orgnummer,
                    bruker_fnr = EXCLUDED.bruker_fnr,
                    narmeste_leder_fnr = EXCLUDED.narmeste_leder_fnr,
                    narmeste_leder_telefonnummer = EXCLUDED.narmeste_leder_telefonnummer,
                    narmeste_leder_epost = EXCLUDED.narmeste_leder_epost,
                    aktiv_fom = EXCLUDED.aktiv_fom,
                    aktiv_tom = EXCLUDED.aktiv_tom,
                    arbeidsgiver_forskutterer = EXCLUDED.arbeidsgiver_forskutterer,
                    timestamp = EXCLUDED.timestamp
                """.trimIndent(),
                id,
                leder.orgnummer,
                leder.fnr,
                leder.narmesteLederFnr,
                leder.narmesteLederTelefonnummer,
                leder.narmesteLederEpost,
                leder.aktivFom,
                leder.aktivTom,
                leder.arbeidsgiverForskutterer,
                leder.timestamp,
            )
        }
        jdbcTemplate.update(
            "UPDATE narmeste_leder_esyfo_offset SET last_offset = ? WHERE topic = ? AND partition = ?",
            offset,
            topic,
            partition,
        )
        registrerCheckpoint(partition)
        // Tellere er observasjon, ikke transaksjonell tilstand. Checkpoint og projeksjon er atomiske.
        registry.counter("narmeste_leder_esyfo_record", "resultat", if (leder == null) "tombstone" else "verdi").increment()
        return true
    }
}
