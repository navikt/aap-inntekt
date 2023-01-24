package no.nav.inntekt.inntektskomponent

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
