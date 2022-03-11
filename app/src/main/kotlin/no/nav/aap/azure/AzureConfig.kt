package no.nav.aap.azure

data class AzureConfig(
    val tokenEndpoint: String,
    val clientId: String,
    val clientSecret: String
)