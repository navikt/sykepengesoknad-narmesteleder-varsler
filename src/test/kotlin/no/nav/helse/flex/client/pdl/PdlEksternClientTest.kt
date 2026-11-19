package no.nav.helse.flex.client.pdl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.pdl.IdentGruppe.AKTORID
import no.nav.helse.flex.client.pdl.IdentGruppe.FOLKEREGISTERIDENT
import no.nav.helse.flex.client.pdl.IdentGruppe.UKJENT
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CopyOnWriteArrayList

class PdlEksternClientTest : FellesTestOppsett() {
    @Autowired
    lateinit var pdlEksternClient: PdlEksternClient

    @Test
    fun `Henter identer med historikk`() {
        val requests =
            svarMed(
                """
                {"data":{"hentIdenter":{"identer":[
                  {"ident":"12345678901","gruppe":"FOLKEREGISTERIDENT","historisk":false},
                  {"ident":"10987654321","gruppe":"FOLKEREGISTERIDENT","historisk":true},
                  {"ident":"aktorid123","gruppe":"AKTORID","historisk":false}
                ]}}}
                """.trimIndent(),
            )

        pdlEksternClient.hentIdenterMedHistorikk("12345678901") shouldBe
            listOf(
                PdlIdent(FOLKEREGISTERIDENT, "12345678901", historisk = false),
                PdlIdent(FOLKEREGISTERIDENT, "10987654321", historisk = true),
                PdlIdent(AKTORID, "aktorid123", historisk = false),
            )

        val request = requests.single()
        request.url.encodedPath shouldBe "/graphql"
        request.method shouldBe "POST"
        request.headers["Tema"] shouldBe "SYK"
        request.headers["Behandlingsnummer"] shouldBe "B139"
        request.headers["Authorization"]!! shouldStartWith "Bearer ey"
        request.body!!.utf8().also {
            it shouldContain "historikk: true"
            it shouldContain "\"ident\":\"12345678901\""
        }
    }

    @Test
    fun `Tåler ukjent identgruppe fra PDL`() {
        svarMed(
            """
            {"data":{"hentIdenter":{"identer":[
              {"ident":"12345678901","gruppe":"FOLKEREGISTERIDENT","historisk":false},
              {"ident":"ny-ident","gruppe":"NY_GRUPPE","historisk":false}
            ]}}}
            """.trimIndent(),
        )

        pdlEksternClient.hentIdenterMedHistorikk("12345678901") shouldBe
            listOf(
                PdlIdent(FOLKEREGISTERIDENT, "12345678901", historisk = false),
                PdlIdent(UKJENT, "ny-ident", historisk = false),
            )
    }

    @Test
    fun `Kaster PdlGraphQlFeil med not_found uten å prøve på nytt`() {
        val requests =
            svarMed(
                """{"data":{"hentIdenter":null},"errors":[{"message":"Fant ikke person","extensions":{"code":"not_found"}}]}""",
            )

        val feil = shouldThrow<PdlGraphQlFeil> { pdlEksternClient.hentIdenterMedHistorikk("12345678902") }

        feil.koder shouldBe listOf("not_found")
        requests shouldHaveSize 1
    }

    @Test
    fun `Kaster PdlGraphQlFeil ved unauthorized selv om status er 200`() {
        svarMed(
            """{"data":{"hentIdenter":null},"errors":[{"message":"Ingen tilgang","extensions":{"code":"unauthorized"}}]}""",
        )

        val feil = shouldThrow<PdlGraphQlFeil> { pdlEksternClient.hentIdenterMedHistorikk("12345678903") }

        feil.koder shouldBe listOf("unauthorized")
    }

    @Test
    fun `Prøver på nytt ved teknisk feil, og feilmeldingen inneholder ikke body fra PDL`() {
        val responseBody = "Feil for ident 10987654321"
        val requests = svarMed(responseBody, status = 500)

        val feil = shouldThrow<PdlTekniskFeil> { pdlEksternClient.hentIdenterMedHistorikk("12345678904") }

        feil.message shouldBe "PDL svarte med status 500"
        feil.message!! shouldNotContain responseBody
        requests shouldHaveSize 4
    }

    private fun svarMed(
        body: String,
        status: Int = 200,
    ): List<RecordedRequest> {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        pdlMockWebServer.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests.add(request)
                    return MockResponse
                        .Builder()
                        .code(status)
                        .body(body)
                        .build()
                }
            }
        return requests
    }
}
