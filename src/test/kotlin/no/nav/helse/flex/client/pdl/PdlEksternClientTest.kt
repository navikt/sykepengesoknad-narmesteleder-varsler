package no.nav.helse.flex.client.pdl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.pdl.IdentGruppe.AKTORID
import no.nav.helse.flex.client.pdl.IdentGruppe.FOLKEREGISTERIDENT
import no.nav.helse.flex.client.pdl.IdentGruppe.UKJENT
import no.nav.helse.flex.mockdispatcher.PdlMockDispatcher
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PdlEksternClientTest : FellesTestOppsett() {
    @Autowired
    lateinit var pdlEksternClient: PdlEksternClient

    @Test
    fun `Henter identer med historikk`() {
        val identerFraPdl =
            listOf(
                PdlIdent(FOLKEREGISTERIDENT, "12345678901", historisk = false),
                PdlIdent(FOLKEREGISTERIDENT, "10987654321", historisk = true),
                PdlIdent(AKTORID, "aktorid123", historisk = false),
            )
        PdlMockDispatcher.enqueueIdenter(identerFraPdl)

        pdlEksternClient.hentIdenterMedHistorikk("12345678901") shouldBe identerFraPdl

        val request = PdlMockDispatcher.requests().single()
        request.url.encodedPath shouldBe "/graphql"
        request.method shouldBe "POST"
        request.headers["Tema"] shouldBe "SYK"
        request.headers["Behandlingsnummer"] shouldBe "B139"
        request.headers["Authorization"]!! shouldStartWith "Bearer ey"
        request.body!!.utf8() shouldContain "historikk: true"
        request.body!!.utf8() shouldContain "\"ident\":\"12345678901\""
    }

    @Test
    fun `Tåler ukjent identgruppe fra PDL`() {
        PdlMockDispatcher.enqueueJson(
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
        PdlMockDispatcher.enqueueFeil("not_found")

        val feil = shouldThrow<PdlGraphQlFeil> { pdlEksternClient.hentIdenterMedHistorikk("12345678902") }

        feil.koder shouldBe listOf("not_found")
        PdlMockDispatcher.requests() shouldHaveSize 1
    }

    @Test
    fun `Kaster PdlGraphQlFeil ved unauthorized selv om status er 200`() {
        PdlMockDispatcher.enqueueFeil("unauthorized")

        val feil = shouldThrow<PdlGraphQlFeil> { pdlEksternClient.hentIdenterMedHistorikk("12345678903") }

        feil.koder shouldBe listOf("unauthorized")
    }

    @Test
    fun `Prøver på nytt ved teknisk feil, og feilmeldingen inneholder ikke body fra PDL`() {
        val bodyMedIdent = "Feil for ident 10987654321"
        repeat(4) { PdlMockDispatcher.enqueueHttpFeil(status = 500, body = bodyMedIdent) }

        val feil = shouldThrow<PdlTekniskFeil> { pdlEksternClient.hentIdenterMedHistorikk("12345678904") }

        feil.message shouldBe "PDL svarte med status 500"
        feil.message!! shouldNotContain bodyMedIdent
        PdlMockDispatcher.requests() shouldHaveSize 4
    }
}
