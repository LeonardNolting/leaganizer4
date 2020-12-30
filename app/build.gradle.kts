import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.4.21"
	id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "com.toleno"
version = "1.0-SNAPSHOT"

val exposedVersion = "0.28.1"
val ktorVersion = "1.4.0"

dependencies {
	implementation(project(":organization"))
	testImplementation(kotlin("test-junit5"))
	testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")

	implementation(kotlin("reflect"))

//    https://stackoverflow.com/a/61285361
//    implementation("org.reflections:reflections:0.9.12")
	implementation("net.oneandone.reflections8:reflections8:0.11.7")

	implementation("org.slf4j", "slf4j-api", "1.7.30")
	implementation("org.slf4j", "slf4j-simple", "1.7.30")

	implementation("com.jessecorbett", "diskord", "1.8.1")
	implementation("io.github.cdimascio", "dotenv-kotlin", "6.2.1")

	implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
	implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
	implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
	implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

	implementation("org.postgresql:postgresql:42.2.18")

	implementation("io.ktor:ktor-client-core:$ktorVersion")
	implementation("io.ktor:ktor-client-cio:$ktorVersion")
	implementation("io.ktor:ktor-client-gson:$ktorVersion")

	implementation("org.apache.xmlgraphics", "batik-transcoder", "1.7")
	implementation("org.apache.xmlgraphics", "batik-codec", "1.7")
	implementation("org.apache.xmlgraphics", "xmlgraphics-commons", "2.1")
}

tasks.test {
	useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
//    Java version (required by dotenv)
	kotlinOptions.jvmTarget = "14"
	kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.ExperimentalStdlibApi"
}
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
	archiveFileName.set("leaganizer2.jar")
	manifest.attributes["Main-Class"] = "MainKt"
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
	languageVersion = "1.4"
	freeCompilerArgs = listOf("-Xinline-classes", "-Xopt-in=kotlin.ExperimentalStdlibApi")
}

tasks.register("stage") {
	dependsOn("clean")
	dependsOn("shadowJar")
}