package no.nav.helse.flex.varsler

import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface PlanlagtVarselRepository : CrudRepository<PlanlagtVarsel, String> {
    fun findBySykepengesoknadId(sykepengesoknadId: String): List<PlanlagtVarsel>
    fun findByStatusAndSendesIsBefore(status: PlanlagtVarselStatus, sendes: OffsetDateTime): List<PlanlagtVarsel>
}
