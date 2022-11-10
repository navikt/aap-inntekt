val aapLibVersion = "3.5.28"
val ktorVersion = "2.1.3"

plugins {
    id("io.ktor.plugin")
}

application {
    mainClass.set("no.nav.aap.AppKt")
}

dependencies {
    implementation("com.github.navikt.aap-libs:ktor-utils:$aapLibVersion")
    implementation("com.github.navikt.aap-libs:ktor-auth-azuread:$aapLibVersion")
    implementation("com.github.navikt.aap-libs:kafka:$aapLibVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")

    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")

    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.10.0")

    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:1.4.4")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.2")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.4")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.github.navikt.aap-libs:kafka-test:$aapLibVersion")
}
