package no.nav.helse.flex.narmesteleder

import no.nav.helse.flex.narmesteleder.domain.NarmesteLeder
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface NarmesteLederRepository : CrudRepository<NarmesteLeder, String> {
    fun findByNarmesteLederId(narmesteLederId: UUID): NarmesteLeder?
}
