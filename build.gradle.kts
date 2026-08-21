plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "org.katacr"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.alessiodp.com/releases/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://jitpack.io")
}

// Spigot 平台适配器编译在独立 sourceSet：使用 Spigot Bungee Dialog API，
// 与 main（Paper）互不依赖对方专属类，最终由 shadowJar 打进同一 JAR。
val spigotAdapter by sourceSets.creating {
    java.srcDir("src/spigot/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.23.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit")
    }
    implementation("net.byteflux:libby-bukkit:1.3.0")
    compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    add(spigotAdapter.compileOnlyConfigurationName, "org.spigotmc:spigot-api:1.21.6-R0.1-SNAPSHOT")
    add(spigotAdapter.compileOnlyConfigurationName, "net.kyori:adventure-text-serializer-bungeecord:4.4.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        val properties = mapOf("version" to version)
        inputs.properties(properties)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(properties)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("net.byteflux", "org.katacr.katpa.libs.libby")
        dependsOn(spigotAdapter.classesTaskName)
        from(spigotAdapter.output)
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.21.7")
    }
}
