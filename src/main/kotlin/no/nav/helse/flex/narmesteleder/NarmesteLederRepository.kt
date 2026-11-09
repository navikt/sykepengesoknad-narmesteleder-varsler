package no.nav.helse.flex.narmesteleder

import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface NarmesteLederRepository : CrudRepository<NarmesteLeder, String> {
    fun findByNarmesteLederId(narmesteLederId: UUID): NarmesteLeder?

    fun findByBrukerFnrAndOrgnummer(
        brukerFnr: String,
        orgnummer: String,
    ): List<NarmesteLeder>

    @Query(
        """
        SELECT * FROM narmeste_leder
        WHERE bruker_fnr = :brukerFnr AND orgnummer = :orgnummer AND aktiv_tom IS NULL
        """,
    )
    fun findAktiveByBrukerFnrAndOrgnummer(
        brukerFnr: String,
        orgnummer: String,
    ): List<NarmesteLeder>
}
