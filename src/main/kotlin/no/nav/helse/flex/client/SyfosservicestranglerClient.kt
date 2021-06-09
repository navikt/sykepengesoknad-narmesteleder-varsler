package no.nav.helse.flex.client

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Component
class SyfoservicestranglerClient(
    private val syfoservicestranglerRestTemplate: RestTemplate,
    @Value("\${flex.fss.proxy.url}") private val url: String
) {

    val log = logger()

    fun opprettOppgave(oppgave: OpprettHendelseRequest): OpprettHendelseResponse? {
        return try {
            val uriBuilder = UriComponentsBuilder.fromHttpUrl("$url/api/syfoservicestrangler/brukeroppgave/soknad")

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val result = syfoservicestranglerRestTemplate
                .exchange(
                    uriBuilder.toUriString(),
                    HttpMethod.POST,
                    HttpEntity<Any>(oppgave, headers),
                    OpprettHendelseResponse::class.java
                )

            if (result.statusCode != OK) {
                val message = "Kall mot syfoservicestrangler feiler med HTTP-" + result.statusCode
                log.error(message)
                throw RuntimeException(message)
            }
            log.info("Returner result ${result.body}")
            result.body
        } catch (ex: HttpClientErrorException.BadRequest) {
            throw OppgaveBleIkkeOpprettetException("Kunne ikke opprette oppgave for søknad med id: ${oppgave.soknadId}, $ex")
        }
    }
}

data class OpprettHendelseRequest(
    val soknadId: String,
    val aktorId: String?,
    val orgnummer: String?,
    val type: String
)

data class OpprettHendelseResponse(
    val melding: String,
)

class OppgaveBleIkkeOpprettetException(msg: String) : RuntimeException(msg)
