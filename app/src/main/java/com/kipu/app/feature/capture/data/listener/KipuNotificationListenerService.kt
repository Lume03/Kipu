package com.kipu.app.feature.capture.data.listener

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kipu.app.feature.capture.domain.CaptureAccessGate
import com.kipu.app.feature.capture.domain.CaptureSignal
import com.kipu.app.feature.capture.domain.CaptureSourceCatalog
import com.kipu.app.feature.capture.domain.NotificationCaptureIngress
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@AndroidEntryPoint
class KipuNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var catalog: CaptureSourceCatalog
    @Inject lateinit var gate: CaptureAccessGate
    @Inject lateinit var ingress: NotificationCaptureIngress

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serial = Mutex()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val sourcePackage = sbn.packageName
        if (sourcePackage == packageName || !catalog.supportsPackage(sourcePackage)) return
        val ownerId = gate.currentOwnerId() ?: return
        scope.launch {
            try {
                serial.withLock {
                    ingress.receive(sourcePackage, ownerId) {
                        val notification = sbn.notification
                        val excludedFlags = Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT
                        if (notification.flags and excludedFlags != 0) return@receive null
                        // Extras are accessed exclusively inside the authorized lazy reader.
                        val extras = notification.extras
                        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
                        val content = bigText.ifBlank { text }
                        val provider = catalog.provider(sourcePackage, title, content) ?: return@receive null
                        CaptureSignal(provider, sourcePackage, sbn.key, sbn.postTime, title, content)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Fail closed. Never log notification contents or exception messages from parsing.
            }
        }
    }

    // No onListenerConnected history scan: only callbacks posted after authorization are processed.
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
