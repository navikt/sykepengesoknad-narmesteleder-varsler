package no.nav.helse.flex.varsler

import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PlanlagtVarselRepository : CrudRepository<PlanlagtVarsel, String> {
    fun findBySykepengesoknadId(sykepengesoknadId: String): List<PlanlagtVarsel>
}
