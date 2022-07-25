import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "by.enrollie"
version = "0.1.0"

repositories {
    mavenCentral()
    maven {
        url = uri("https://packages.neitex.me/releases")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly("by.enrollie:eversity-shared-api:0.1.1-alpha.9")
    implementation("org.jetbrains.xodus:dnq:2.0.0")
    implementation("org.jetbrains.xodus:xodus-entity-store:2.0.1")
    implementation("org.jetbrains.xodus:xodus-query:2.0.1")
    implementation("org.jetbrains.xodus:xodus-utils:2.0.1")
    implementation("org.jetbrains.xodus:xodus-environment:2.0.1")
}

tasks.named<ShadowJar>("shadowJar") {
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-common"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
        exclude(dependency("org.slf4j:slf4j-api"))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("cleanResources"){
    delete("$buildDir/resources")
    this.didWork = true
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.dependsOn.add((tasks.getByName("processResources") as ProcessResources).apply {
    dependsOn("cleanResources")
    filesMatching("metadata.properties") {
        val props = mutableMapOf<String, String>()
        props["version"] = project.version.toString()
        props["apiVersion"] =
            configurations.getByName("compileOnly").allDependencies.first { it.name == "eversity-shared-api" }.version.toString()
        expand(props)
    }
})

