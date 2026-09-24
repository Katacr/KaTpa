plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "org.katacr"
version = "1.2.1"

repositories {
    mavenCentral()
    maven("https://repo.alessiodp.com/releases/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://jitpack.io")
    maven("https://maven.aliyun.com/repository/public")
}

dependencies {
    // 共享运行时以最旧公开 Bukkit API 为编译基准，保证 1.16.5 兼容。
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    // Adventure 仅编译期可见；运行时 Paper 1.16.5 内置，Spigot 由 Libby 挂载。
    compileOnly("net.kyori:adventure-api:4.26.1")
    compileOnly("net.kyori:adventure-key:4.26.1")
    compileOnly("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.26.1")
    compileOnly("net.kyori:adventure-text-serializer-plain:4.26.1")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit")
    }
    compileOnly("com.github.PlaceholderAPI:PlaceholderAPI:2.11.6")
    implementation("net.byteflux:libby-bukkit:1.3.0")
    compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// 插件字节码需运行在 1.16.5 的 Java 16 运行时；构建本身使用 Java 21 toolchain（Gradle 8 不兼容 Java 16）。
val targetJavaVersion = 16
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(targetJavaVersion)
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
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.16.5")
    }
}
