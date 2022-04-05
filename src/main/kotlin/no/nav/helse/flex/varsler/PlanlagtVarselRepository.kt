package no.nav.helse.flex.varsler

import no.nav.helse.flex.varsler.domain.PlanlagtVarsel
import no.nav.helse.flex.varsler.domain.PlanlagtVarselStatus
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PlanlagtVarselRepository : CrudRepository<PlanlagtVarsel, String> {
    fun findBySykepengesoknadId(sykepengesoknadId: String): List<PlanlagtVarsel>
    fun findFirst300ByStatusAndSendesIsBefore(status: PlanlagtVarselStatus, sendes: Instant): List<PlanlagtVarsel>
    fun findBySykepengesoknadIdAndStatus(sykepengesoknadId: String, status: PlanlagtVarselStatus): List<PlanlagtVarsel>

    @Query(
        """
        SELECT * 
        FROM planlagt_varsel 
        WHERE sykepengesoknad_id = :id 
          AND varsel_type = :type
          AND dine_sykmeldte_hendelse_opprettet IS NOT NULL
        """
    )
    fun findBySendtDineSykmeldte(id: String, type: String): List<PlanlagtVarsel>
}
