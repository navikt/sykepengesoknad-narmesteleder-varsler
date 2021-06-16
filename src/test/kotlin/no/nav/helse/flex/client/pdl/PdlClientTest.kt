package no.nav.helse.flex.client.pdl

import no.nav.helse.flex.AbstractContainerBaseTest
import no.nav.helse.flex.serialisertTilString
import no.nav.helse.flex.testUtils.getIdentResponse
import no.nav.helse.flex.testUtils.harBearerToken
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus.*
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.MockRestServiceServer.createServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestTemplate
import java.net.URI

@DirtiesContext
@EnableMockOAuth2Server
class PdlClientTest : AbstractContainerBaseTest() {

    @Autowired
    private lateinit var pdlClient: PdlClient

    private lateinit var pdlMockServer: MockRestServiceServer

    @Autowired
    private lateinit var pdlRestTemplate: RestTemplate

    private val fnr = "12345678901"
    private val aktorId = "aktorid123"

    @BeforeEach
    fun init() {
        pdlMockServer = createServer(pdlRestTemplate)
    }


    @Test
    fun `Vi tester happycase`() {

        val identResponse = getIdentResponse(
            listOf(
                PdlIdent(gruppe = AKTORID, ident = aktorId),
                PdlIdent(gruppe = FOLKEREGISTERIDENT, ident = fnr),
            )
        )

        pdlMockServer.expect(
            once(),
            requestTo(URI("https://pdl-api.dev-fss-pub.nais.io/graphql"))
        )
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("TEMA", "SYK"))
            .andExpect(harBearerToken())
            .andRespond(
                withStatus(OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(identResponse.serialisertTilString()
                    )
            )

        val responseData = pdlClient.hentIdenter(fnr)

        responseData `should be equal to` identResponse.data

        pdlMockServer.verify()
    }
}
