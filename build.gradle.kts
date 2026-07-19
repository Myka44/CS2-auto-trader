import org.gradle.kotlin.dsl.dependencies

plugins {
    id("java")
    id("org.openjfx.javafxplugin") version "0.1.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = "21.0.2"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    // HTTP client (existing)
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2")
    implementation("org.apache.httpcomponents.client5:httpclient5-fluent:5.2")

    // JSON parsing (existing)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // Cryptography library for Ed25519 signing (existing, DMarket auth)
    implementation("org.bouncycastle:bcprov-jdk15to18:1.76")

    // Logging (existing)
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Local database - plain JDBC against SQLite, no ORM
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")

    // Math library for statistics
    implementation("org.apache.commons:commons-math3:3.6.1")

    // JavaFX UI controls/validation
    implementation("org.controlsfx:controlsfx:11.2.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:unchecked")
    options.encoding = "UTF-8"
}

application {
    mainClass.set("org.example.App")
}

// Produces a single runnable "fat-ish" distribution via the application plugin's
// standard `installDist` / `distZip` tasks (JavaFX jars handled separately by the plugin).
tasks.named<JavaExec>("run") {
    // Allows the running app to write its DB/log files to a per-user folder.
    jvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "--module-path", "${classpath.asPath}",
        "--add-modules", "javafx.controls,javafx.fxml,javafx.graphics,javafx.base"
    )
}
