plugins {
    id("com.github.johnrengelman.shadow")
    application
}

application {
    mainClass.set("no.nav.aap.AppKt")
}

dependencies {
    implementation("com.github.navikt.aap-libs:ktor-utils:0.0.40")
    implementation("com.github.navikt.aap-libs:ktor-client-auth:0.0.40")
    implementation("com.github.navikt.aap-libs:kafka:0.0.40")
    testImplementation("com.github.navikt.aap-libs:kafka-test:0.0.40")

    implementation("io.ktor:ktor-server-core:2.0.0")
    implementation("io.ktor:ktor-server-netty:2.0.0")
    implementation("io.ktor:ktor-client-jackson:2.0.0")

    implementation("io.ktor:ktor-client-cio:2.0.0")
    implementation("io.ktor:ktor-client-logging-jvm:2.0.0")

    implementation("io.ktor:ktor-auth:1.6.8")
    implementation("io.ktor:ktor-client-auth:2.0.0")

    implementation("io.ktor:ktor-server-metrics-micrometer:2.0.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.8.5")

    implementation("io.ktor:ktor-client-content-negotiation:2.0.0")
    implementation("io.ktor:ktor-serialization-jackson:2.0.0")


    implementation("com.sksamuel.hoplite:hoplite-yaml:2.1.2")

    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("io.ktor:ktor-jackson:1.6.8")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.1.1")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.2")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:2.0.0")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.0.1")
}
