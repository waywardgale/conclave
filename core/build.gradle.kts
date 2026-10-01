plugins { kotlin("jvm") }

repositories { mavenCentral() }

kotlin { jvmToolchain(25) }
java { withSourcesJar() }

dependencies {
    implementation("org.snakeyaml:snakeyaml-engine:3.1.1")
    implementation("tools.aqua:z3-turnkey:4.14.1")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
