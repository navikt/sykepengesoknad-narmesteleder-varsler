package no.nav.helse.flex.narmesteleder

import no.nav.helse.flex.narmesteleder.domain.NarmesteLederEsyfo
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NarmesteLederEsyfoRepository : CrudRepository<NarmesteLederEsyfo, String> {
    fun findByNarmesteLederId(narmesteLederId: UUID): NarmesteLederEsyfo?

    @Query("SELECT * FROM narmeste_leder_esyfo WHERE bruker_fnr = :brukerFnr AND orgnummer = :orgnummer AND aktiv_tom IS NULL")
    fun findAktiveByBrukerFnrAndOrgnummer(
        brukerFnr: String,
        orgnummer: String,
    ): List<NarmesteLederEsyfo>
}
