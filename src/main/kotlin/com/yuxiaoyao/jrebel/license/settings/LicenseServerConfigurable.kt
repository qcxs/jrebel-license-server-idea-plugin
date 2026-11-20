package com.yuxiaoyao.jrebel.license.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.yuxiaoyao.jrebel.license.LicenseServerNotifications
import com.yuxiaoyao.jrebel.license.LicenseServerService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class LicenseServerConfigurable : Configurable {

    private val settings: LicenseServerSettings
        get() = LicenseServerSettings.getInstance()

    private val server: LicenseServerService
        get() = ApplicationManager.getApplication().getService(LicenseServerService::class.java)

    private val portField = JBTextField(8)
    private val offlineDaysField = JBTextField(8)
    private val ignoreOfflineCheck = JBCheckBox("忽略客户端参数，强制使用上面的离线天数")
    private val workModeCombo = ComboBox(arrayOf("自动（按客户端参数）", "强制离线", "强制在线", "旧版 GUID"))
    private val schemaCombo = ComboBox(arrayOf("http", "https"))
    private val exportHostField = JBTextField()
    private val autoStartCheck = JBCheckBox("随 IDE 启动激活服务")
    private val activationField = JBTextField().apply { isEditable = false }
    private val statusLabel = JBLabel()
    private val toggleButton = JButton("启动")

    private val listener: (LicenseServerService.Status) -> Unit = { updateStatus(it) }
    private var root: JComponent? = null
    private var listening = false

    override fun getDisplayName(): String = "JRebel License Server"

    override fun createComponent(): JComponent {
        root?.let { return it }

        val copyButton = JButton("复制")
        copyButton.addActionListener { copyActivationUrl() }

        val regenerateButton = JButton("重新生成 UUID")
        regenerateButton.addActionListener {
            server.regenerateUuid()
            updateStatus(server.status())
            copyActivationUrl()
        }

        toggleButton.addActionListener { toggleServer() }

        val activationRow = JPanel(BorderLayout(8, 0)).apply {
            add(activationField, BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    add(copyButton)
                    add(regenerateButton)
                },
                BorderLayout.EAST,
            )
        }

        val statusRow = JPanel(BorderLayout(8, 0)).apply {
            add(statusLabel, BorderLayout.CENTER)
            add(toggleButton, BorderLayout.WEST)
        }

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("监听端口：", portField)
            .addComponent(autoStartCheck)
            .addSeparator()
            .addLabeledComponent("离线天数：", offlineDaysField)
            .addComponent(ignoreOfflineCheck)
            .addLabeledComponent("工作模式：", workModeCombo)
            .addSeparator()
            .addLabeledComponent("首页展示协议：", schemaCombo)
            .addLabeledComponent("首页展示主机：", exportHostField)
            .addSeparator()
            .addLabeledComponent("激活地址：", activationRow)
            .addComponent(statusRow)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        panel.border = JBUI.Borders.empty(8)
        root = panel

        server.addListener(listener)
        listening = true
        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val state = settings.getState()
        return portField.text.trim().toIntOrNull() != state.port ||
            offlineDaysField.text.trim().toLongOrNull() != state.offlineDays ||
            ignoreOfflineCheck.isSelected != state.ignoreOfflineDays ||
            workModeCombo.selectedIndex != state.workMode ||
            schemaCombo.selectedItem != state.exportSchema ||
            exportHostField.text.trim() != state.exportHost ||
            autoStartCheck.isSelected != state.autoStart
    }

    override fun apply() {
        val port = portField.text.trim().toIntOrNull()
        if (port == null || !LicenseServerSettings.isValidPort(port)) {
            Messages.showErrorDialog("端口必须是 1-65535 之间的整数。", TITLE)
            return
        }
        val offlineDays = offlineDaysField.text.trim().toLongOrNull()
        if (offlineDays == null || offlineDays <= 0) {
            Messages.showErrorDialog("离线天数必须是大于 0 的整数。", TITLE)
            return
        }

        val state = settings.getState()
        val oldPort = state.port
        val wasRunning = server.status().running
        val wasAutoStart = state.autoStart

        state.port = port
        state.offlineDays = offlineDays
        state.ignoreOfflineDays = ignoreOfflineCheck.isSelected
        state.workMode = workModeCombo.selectedIndex.coerceIn(0, 3)
        state.exportSchema = (schemaCombo.selectedItem as? String) ?: "http"
        state.exportHost = exportHostField.text.trim()
        state.autoStart = autoStartCheck.isSelected

        when {
            wasRunning && oldPort != port -> {
                val status = server.restart()
                if (!status.running) {
                    // 与 Go 版界面一致：新端口启动失败时回退到原端口。
                    state.port = oldPort
                    portField.text = oldPort.toString()
                    val restored = server.start()
                    val reason = status.lastError ?: "端口可能被占用"
                    if (restored.running) {
                        LicenseServerNotifications.error("切换端口失败，已恢复端口 $oldPort", reason)
                    } else {
                        LicenseServerNotifications.error("切换端口失败", reason)
                    }
                }
            }

            !wasRunning && !wasAutoStart && state.autoStart -> {
                val status = server.start()
                if (!status.running) {
                    LicenseServerNotifications.error("启动服务失败", status.lastError ?: "端口可能被占用")
                }
            }
        }

        updateStatus(server.status())
    }

    override fun reset() {
        val state = settings.getState()
        portField.text = state.port.toString()
        offlineDaysField.text = state.offlineDays.toString()
        ignoreOfflineCheck.isSelected = state.ignoreOfflineDays
        workModeCombo.selectedIndex = state.workMode.coerceIn(0, 3)
        schemaCombo.selectedItem = state.exportSchema
        exportHostField.text = state.exportHost
        autoStartCheck.isSelected = state.autoStart
        updateStatus(server.status())
    }

    override fun disposeUIResources() {
        if (listening) {
            server.removeListener(listener)
            listening = false
        }
        root = null
    }

    private fun toggleServer() {
        val current = server.status()
        val next = if (current.running) server.stop() else server.start()
        if (!next.running && !current.running) {
            LicenseServerNotifications.error("JRebel 激活服务启动失败", next.lastError ?: "端口可能被占用")
        }
        updateStatus(next)
    }

    private fun copyActivationUrl() {
        val url = server.activationUrl()
        CopyPasteManager.getInstance().setContents(StringSelection(url))
        LicenseServerNotifications.info("JRebel 激活地址已复制", url)
    }

    private fun updateStatus(status: LicenseServerService.Status) {
        ApplicationManager.getApplication().invokeLater {
            when {
                status.running -> {
                    statusLabel.text = "● 运行中（127.0.0.1:${status.port}）"
                    statusLabel.foreground = JBColor(0x22A06B, 0x4CC38A)
                }

                status.lastError != null -> {
                    statusLabel.text = "● 启动失败：${status.lastError}"
                    statusLabel.foreground = JBColor.RED
                }

                else -> {
                    statusLabel.text = "● 服务已停止"
                    statusLabel.foreground = UIUtil.getLabelForeground()
                }
            }
            activationField.text = "http://127.0.0.1:${status.port}/${settings.getState().uuid}"
            toggleButton.text = if (status.running) "停止" else "启动"
        }
    }

    private companion object {
        const val TITLE = "JRebel License Server"
    }
}
