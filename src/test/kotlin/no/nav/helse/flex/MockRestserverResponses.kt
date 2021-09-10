package no.nav.helse.flex

import no.nav.helse.flex.client.pdl.*
import no.nav.helse.flex.client.syfoservicestrangler.OpprettHendelseResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequest
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.RequestMatcher
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators
import java.net.URI

fun Testoppsett.mockPdlResponse(
    identResponse: HentIdenterResponse = getIdentResponse(
        listOf(
            PdlIdent(gruppe = AKTORID, ident = aktorId),
            PdlIdent(gruppe = FOLKEREGISTERIDENT, ident = fnr),
        )
    ),
    expectedCount: ExpectedCount = ExpectedCount.once()
) {
    pdlMockServer!!.expect(
        expectedCount,
        requestTo(URI("https://pdl-api.dev-fss-pub.nais.io/graphql"))
    )
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("TEMA", "SYK"))
        .andExpect(harBearerToken())
        .andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    identResponse.serialisertTilString()
                )
        )
}

fun Testoppsett.mockSyfoserviceStranglerBrukeroppgavePost(
    response: OpprettHendelseResponse = OpprettHendelseResponse(melding = "Mocket kall"),
    expectedCount: ExpectedCount = ExpectedCount.once()
) {
    syfoServiceStanglerMockServer!!.expect(
        expectedCount,
        requestTo(URI("http://flex-fss-proxy/api/syfoservicestrangler/brukeroppgave/soknad"))
    )
        .andExpect(method(HttpMethod.POST))
        .andExpect(harBearerToken())
        .andRespond(
            MockRestResponseCreators.withStatus(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    response.serialisertTilString()
                )
        )
}

fun harBearerToken(): RequestMatcher {
    return RequestMatcher { request: ClientHttpRequest ->

        val authHeader = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?: throw AssertionError("Mangler ${HttpHeaders.AUTHORIZATION} header")

        if (!authHeader.startsWith("Bearer ey")) {
            throw AssertionError("${HttpHeaders.AUTHORIZATION} ser ikke ut til å være bearertoken")
        }
    }
}
