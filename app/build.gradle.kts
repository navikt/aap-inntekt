import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow")
    application
}

dependencies {

    implementation("io.micrometer:micrometer-registry-prometheus:1.8.5")

    implementation("com.sksamuel.hoplite:hoplite-yaml:2.1.2")

    // serialiserig til/fra json på kafka

    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("io.ktor:ktor-server-core:2.0.0")
    implementation("io.ktor:ktor-server-netty:2.0.0")
    implementation("io.ktor:ktor-client-jackson:2.0.0")
    implementation("io.ktor:ktor-client-core:2.0.0")
    implementation("io.ktor:ktor-client-cio:2.0.0")
    implementation("io.ktor:ktor-client-logging:2.0.0")
    implementation("io.ktor:ktor-server-auth:2.0.0")
    implementation("io.ktor:ktor-client-auth:2.0.0")
    implementation("io.ktor:ktor-client-content-negotiation:2.0.0")
    implementation("io.ktor:ktor-serialization-jackson:2.0.0")
    implementation("io.ktor:ktor-server-metrics-micrometer:2.0.0")
    testImplementation("io.ktor:ktor-server-test-host:2.0.0")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.1.1")

    implementation("org.apache.kafka:kafka-streams:3.1.0")
    implementation("org.apache.kafka:kafka-clients:3.1.0")
    implementation("io.confluent:kafka-streams-avro-serde:7.0.1") {
        exclude("org.apache.kafka", "kafka-clients")
    }

    implementation("no.nav.aap.avro:inntekter:0.0.11")

    // JsonSerializer java 8 LocalDate
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.2")

    testImplementation(kotlin("test"))
    // used to override env var runtime
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.0.1")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "18"
    }

    withType<Test> {
        useJUnitPlatform()
    }
}

application {
    // Define the main class for the application.
    mainClass.set("no.nav.aap.AppKt")
}
