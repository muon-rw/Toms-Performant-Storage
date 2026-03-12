import dev.muon.tomsperformantstorage.gradle.Properties
import dev.muon.tomsperformantstorage.gradle.Versions
import org.gradle.jvm.tasks.Jar

plugins {
    id("conventions.loader")
    id("net.neoforged.moddev.legacyforge")
    id("me.modmuss50.mod-publish-plugin")
    id("dev.mixinmcp.decompile")
}

repositories {
    maven("https://maven.blamejared.com/")
    maven("https://maven.wispforest.io/releases")
    maven("https://maven.su5ed.dev/releases")
    maven("https://maven.fabricmc.net")
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/")
    maven("https://maven.ladysnake.org/releases")
    maven("https://maven.parchmentmc.org")
    maven {
        url = uri("https://cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    maven {
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven {
        url = uri("https://maven.ftb.dev/snapshots")
        content {
            includeGroup("dev.latvian.mods")
            includeGroup("dev.ftb.mods")
        }
    }
    maven {
        url = uri("https://maven.ftb.dev/releases")
        content {
            includeGroup("dev.latvian.mods")
            includeGroup("dev.ftb.mods")
        }
    }
    maven("https://maven.architectury.dev/")
    maven("https://code.redspace.io/releases")
    maven("https://code.redspace.io/snapshots")
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    compileOnly("io.github.llamalad7:mixinextras-common:${Versions.MIXIN_EXTRAS}")
    annotationProcessor("io.github.llamalad7:mixinextras-common:${Versions.MIXIN_EXTRAS}")
    jarJar(implementation("io.github.llamalad7:mixinextras-forge:${Versions.MIXIN_EXTRAS}") {
        version {
            strictly("[0.5.3,)")
            prefer("0.5.3")
        }
    })
    modImplementation("curse.maven:toms-storage-378609:6418133")
}

mixin {
    add(sourceSets["main"], "${Properties.MOD_ID}.refmap.json")
    config("${Properties.MOD_ID}.mixins.json")
    config("${Properties.MOD_ID}.forge.mixins.json")
}

legacyForge {
    version = "${Versions.MINECRAFT}-${Versions.FORGE}"
    parchment {
        minecraftVersion = Versions.PARCHMENT_MINECRAFT
        mappingsVersion = Versions.PARCHMENT
    }
    addModdingDependenciesTo(sourceSets["test"])

    val at = project(":common").file("src/main/resources/${Properties.MOD_ID}.cfg")
    if (at.exists())
        setAccessTransformers(at)
    validateAccessTransformers = true

    runs {
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("forge.logging.console.level", "debug")
            systemProperty("forge.enabledGameTestNamespaces", Properties.MOD_ID)
        }
        create("client") {
            client()
            ideName = "Forge Client (:${project.name})"
            gameDirectory.set(file("runs/client"))
            sourceSet = sourceSets["test"]
            jvmArguments.set(setOf("-Dmixin.debug.verbose=true", "-Dmixin.debug.export=true"))
        }
        create("clientExtra") {
            client()
            ideName = "Forge Client 2 (:${project.name})"
            gameDirectory.set(file("runs/client2"))
            sourceSet = sourceSets["test"]
            jvmArguments.set(setOf("-Dmixin.debug.verbose=true", "-Dmixin.debug.export=true"))
            programArguments.addAll("--username", "Dev2")
        }
        create("server") {
            server()
            ideName = "Forge Server (:${project.name})"
            gameDirectory.set(file("runs/server"))
            programArgument("--nogui")
            sourceSet = sourceSets["test"]
            jvmArguments.set(setOf("-Dmixin.debug.verbose=true", "-Dmixin.debug.export=true"))
        }
    }

    mods {
        register(Properties.MOD_ID) {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["test"])
        }
    }
}

tasks {
    named<Jar>("jar").configure {
        manifest {
            attributes("MixinConfigs" to "${Properties.MOD_ID}.mixins.json,${Properties.MOD_ID}.forge.mixins.json")
        }
    }
    register<Copy>("sendToModpack") {
        group = "publishing"
        description = "Copies a dev-versioned jar to the local Forge modpack"
        from(named<Jar>("reobfJar").flatMap { it.archiveFile })
        into("${System.getProperty("user.home")}/Documents/curseforge/minecraft/Instances/Forge/mods")
        rename { it.replace(Versions.MOD, "${Versions.MOD}-dev") }
    }
}

publishMods {
    file.set(tasks.named<Jar>("reobfJar").get().archiveFile)
    modLoaders.add("forge")
    changelog = rootProject.file("CHANGELOG.md").readText()
    displayName = "Forge-${Versions.MOD}+${Versions.MINECRAFT}"
    version = "${Versions.MOD}+${Versions.MINECRAFT}-forge"
    type = STABLE

    curseforge {
        projectId = Properties.CURSEFORGE_PROJECT_ID
        accessToken = providers.environmentVariable("CF_TOKEN")

        minecraftVersions.add(Versions.MINECRAFT)
        javaVersions.add(JavaVersion.VERSION_17)

        clientRequired = true
        serverRequired = true

        requires {
            slug = "ftb-teams-forge"
        }
    }
}