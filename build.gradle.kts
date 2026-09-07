plugins {
    id("net.fabricmc.fabric-loom") version "1.15-SNAPSHOT"
}

// 26.1+ ships unobfuscated with official Mojang mappings baked in — Yarn is no longer published for it,
// and the new Loom plugin has no mappings()/modImplementation()/modCompileOnly() DSL at all.
val minecraftVersion = properties["minecraft_version_26_1_2"] as String
val loaderVersion = properties["loader_version_26_1_2"] as String
// Baritone's Meteor fork is versioned by the MC minor release (26.1), not the full patch version.
val baritoneVersion = "26.1"

base {
    archivesName = "${properties["archives_base_name"] as String} ($minecraftVersion)"
    version = properties["mod_version"] as String
    group = properties["maven_group"] as String
}

// This branch only ports BetterBaritoneBuild to 26.1.2 - the rest of the addon's modules/commands/HUDs
// haven't been touched for this target and won't compile against it, so they're excluded here rather
// than deleted (uncomment/remove this block once/if they get ported too).
sourceSets {
    main {
        java {
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
    // Fabric
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")

    // Meteor
    implementation("meteordevelopment:meteor-client:$minecraftVersion-SNAPSHOT")

    // Baritone
    compileOnly("meteordevelopment:baritone:$baritoneVersion-SNAPSHOT")
}

tasks {
    processResources {
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

    jar {
        val licenseSuffix = project.base.archivesName.get()
        from("LICENSE") {
            rename { "${it}_${licenseSuffix}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 25
    }
}
