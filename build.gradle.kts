plugins {
    base
    kotlin("jvm") version "2.4.20" apply false
    id("net.fabricmc.fabric-loom") version "1.18.2" apply false
}

allprojects {
    group = rootProject.group
    version = rootProject.version
}

tasks.named("check") { dependsOn(":core:check", ":storage:check", ":fabric:check") }
tasks.named("assemble") { dependsOn(":core:assemble", ":storage:assemble", ":fabric:assemble") }
