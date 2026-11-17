package com.yuxiaoyao.jrebel.license

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType

object LicenseServerNotifications {

    private const val GROUP_ID = "JRebelLicenseServer"

    fun info(title: String, content: String) {
        notify(title, content, NotificationType.INFORMATION)
    }

    fun error(title: String, content: String) {
        notify(title, content, NotificationType.ERROR)
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)
            .notify(null)
    }
}
