import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("fabric-loom") version "1.15-SNAPSHOT" apply false
    id("net.fabricmc.fabric-loom") version "1.15-SNAPSHOT" apply false
}

val targetVersion = findProperty("target_version") as String? ?: "26_1_2"
// 26.1+ shipped unobfuscated - Yarn/Intermediary were discontinued for it, and the new plugin id's
// no-remap workflow is what's proven to actually work for it (the classic plugin's officialMojangMappings()
// lookup doesn't yet handle 26.1's new version-string format). Everything before that still needs Yarn
// remapping via the classic plugin. Since Gradle can't pick between two different plugin ids inside a
// static plugins{} block, both are declared with apply false above and the right one is applied here.
// Because the applied plugin is chosen dynamically rather than declared statically, Kotlin's typed DSL
// shorthands (base { }, java { }, sourceSets { }) aren't available below - extensions are configured by
// type instead (extensions.configure<T>), which works regardless of how the plugin was applied.
val isUnobfuscated = targetVersion.startsWith("26_") || targetVersion.startsWith("27_")

if (isUnobfuscated) {
    apply(plugin = "net.fabricmc.fabric-loom")
} else {
    apply(plugin = "fabric-loom")
}
apply(plugin = "java") // ensures base/java extensions exist regardless of what the loom variant applies transitively

val minecraftVersion = properties["minecraft_version_$targetVersion"] as String
val loaderVersion = properties["loader_version_$targetVersion"] as String
// Baritone's Meteor fork is versioned by the MC minor release (e.g. "26.1") for unobfuscated targets,
// but by the full patch version (e.g. "1.21.8") for Yarn-mapped ones.
val baritoneVersion = if (isUnobfuscated) minecraftVersion.substringBeforeLast(".") else minecraftVersion

val archivesNameValue = "${properties["archives_base_name"] as String} ($minecraftVersion)"
extensions.configure<BasePluginExtension> {
    archivesName.set(archivesNameValue)
}
version = properties["mod_version"] as String
group = properties["maven_group"] as String

// src/common/java: files with no Minecraft-version-specific code (shared across every target).
// src/main/java: Mojang-mapped modules - only used for unobfuscated (26.1+) targets.
// src/legacy/java: Yarn-mapped modules - used for every obfuscated (pre-26.1) target.
// Only BetterBaritoneBuild is ported for any target; the addon's other modules/commands/HUDs are
// excluded (not deleted) from the modern source set, and were never added to the legacy one.
extensions.configure<SourceSetContainer> {
    named("main") {
        java {
            setSrcDirs(listOf("src/common/java", if (isUnobfuscated) "src/main/java" else "src/legacy/java"))

            if (isUnobfuscated) {
                exclude("xyz/omegaware/addon/commands/**")
                exclude("xyz/omegaware/addon/hud/**")
                exclude("xyz/omegaware/addon/modules/BeaconRangeModule.java")
                exclude("xyz/omegaware/addon/modules/BetterStashFinderModule.java")
                exclude("xyz/omegaware/addon/modules/ChatFilterModule.java")
                exclude("xyz/omegaware/addon/modules/ItemFrameDupeModule.java")
                exclude("xyz/omegaware/addon/modules/TPAAutomationModule.java")
                exclude("xyz/omegaware/addon/modules/TSRKitBotModule.java")
                exclude("xyz/omegaware/addon/utils/ServerCheck.java")
            }
        }
    }
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    add("minecraft", "com.mojang:minecraft:$minecraftVersion")

    if (isUnobfuscated) {
        add("implementation", "net.fabricmc:fabric-loader:$loaderVersion")
        add("implementation", "meteordevelopment:meteor-client:$minecraftVersion-SNAPSHOT")
        add("compileOnly", "meteordevelopment:baritone:$baritoneVersion-SNAPSHOT")
    } else {
        val yarnMappings = properties["yarn_mappings_$targetVersion"] as String
        add("mappings", "net.fabricmc:yarn:$yarnMappings:v2")
        add("modImplementation", "net.fabricmc:fabric-loader:$loaderVersion")
        add("modImplementation", "meteordevelopment:meteor-client:$minecraftVersion-SNAPSHOT")
        add("modCompileOnly", "meteordevelopment:baritone:$baritoneVersion-SNAPSHOT")
    }
}

val javaVersion = if (isUnobfuscated) 25 else 21

extensions.configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

tasks.withType(JavaCompile::class.java).configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.named("processResources", ProcessResources::class.java).configure {
    val commit = project.findProperty("commit")?.toString() ?: ""

    val propertyMap = mapOf(
        "version" to project.version,
        "mc_version" to minecraftVersion,
        "commit" to commit,
    )

    inputs.properties(propertyMap)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(propertyMap)
    }
}

tasks.named("jar", Jar::class.java).configure {
    from("LICENSE") {
        rename { "${it}_$archivesNameValue" }
    }
}
