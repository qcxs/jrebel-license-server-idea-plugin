package com.yuxiaoyao.jrebel.license

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.yuxiaoyao.jrebel.license.server.LicenseHttpServer
import com.yuxiaoyao.jrebel.license.server.ServerConfig
import com.yuxiaoyao.jrebel.license.settings.LicenseServerSettings
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 应用级服务：随 IDE 启动/关闭，管理内嵌 HTTP 服务器的生命周期。
 */
@Service(Service.Level.APP)
class LicenseServerService : Disposable {

    data class Status(val running: Boolean, val port: Int, val lastError: String?)

    private val settings: LicenseServerSettings
        get() = service()

    private val httpServer = LicenseHttpServer { buildConfig() }
    private val listeners = CopyOnWriteArrayList<(Status) -> Unit>()

    @Volatile
    private var lastError: String? = null

    /** 仅在开启了“随 IDE 启动”时启动；返回 null 表示本次没有尝试启动。 */
    fun startIfNeeded(): Status? {
        if (!settings.getState().autoStart) {
            return null
        }
        return start()
    }

    @Synchronized
    fun start(): Status {
        val state = settings.getState()
        try {
            httpServer.start(state.port)
            lastError = null
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
        }
        return status().also { notifyListeners() }
    }

    @Synchronized
    fun stop(): Status {
        httpServer.stop()
        lastError = null
        return status().also { notifyListeners() }
    }

    @Synchronized
    fun restart(): Status {
        httpServer.stop()
        return start()
    }

    @Synchronized
    fun status(): Status {
        val state = settings.getState()
        return Status(
            running = httpServer.running,
            port = if (httpServer.running) httpServer.port else state.port,
            lastError = lastError,
        )
    }

    fun activationUrl(): String = "http://127.0.0.1:${status().port}/${settings.getState().uuid}"

    @Synchronized
    fun regenerateUuid(): String {
        val uuid = UUID.randomUUID().toString()
        settings.getState().uuid = uuid
        notifyListeners()
        return uuid
    }

    fun addListener(listener: (Status) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Status) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        val status = status()
        listeners.forEach { listener -> runCatching { listener(status) } }
    }

    private fun buildConfig(): ServerConfig {
        val state = settings.getState()
        return ServerConfig(
            port = if (httpServer.running) httpServer.port else state.port,
            uuid = state.uuid,
            offlineDays = state.offlineDays,
            ignoreOfflineDays = state.ignoreOfflineDays,
            workMode = state.workMode,
            exportSchema = state.exportSchema,
            exportHost = state.exportHost,
        )
    }

    override fun dispose() {
        httpServer.stop()
        listeners.clear()
    }
}
