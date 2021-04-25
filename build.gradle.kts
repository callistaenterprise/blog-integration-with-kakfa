plugins {
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("io.micronaut.application") version "1.4.2"
}

group = "se.martin"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("demo.*")
    }
}

dependencies {
    // Micronaut
    implementation("io.micronaut.kafka:micronaut-kafka")

    // Micronaut micrometer metrics
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")
    runtimeOnly("io.micronaut.micrometer:micronaut-micrometer-registry-prometheus")
    runtimeOnly("io.micronaut:micronaut-management")

    // Resilience4j
    implementation("io.github.resilience4j:resilience4j-retry:1.7.0")

    // Logback
    runtimeOnly("ch.qos.logback:logback-classic")
}

application {
    mainClass.set("se.martin.endlessloop.Application")
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}



