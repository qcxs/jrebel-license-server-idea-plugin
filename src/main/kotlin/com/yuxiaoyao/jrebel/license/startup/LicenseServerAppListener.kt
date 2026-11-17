package com.yuxiaoyao.jrebel.license.startup

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.components.service
import com.yuxiaoyao.jrebel.license.LicenseServerNotifications
import com.yuxiaoyao.jrebel.license.LicenseServerService

class LicenseServerAppListener : AppLifecycleListener {

    override fun appStarted() {
        val status = service<LicenseServerService>().startIfNeeded() ?: return
        if (!status.running) {
            LicenseServerNotifications.error(
                "JRebel 激活服务启动失败",
                status.lastError ?: "无法监听端口 ${status.port}",
            )
        }
    }
}
