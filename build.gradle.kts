import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.yuxiaoyao"
// 版本号固定，仅用于显示；覆盖安装不受版本高低限制。
version = "1.0.0"

repositories {
    // Maven Central 国内镜像优先，官方仓库作为兜底。
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
    intellijPlatform {
        // IntelliJ Platform 制品由 JetBrains CDN 提供，无可用国内镜像。
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2023.3.4")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        id = "com.yuxiaoyao.jrebel.license.server"
        name = "JRebel License Server"
        version = project.version.toString()

        ideaVersion {
            // 新版包：覆盖 IntelliJ IDEA 2022.3（build 223）及以上。
            sinceBuild = "223"
            untilBuild = provider { null }
        }

        vendor {
            name = "qcxs"
            url = "https://github.com/qcxs/jrebel-license-server-idea-plugin"
        }
    }
}

kotlin {
    // 新版包目标 IntelliJ Platform 2022.3+，使用 Java 17 字节码。
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin.sourceSets["main"].kotlin.srcDir("src/modern/kotlin")

tasks {
    test {
        useJUnitPlatform()
    }
}
