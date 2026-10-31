package com.kipu.app.feature.notifications.presentation

import androidx.compose.runtime.Composable

@Composable
internal fun rememberReducedMotionEnabled(): Boolean {
    return com.kipu.app.ui.motion.rememberReducedMotionEnabled()
}

/** Keep placement/removal and destructive feedback aligned with Kipu's existing motion tokens. */
internal const val NOTIFICATION_SWIPE_THRESHOLD = 0.4f
internal const val NOTIFICATION_DISMISS_MILLIS = com.kipu.app.ui.theme.KipuMotionTokens.SubtreeExitMillis

/** Commit on navigation, disposal or backgrounding; a configuration change keeps the undo window. */
@Composable
internal fun CommitNotificationDismissalsOnExit(onCommit: () -> Unit) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val latestCommit = androidx.compose.runtime.rememberUpdatedState(onCommit)
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, context) {
        fun commitUnlessRecreating() {
            if (context.notificationActivity()?.isChangingConfigurations != true) latestCommit.value()
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) commitUnlessRecreating()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            commitUnlessRecreating()
        }
    }
}

private tailrec fun android.content.Context.notificationActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.notificationActivity()
    else -> null
}
