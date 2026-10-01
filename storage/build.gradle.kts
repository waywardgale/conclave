plugins { kotlin("jvm") }
repositories { mavenCentral() }
kotlin { jvmToolchain(25) }
java { withSourcesJar() }
dependencies {
    implementation(project(":core"))
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
