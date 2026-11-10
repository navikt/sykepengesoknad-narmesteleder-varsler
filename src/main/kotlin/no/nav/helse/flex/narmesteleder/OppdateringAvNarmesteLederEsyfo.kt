package no.nav.helse.flex.narmesteleder

import no.nav.helse.flex.kafka.NARMESTELEDER_ESYFO_LEESAH_TOPIC
import no.nav.helse.flex.narmesteleder.domain.NarmesteLederLeesah
import no.nav.helse.flex.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.readValue
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@Component
class OppdateringAvNarmesteLederEsyfo(
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional(rollbackFor = [Exception::class])
    fun behandleBatch(records: List<ConsumerRecord<String, String?>>) {
        // Fast låserekkefølge mellom partisjoner; sorteringen bevarer record-rekkefølgen innen hver partisjon.
        records.sortedBy { it.partition() }.forEach { record ->
            behandle(record.topic(), record.partition(), record.offset(), record.key(), record.value())
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
        val id =
            key?.let { Uuid.parseHexDashOrNull(it) }?.toJavaUuid()
                ?: throw IllegalArgumentException("Ugyldig nøkkel i nærmeste leder-record")
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
        return true
    }
}
