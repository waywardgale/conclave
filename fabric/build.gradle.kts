import java.security.MessageDigest
import java.util.HexFormat

plugins {
    kotlin("jvm")
    id("net.fabricmc.fabric-loom")
}

repositories { mavenCentral() }

loom {
    splitEnvironmentSourceSets()
    mods {
        create("conclave") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

fabricApi {
    configureTests {
        createSourceSet.set(true)
        modId.set("conclave_test")
        enableGameTests.set(true)
        enableClientGameTests.set(true)
        clearRunDirectory.set(true)
        eula.set(false)
    }
}

kotlin.target.compilations.named("gametest") {
    associateWith(kotlin.target.compilations.getByName("main"))
    associateWith(kotlin.target.compilations.getByName("client"))
}

// The server runner otherwise reuses its generated world across development storage formats.
val resetServerTestWorld = tasks.register<Delete>("resetServerTestWorld") {
    delete(layout.buildDirectory.dir("run/gameTest/world"))
}
tasks.named("runGameTest") { dependsOn(resetServerTestWorld) }

dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.2")
    implementation("net.fabricmc:fabric-language-kotlin:1.14.1+kotlin.2.4.20")
    implementation(project(":core"))
    include(project(":core"))
    implementation(project(":storage"))
    include(project(":storage"))
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    include("org.xerial:sqlite-jdbc:3.53.4.0")
    include("org.snakeyaml:snakeyaml-engine:3.1.1")
    implementation("tools.aqua:z3-turnkey:4.14.1")
    include("tools.aqua:z3-turnkey:4.14.1")
    include("tools.aqua:turnkey-support:1.0.0")
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { jvmToolchain(25) }
java { withSourcesJar() }
tasks.test { useJUnitPlatform() }

abstract class GenerateBuildIdentity : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection
    @get:Internal abstract val checkoutDirectory: DirectoryProperty
    @get:Input abstract val releaseVersion: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        val checkoutPath = checkoutDirectory.get().asFile.toPath()
        val digest = MessageDigest.getInstance("SHA-256")
        sourceFiles.files.sortedBy { checkoutPath.relativize(it.toPath()).toString().replace('\\', '/') }.forEach { input ->
            val relative = checkoutPath.relativize(input.toPath()).toString().replace('\\', '/')
            digest.update(relative.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(MessageDigest.getInstance("SHA-256").digest(input.readBytes()))
        }
        val fingerprint = HexFormat.of().formatHex(digest.digest())
        val output = outputDirectory.get().file("conclave-build.properties").asFile
        output.parentFile.mkdirs()
        output.writeText("version=${releaseVersion.get()}\nfingerprint=$fingerprint\n")
    }
}

// Hash code, resources and pinned build configuration, independently of checkout path or Git state.
val generateBuildIdentity = tasks.register<GenerateBuildIdentity>("generateBuildIdentity") {
    sourceFiles.from(
        rootProject.fileTree("core/src/main"), rootProject.fileTree("storage/src/main"), fileTree("src/main"), fileTree("src/client"),
        rootProject.file("build.gradle.kts"), rootProject.file("settings.gradle.kts"),
        rootProject.file("gradle.properties"), rootProject.file("gradle/wrapper/gradle-wrapper.properties"),
        rootProject.file("core/build.gradle.kts"), rootProject.file("storage/build.gradle.kts"), file("build.gradle.kts"),
    )
    checkoutDirectory.set(rootProject.layout.projectDirectory)
    releaseVersion.set(project.version.toString())
    outputDirectory.set(layout.buildDirectory.dir("generated/build-identity"))
}

tasks.processResources {
    val modVersion = project.version.toString()
    from(generateBuildIdentity.flatMap { it.outputDirectory })
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") { expand("version" to modVersion) }
}

tasks.jar {
    archiveBaseName.set("conclave")
    from(rootProject.file("LICENSE")) { rename { "LICENSE_conclave" } }
}
