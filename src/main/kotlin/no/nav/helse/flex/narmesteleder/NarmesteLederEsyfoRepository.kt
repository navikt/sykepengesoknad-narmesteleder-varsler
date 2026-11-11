package no.nav.helse.flex.narmesteleder

import no.nav.helse.flex.narmesteleder.domain.NarmesteLederEsyfo
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NarmesteLederEsyfoRepository : CrudRepository<NarmesteLederEsyfo, String> {
    @Modifying
    @Query(
        """
        INSERT INTO narmeste_leder_esyfo
            (oppdatert, narmeste_leder_id, orgnummer, bruker_fnr, narmeste_leder_fnr,
             narmeste_leder_telefonnummer, narmeste_leder_epost, aktiv_fom, aktiv_tom,
             arbeidsgiver_forskutterer, timestamp)
        VALUES (
            now(), :#{#leder.narmesteLederId}, :#{#leder.orgnummer}, :#{#leder.brukerFnr}, :#{#leder.narmesteLederFnr},
            :#{#leder.narmesteLederTelefonnummer}, :#{#leder.narmesteLederEpost}, :#{#leder.aktivFom}, :#{#leder.aktivTom},
            :#{#leder.arbeidsgiverForskutterer}, :#{T(java.sql.Timestamp).from(#leder.timestamp)}
        )
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
        """,
    )
    fun lagre(leder: NarmesteLederEsyfo): Int

    @Modifying
    @Query("DELETE FROM narmeste_leder_esyfo WHERE narmeste_leder_id = :narmesteLederId")
    fun slett(narmesteLederId: UUID): Int

    fun findByNarmesteLederId(narmesteLederId: UUID): NarmesteLederEsyfo?
}
