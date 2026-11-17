package com.yuxiaoyao.jrebel.license.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.yuxiaoyao.jrebel.license.LicenseServerNotifications
import com.yuxiaoyao.jrebel.license.LicenseServerService
import java.awt.datatransfer.StringSelection

class CopyActivationUrlAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {
        val server = service<LicenseServerService>()
        var status = server.status()
        if (!status.running) {
            status = server.start()
        }
        if (!status.running) {
            LicenseServerNotifications.error(
                "JRebel 激活服务启动失败",
                status.lastError ?: "端口可能被占用",
            )
            return
        }

        val url = server.activationUrl()
        CopyPasteManager.getInstance().setContents(StringSelection(url))
        LicenseServerNotifications.info("JRebel 激活地址已复制", url)
    }
}
