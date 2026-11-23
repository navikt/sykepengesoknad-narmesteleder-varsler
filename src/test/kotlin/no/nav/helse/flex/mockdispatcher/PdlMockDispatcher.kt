package no.nav.helse.flex.mockdispatcher

import mockwebserver3.MockResponse
import no.nav.helse.flex.client.pdl.ErrorExtension
import no.nav.helse.flex.client.pdl.HentIdenter
import no.nav.helse.flex.client.pdl.HentIdenterResponse
import no.nav.helse.flex.client.pdl.HentIdenterResponseData
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.client.pdl.ResponseError

object PdlMockDispatcher : FellesQueueDispatcher<HentIdenterResponse>(
    defaultFactory = { MockResponse(code = 500, body = "Ingen respons lagt i kø for PDL") },
) {
    fun enqueueIdenter(identer: List<PdlIdent>) {
        enqueue(
            HentIdenterResponse(
                data = HentIdenterResponseData(hentIdenter = HentIdenter(identer)),
                errors = null,
            ),
        )
    }

    fun enqueueFeil(kode: String) {
        enqueue(
            HentIdenterResponse(
                data = HentIdenterResponseData(hentIdenter = null),
                errors =
                    listOf(
                        ResponseError(
                            message = "Feil fra PDL",
                            locations = null,
                            path = null,
                            extensions = ErrorExtension(code = kode, classification = null),
                        ),
                    ),
            ),
        )
    }

    fun enqueueHttpFeil(
        status: Int,
        body: String = "",
    ) {
        enqueueResponse(MockResponse(code = status, body = body))
    }

    fun enqueueJson(json: String) {
        enqueueResponse(withContentTypeApplicationJson { MockResponse(body = json) })
    }
}
