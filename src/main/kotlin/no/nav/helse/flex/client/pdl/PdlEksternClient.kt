package no.nav.helse.flex.client.pdl

import no.nav.helse.flex.objectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.readValue

private const val TEMA_HEADER = "Tema"
private const val TEMA_SYK = "SYK"
private const val BEHANDLINGSNUMMER_HEADER = "Behandlingsnummer"

// Se behandlinger: https://behandlingskatalog.ansatt.nav.no/process/purpose/SYKEPENGER
private const val BEHANDLINGSNUMMER = "B139"

@Component
class PdlEksternClient(
    @param:Value("\${PDL_BASE_URL}") private val pdlApiUrl: String,
    private val pdlRestTemplate: RestTemplate,
) : PdlClient {
    private val hentIdenterMedHistorikkQuery =
        """
        query HentIdenterMedHistorikk(${'$'}ident: ID!) {
          hentIdenter(ident: ${'$'}ident, historikk: true) {
            identer { ident, gruppe, historisk }
          }
        }
        """.trimIndent()

    @Retryable(excludes = [PdlGraphQlFeil::class])
    override fun hentIdenterMedHistorikk(ident: String): List<PdlIdent> {
        val request =
            GraphQLRequest(
                operationName = "HentIdenterMedHistorikk",
                query = hentIdenterMedHistorikkQuery,
                variables = mapOf("ident" to ident),
            )
        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set(TEMA_HEADER, TEMA_SYK)
                set(BEHANDLINGSNUMMER_HEADER, BEHANDLINGSNUMMER)
            }

        val responseBody =
            try {
                pdlRestTemplate
                    .exchange(
                        "$pdlApiUrl/graphql",
                        HttpMethod.POST,
                        HttpEntity(objectMapper.writeValueAsString(request), headers),
                        String::class.java,
                    ).body
            } catch (e: HttpStatusCodeException) {
                throw PdlTekniskFeil("PDL svarte med status ${e.statusCode.value()}")
            } catch (e: RestClientException) {
                throw PdlTekniskFeil("Kall mot PDL feilet")
            }

        val response =
            try {
                responseBody?.let { objectMapper.readValue<HentIdenterResponse>(it) }
            } catch (_: JacksonException) {
                throw PdlTekniskFeil("Kall mot PDL feilet")
            }
        val koder = response?.errors?.map { it.extensions?.code ?: "ukjent" } ?: emptyList()
        val hentIdenter = response?.data?.hentIdenter
        if (koder.isNotEmpty() || hentIdenter == null) {
            throw PdlGraphQlFeil(koder)
        }

        return hentIdenter.identer
    }

    private data class GraphQLRequest(
        val operationName: String,
        val query: String,
        val variables: Map<String, String>,
    )
}
