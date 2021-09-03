package no.nav.helse.flex.db

import no.nav.helse.flex.domain.NarmesteLeder
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface NarmesteLederRepository : CrudRepository<NarmesteLeder, String> {
    fun findByNarmesteLederId(narmesteLederId: UUID): NarmesteLeder?
}
