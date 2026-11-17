package com.yuxiaoyao.jrebel.license.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.components.JBLabel
import com.yuxiaoyao.jrebel.license.LicenseServerNotifications
import com.yuxiaoyao.jrebel.license.LicenseServerService
import com.yuxiaoyao.jrebel.license.settings.LicenseServerConfigurable
import java.awt.Cursor
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

class LicenseServerStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = LicenseServerStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "JRebel 激活服务"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = LicenseServerStatusBarWidget()
}

/**
 * 状态栏展示服务状态，点击后提供复制地址、启停、重启和打开设置。
 */
class LicenseServerStatusBarWidget : CustomStatusBarWidget, Disposable {

    private val server: LicenseServerService = service()

    private val label = JBLabel().apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = showPopup(e)
        })
    }

    private val listener: (LicenseServerService.Status) -> Unit = { update(it) }

    private var statusBar: StatusBar? = null
    private var disposed = false

    override fun ID(): String = WIDGET_ID

    override fun getComponent() = label

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        server.addListener(listener)
        update(server.status())
    }

    override fun dispose() {
        disposed = true
        server.removeListener(listener)
    }

    private fun update(status: LicenseServerService.Status) {
        ApplicationManager.getApplication().invokeLater {
            if (disposed) {
                return@invokeLater
            }
            label.text = if (status.running) "JRebel :${status.port}" else "JRebel 未启动"
            label.toolTipText = when {
                status.running -> "激活地址：${server.activationUrl()}"
                status.lastError != null -> "启动失败：${status.lastError}"
                else -> "JRebel 激活服务已停止"
            }
            statusBar?.updateWidget(WIDGET_ID)
        }
    }

    private fun showPopup(event: MouseEvent) {
        val current = server.status()
        val popup = JPopupMenu().apply {
            add(menuItem("复制激活地址") { copyActivationUrl() })
            add(menuItem("重新生成 UUID") {
                server.regenerateUuid()
                copyActivationUrl()
            })
            addSeparator()
            add(menuItem(if (current.running) "停止服务" else "启动服务") {
                val status = if (current.running) server.stop() else server.start()
                notifyIfFailed(status)
            })
            add(menuItem("重启服务") {
                notifyIfFailed(server.restart())
            })
            addSeparator()
            add(menuItem("设置…") {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(null, LicenseServerConfigurable::class.java)
            })
        }
        popup.show(label, event.x, event.y)
    }

    private fun copyActivationUrl() {
        val url = server.activationUrl()
        CopyPasteManager.getInstance().setContents(StringSelection(url))
        LicenseServerNotifications.info("JRebel 激活地址已复制", url)
    }

    private fun notifyIfFailed(status: LicenseServerService.Status) {
        if (!status.running) {
            LicenseServerNotifications.error("JRebel 激活服务启动失败", status.lastError ?: "端口可能被占用")
        }
    }

    private fun menuItem(text: String, action: () -> Unit): JMenuItem =
        JMenuItem(text).apply { addActionListener { action() } }

    companion object {
        const val WIDGET_ID = "JRebelLicenseServerStatusWidget"
    }
}
