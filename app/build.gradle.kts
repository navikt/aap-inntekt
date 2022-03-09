import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow")
    application
}

dependencies {
    implementation("io.ktor:ktor-server-core:1.6.7")
    implementation("io.ktor:ktor-server-netty:1.6.7")
    implementation("io.ktor:ktor-client-jackson:1.6.7")

    implementation("io.ktor:ktor-client-core:1.6.7")
    implementation("io.ktor:ktor-client-cio:1.6.7")

    implementation("io.ktor:ktor-auth:1.6.7")
    implementation("io.ktor:ktor-client-auth:1.6.7")

    implementation("io.ktor:ktor-metrics-micrometer:1.6.7")
    implementation("io.micrometer:micrometer-registry-prometheus:1.8.3")

    implementation("com.sksamuel.hoplite:hoplite-yaml:1.4.16")

    // serialiserig til/fra json på kafka
    implementation("io.ktor:ktor-serialization:1.6.7")

    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("io.ktor:ktor-jackson:1.6.7")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:7.0.1")

    implementation("org.apache.kafka:kafka-streams:3.1.0")
    implementation("org.apache.kafka:kafka-clients:3.1.0")
    implementation("io.confluent:kafka-streams-avro-serde:7.0.1") {
        exclude("org.apache.kafka", "kafka-clients")
    }

    implementation("no.nav.aap.avro:inntekter:0.0.11")

    // JsonSerializer java 8 LocalDate
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.1")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:1.6.7")
    // used to override env var runtime
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.0.1")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    withType<Test> {
        useJUnitPlatform()
    }
}

application {
    // Define the main class for the application.
    mainClass.set("no.nav.aap.AppKt")
}
