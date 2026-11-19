package no.nav.helse.flex.client.pdl

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

enum class IdentGruppe {
    AKTORID,
    FOLKEREGISTERIDENT,
    NPID,

    // En ny gruppe fra PDL skal ikke gi feil ved deserialisering og stoppe kjøringen.
    @JsonEnumDefaultValue
    UKJENT,
}

data class HentIdenterResponse(
    val data: HentIdenterResponseData?,
    val errors: List<ResponseError>?,
)

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?,
)

data class HentIdenterResponseData(
    val hentIdenter: HentIdenter? = null,
)

data class HentIdenter(
    val identer: List<PdlIdent>,
)

data class PdlIdent(
    val gruppe: IdentGruppe,
    val ident: String,
    val historisk: Boolean = false,
)

data class ErrorLocation(
    val line: String?,
    val column: String?,
)

data class ErrorExtension(
    val code: String?,
    val classification: String?,
)
