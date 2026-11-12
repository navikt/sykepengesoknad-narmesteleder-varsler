package no.nav.helse.flex.narmesteleder.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table

@Table("narmeste_leder_esyfo_offset")
data class NarmesteLederEsyfoOffset(
    @Id
    @Embedded.Nullable
    val id: NarmesteLederEsyfoOffsetId,
    val lastOffset: Long,
)

data class NarmesteLederEsyfoOffsetId(
    val topic: String,
    val partition: Int,
)
