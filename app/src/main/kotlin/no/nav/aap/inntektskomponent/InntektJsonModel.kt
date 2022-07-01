package no.nav.aap.inntektskomponent

import java.time.YearMonth

data class InntektskomponentResponse(
    val arbeidsInntektMaaned: List<Måned>
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
