package no.nav.helse.flex.narmesteleder

import no.nav.helse.flex.narmesteleder.domain.NarmesteLederEsyfo
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NarmesteLederEsyfoRepository : CrudRepository<NarmesteLederEsyfo, String> {
    fun findByNarmesteLederId(narmesteLederId: UUID): NarmesteLederEsyfo?
}
