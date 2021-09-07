package no.nav.helse.flex.client.pdl

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.mockPdlResponse
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.client.ExpectedCount

class PdlClientTestClass : BaseTestClass() {

    @Autowired
    private lateinit var pdlClient: PdlClient

    @Test
    fun `Vi tester happycase`() {
        pdlMockServer!!.reset()
        val identResponse = getIdentResponse(
            listOf(
                PdlIdent(gruppe = AKTORID, ident = aktorId),
                PdlIdent(gruppe = FOLKEREGISTERIDENT, ident = fnr),
            )
        )

        mockPdlResponse(identResponse, ExpectedCount.once())

        val responseData = pdlClient.hentIdenter(fnr)

        responseData `should be equal to` identResponse.data

        pdlMockServer!!.verify()
        pdlMockServer!!.reset()
    }
}

fun getIdentResponse(identer: List<PdlIdent>): HentIdenterResponse {
    return HentIdenterResponse(
        errors = emptyList(),
        data = HentIdenterResponseData(
            hentIdenter = HentIdenter(
                identer
            )
        )
    )
}
