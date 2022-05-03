package no.nav.aap.popp

data class PoppRequest(
    val fnr: String,
    val fomAr: Int,
    val tomAr: Int
)

data class PoppResponse(
    val inntekter: List<PoppInntekt>
)

data class PoppInntekt(
    val belop: Double,
    val inntektAr: Int,
    val inntektType: String
)