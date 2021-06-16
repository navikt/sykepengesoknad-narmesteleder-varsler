package no.nav.helse.flex.testUtils

import no.nav.helse.flex.Application
import no.nav.helse.flex.client.OpprettHendelseResponse
import no.nav.helse.flex.client.pdl.*
import no.nav.helse.flex.objectMapper
import no.nav.syfo.kafka.felles.SykepengesoknadDTO
import org.springframework.http.HttpHeaders
import org.springframework.http.client.ClientHttpRequest
import org.springframework.test.web.client.RequestMatcher

val mockSykepengesoknadDTO: SykepengesoknadDTO =
    objectMapper.readValue(
        Application::class.java.getResource("/arbeidstakersoknad.json"),
        SykepengesoknadDTO::class.java
    )

fun harBearerToken(): RequestMatcher {
    return RequestMatcher { request: ClientHttpRequest ->

        val authHeader = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?: throw AssertionError("Mangler ${HttpHeaders.AUTHORIZATION} header")

        if (!authHeader.startsWith("Bearer ey")) {
            throw AssertionError("${HttpHeaders.AUTHORIZATION} ser ikke ut til å være bearertoken")
        }
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

fun getOpprettHendelseResponse(): OpprettHendelseResponse {
    return OpprettHendelseResponse(
        melding = "Opprettet bruker oppgave"
    )
}

