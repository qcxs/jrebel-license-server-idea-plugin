import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.yuxiaoyao"
// 版本号固定，仅用于显示；覆盖安装不受版本高低限制。
version = "1.0.0"

repositories {
    // Maven Central 国内镜像优先，官方仓库作为兜底。
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies {
    // 编译期对齐 2021.2/2021.3 运行时自带的 Kotlin 1.5 API，不打包进插件。
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.5.21")
}

intellij {
    // 旧版包以 2021.3.3（build 213）为编译基线，旧版 IDE 运行在 JBR 11 上。
    version.set("2021.3.3")
    type.set("IC")
    plugins.set(emptyList())
    pluginName.set("jrebel-license-server-idea-plugin-legacy")
    instrumentCode.set(false)
}

kotlin {
    // 构建工具链使用 JDK 17，但目标字节码为 Java 11，兼容 JBR 11 的旧版 IDE。
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

sourceSets {
    getByName("main") {
        kotlin.srcDirs("../src/main/kotlin", "../src/legacy/kotlin")
        resources.srcDir("../src/main/resources")
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        pluginXmlFiles.set(listOf(file("../src/main/resources/META-INF/plugin.xml")))
        version.set(project.version.toString())
        // 旧版包：2021.2（212）到 2022.2（222.*）。
        sinceBuild.set("212")
        untilBuild.set("222.*")
    }
}
