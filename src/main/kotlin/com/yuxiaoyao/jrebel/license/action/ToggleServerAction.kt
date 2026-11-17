package com.yuxiaoyao.jrebel.license.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.yuxiaoyao.jrebel.license.LicenseServerNotifications
import com.yuxiaoyao.jrebel.license.LicenseServerService

class ToggleServerAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val running = service<LicenseServerService>().status().running
        event.presentation.text = if (running) "JRebel: 停止激活服务" else "JRebel: 启动激活服务"
    }

    override fun actionPerformed(event: AnActionEvent) {
        val server = service<LicenseServerService>()
        val current = server.status()
        val next = if (current.running) server.stop() else server.start()
        if (!next.running && !current.running) {
            LicenseServerNotifications.error(
                "JRebel 激活服务启动失败",
                next.lastError ?: "端口可能被占用",
            )
        }
    }
}
