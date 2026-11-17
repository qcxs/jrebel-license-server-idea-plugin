package com.yuxiaoyao.jrebel.license.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.util.UUID

@Service(Service.Level.APP)
@State(name = "JRebelLicenseServerSettings", storages = [Storage("jrebel-license-server.xml")])
class LicenseServerSettings : PersistentStateComponent<LicenseServerSettings.State> {

    data class State(
        var port: Int = DEFAULT_PORT,
        var offlineDays: Long = DEFAULT_OFFLINE_DAYS,
        var ignoreOfflineDays: Boolean = false,
        var workMode: Int = WORK_MODE_AUTO,
        var exportSchema: String = "http",
        var exportHost: String = "",
        var autoStart: Boolean = true,
        var uuid: String = UUID.randomUUID().toString(),
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        state.port = if (isValidPort(state.port)) state.port else DEFAULT_PORT
        state.offlineDays = if (state.offlineDays > 0) state.offlineDays else DEFAULT_OFFLINE_DAYS
        state.workMode = if (state.workMode in 0..3) state.workMode else WORK_MODE_AUTO
        state.exportSchema = if (state.exportSchema == "https") "https" else "http"
        state.exportHost = state.exportHost.trim()
        if (state.uuid.isBlank()) {
            state.uuid = UUID.randomUUID().toString()
        }
        myState = state
    }

    companion object {
        const val DEFAULT_PORT = 12345
        const val DEFAULT_OFFLINE_DAYS = 30L

        const val WORK_MODE_AUTO = 0
        const val WORK_MODE_OFFLINE = 1
        const val WORK_MODE_ONLINE = 2
        const val WORK_MODE_OLD_GUID = 3

        fun getInstance(): LicenseServerSettings = service()

        fun isValidPort(port: Int): Boolean = port in 1..65535
    }
}
