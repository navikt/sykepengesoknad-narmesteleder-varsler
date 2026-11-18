package no.nav.helse.flex.client.pdl

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.pdl.IdentGruppe.AKTORID
import no.nav.helse.flex.client.pdl.IdentGruppe.FOLKEREGISTERIDENT
import no.nav.helse.flex.client.pdl.IdentGruppe.UKJENT
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PdlEksternClientTest : FellesTestOppsett() {
    @Autowired
    lateinit var pdlEksternClient: PdlEksternClient

    @Test
    fun `Henter identer med historikk`() {
        val ident = "12345678901"
        val responseBody =
            """
            {
              "data": {
                "hentIdenter": {
                  "identer": [
                    {"ident": "12345678901", "gruppe": "FOLKEREGISTERIDENT", "historisk": false},
                    {"ident": "10987654321", "gruppe": "FOLKEREGISTERIDENT", "historisk": true},
                    {"ident": "aktorid123", "gruppe": "AKTORID", "historisk": false}
                  ]
                }
              }
            }
            """.trimIndent()
        var path: String? = null
        var method: String? = null
        var tema: String? = null
        var behandlingsnummer: String? = null
        var authorization: String? = null
        var body: String? = null

        pdlMockWebServer.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                    path = request.url.encodedPath
                    method = request.method
                    tema = request.headers["Tema"]
                    behandlingsnummer = request.headers["Behandlingsnummer"]
                    authorization = request.headers["Authorization"]
                    body = request.body?.utf8()
                    return MockResponse.Builder().body(responseBody).build()
                }
            }

        val identer = pdlEksternClient.hentIdenterMedHistorikk(ident)

        identer shouldBe
            listOf(
                PdlIdent(FOLKEREGISTERIDENT, "12345678901", historisk = false),
                PdlIdent(FOLKEREGISTERIDENT, "10987654321", historisk = true),
                PdlIdent(AKTORID, "aktorid123", historisk = false),
            )
        path shouldBe "/graphql"
        method shouldBe "POST"
        tema shouldBe "SYK"
        behandlingsnummer shouldBe "B139"
        authorization.shouldNotBeNull().shouldStartWith("Bearer ey")
        body.shouldNotBeNull().also {
            it shouldContain "historikk: true"
            it shouldContain "\"ident\":\"$ident\""
        }
    }

    @Test
    fun `Tåler ukjent identgruppe fra PDL`() {
        val responseBody =
            """
            {"data":{"hentIdenter":{"identer":[
              {"ident":"12345678901","gruppe":"FOLKEREGISTERIDENT","historisk":false},
              {"ident":"ny-ident","gruppe":"NY_GRUPPE","historisk":false}
            ]}}}
            """.trimIndent()
        pdlMockWebServer.dispatcher = dispatcherWith(responseBody)

        pdlEksternClient.hentIdenterMedHistorikk("12345678901") shouldBe
            listOf(
                PdlIdent(FOLKEREGISTERIDENT, "12345678901", historisk = false),
                PdlIdent(UKJENT, "ny-ident", historisk = false),
            )
    }

    @Test
    fun `Kaster PdlGraphQlFeil med not_found`() {
        val responseBody =
            """{"data":{"hentIdenter":null},"errors":[{"message":"Fant ikke person","extensions":{"code":"not_found"}}]}"""
        pdlMockWebServer.dispatcher = dispatcherWith(responseBody)

        val feil =
            org.junit.jupiter.api.assertThrows<PdlGraphQlFeil> {
                pdlEksternClient.hentIdenterMedHistorikk("12345678902")
            }

        feil.koder shouldBe listOf("not_found")
    }

    @Test
    fun `Kaster PdlGraphQlFeil ved unauthorized selv om status er 200`() {
        val responseBody =
            """{"data":{"hentIdenter":null},"errors":[{"message":"Ingen tilgang","extensions":{"code":"unauthorized"}}]}"""
        pdlMockWebServer.dispatcher = dispatcherWith(responseBody)

        val feil =
            org.junit.jupiter.api.assertThrows<PdlGraphQlFeil> {
                pdlEksternClient.hentIdenterMedHistorikk("12345678903")
            }

        feil.koder shouldBe listOf("unauthorized")
    }

    @Test
    fun `Teknisk feil inneholder ikke body fra PDL`() {
        val identFraFeil = "10987654321"
        val responseBody = "Feil for ident $identFraFeil"
        pdlMockWebServer.dispatcher = dispatcherWith(responseBody, status = 500)

        val feil =
            org.junit.jupiter.api.assertThrows<PdlTekniskFeil> {
                pdlEksternClient.hentIdenterMedHistorikk("12345678904")
            }

        feil.message.orEmpty().shouldNotContain(identFraFeil)
        feil.message.orEmpty().shouldNotContain(responseBody)
    }

    private fun dispatcherWith(
        body: String,
        status: Int = 200,
    ) = object : Dispatcher() {
        override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse =
            MockResponse
                .Builder()
                .code(status)
                .body(body)
                .build()
    }
}
