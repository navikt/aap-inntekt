package inntekt.inntektskomponent

import java.time.YearMonth

data class InntektskomponentResponse(
    val arbeidsInntektMaaned: List<Måned> = emptyList()
)

data class Måned(
    val aarMaaned: YearMonth,
    val arbeidsInntektInformasjon: ArbeidsInntektInformasjon
)

data class ArbeidsInntektInformasjon(
    val inntektListe: List<Inntekt>
)

data class Inntekt(
    val beloep: Double,
)

data class InntektskomponentRequest(
    val fnr: String,
    val fom: YearMonth,
    val tom: YearMonth,
    val filter: String,
    val callId: String
) {
    fun hentinntektliste() = mapOf(
        "ident" to mapOf(
            "identifikator" to fnr,
            "aktoerType" to "NATURLIG_IDENT"
        ),
        "ainntektsfilter" to filter,
        "formaal" to "Arbeidsavklaringspenger",
        "maanedFom" to fom,
        "maanedTom" to tom
    )
}
