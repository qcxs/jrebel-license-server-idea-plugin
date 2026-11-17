pluginManagement {
    repositories {
        // 国内镜像优先，官方仓库作为兜底。
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "jrebel-license-server-idea-plugin"
