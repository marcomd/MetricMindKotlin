import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.metricmind"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin Standard Library
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Database - Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.44.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.44.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.44.1")

    // PostgreSQL Driver
    implementation("org.postgresql:postgresql:42.7.1")

    // Connection Pooling
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Database Migrations
    implementation("org.flywaydb:flyway-core:10.4.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.4.1")

    // CLI Framework
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")

    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // HTTP Client (Ktor) for AI API integration
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")

    // Configuration
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Testing
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-property:5.8.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
}

application {
    mainClass.set("com.metricmind.MainKt")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("metricmind")
    archiveClassifier.set("all")
    archiveVersion.set(version.toString())

    manifest {
        attributes["Main-Class"] = "com.metricmind.MainKt"
    }

    // Merge service files
    mergeServiceFiles()
}

tasks.register<JavaExec>("run-extract") {
    group = "application"
    description = "Run extract command"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.metricmind.MainKt")
    args = listOf("extract", "--help")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
