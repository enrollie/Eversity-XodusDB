import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "by.enrollie"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://packages.neitex.me/releases")
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
    compileOnly("by.enrollie:eversity-shared-api:0.1.1-alpha.6")
    implementation("org.jetbrains.xodus:dnq:2.0.0") {
        exclude("org.slf4j")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
        exclude("org.jetbrains.kotlin", "kotlin-reflect")
    }
    implementation("org.jetbrains.xodus:xodus-entity-store:2.0.1") {
        exclude("org.slf4j")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
        exclude("org.jetbrains.kotlin", "kotlin-reflect")
    }
    implementation("org.jetbrains.xodus:xodus-query:2.0.1") {
        exclude("org.slf4j")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
        exclude("org.jetbrains.kotlin", "kotlin-reflect")
    }
    implementation("org.jetbrains.xodus:xodus-utils:2.0.1") {
        exclude("org.slf4j")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
        exclude("org.jetbrains.kotlin", "kotlin-reflect")
    }
    implementation("org.jetbrains.xodus:xodus-environment:2.0.1") {
        exclude("org.slf4j")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
        exclude("org.jetbrains.kotlin", "kotlin-reflect")
    }
//    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.dependsOn.add((tasks.getByName("processResources") as ProcessResources).apply {
    filesMatching("metadata.properties") {
        val props = mutableMapOf<String, String>()
        props["version"] = project.version.toString()
        expand(props)
    }
})

