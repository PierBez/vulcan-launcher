package dev.vulcanlauncher

import android.accessibilityservice.AccessibilityService
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent

/** Notification access (the user turns it on): keeps the active notifications for badges and the long-press menu. */
class BadgeService : NotificationListenerService() {
    companion object {
        var instance: BadgeService? = null
        /** Package → its current clearable, non-group-summary notifications. */
        var byPackage: Map<String, List<StatusBarNotification>> = emptyMap()
        var onChange: () -> Unit = {}
    }

    override fun onListenerConnected() { instance = this; refresh() }
    override fun onListenerDisconnected() { instance = null; byPackage = emptyMap(); onChange() }
    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        byPackage = runCatching { activeNotifications.orEmpty() }.getOrDefault(emptyArray())
            .filter { it.isClearable && it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0 }
            .groupBy { it.packageName }
        onChange()
    }
}

/** Accessibility service (the user turns it on): only used to perform global actions such as screen lock and recents. */
class ActionService : AccessibilityService() {
    companion object { var instance: ActionService? = null }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
