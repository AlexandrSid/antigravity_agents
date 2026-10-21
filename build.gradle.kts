plugins {
    java
    id("io.quarkus")
}

repositories {
    mavenCentral()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))

    // Quarkus core extensions
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-flyway")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-redis-client")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-logging-json")

    // Key generation
    implementation("org.sqids:sqids:0.1.0")

    // Unit / Slice testing
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-jdbc-h2")

    // Integration testing
    testImplementation("org.testcontainers:postgresql:1.20.1")
    testImplementation("org.testcontainers:junit-jupiter:1.20.1")
}

group = "com.linkshortener"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

// Dedicated integrationTest task to support PROJECT_ENV.md command
val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests with Testcontainers and external containers."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/*IntegrationTest.class", "**/*IT.class")
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    mustRunAfter(tasks.named("test"))
}

// Anti-Stub Quality Gate Task mandated by .ai/guidelines/testing/slots/java.md
tasks.register("checkNoStubs") {
    group = "verification"
    description = "Fails the build if any Not-Implemented stubs remain in src/main."
    doLast {
        if (project.hasProperty("allowStubs")) {
            logger.lifecycle("checkNoStubs skipped (-PallowStubs is active).")
            return@doLast
        }
        val stubPatterns = listOf("UnsupportedOperationException", "Not implemented")
        val mainSrc = fileTree("src/main") { include("**/*.java", "**/*.kt") }
        val violations = mainSrc.files.flatMap { file ->
            file.readLines().mapIndexedNotNull { idx, line ->
                stubPatterns.firstOrNull { line.contains(it) }
                    ?.let { "${file.relativeTo(projectDir)}:${idx + 1} -> stub: $it" }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("checkNoStubs FAILED:\n" + violations.joinToString("\n"))
        }
        logger.lifecycle("checkNoStubs passed — zero stub violations in src/main.")
    }
}

