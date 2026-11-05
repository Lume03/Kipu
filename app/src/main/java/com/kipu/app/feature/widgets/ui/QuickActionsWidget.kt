package com.kipu.app.feature.widgets.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.kipu.app.feature.widgets.di.widgetDataProvider

/** Accesos: the four things done most often, one tap away, without opening menus. */
class QuickActionsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = widgetDataProvider(context).load()
        provideContent { WidgetFrame(state, "money") { Actions(it.captures.pendingCount) } }
    }
}

class QuickActionsReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}

@Composable
private fun Actions(pending: Int) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Action(Icons.Rounded.Add, "Registrar", "register", highlighted = true, modifier = GlanceModifier.defaultWeight())
        Action(Icons.Rounded.ReceiptLong, "Movimientos", "movements", modifier = GlanceModifier.defaultWeight())
        Action(Icons.Rounded.Inbox, if (pending > 0) "Capturas · $pending" else "Capturas", "capture", warn = pending > 0, modifier = GlanceModifier.defaultWeight())
        Action(Icons.Rounded.CalendarMonth, "Calendario", "calendar", modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, target: String, highlighted: Boolean = false, warn: Boolean = false, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(modifier.clickable(WidgetLinks.open(context, target)), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            GlanceModifier.size(36.dp).background(if (highlighted) WColors.brand else WColors.tile).cornerRadius(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Pic(glyph(icon, px(48)), 20.dp, tint = if (highlighted) WColors.onBrand else WColors.ink)
        }
        Spacer(GlanceModifier.height(3.dp))
        if (warn) Strong(label, 10.5f, WColors.warn, maxLines = 1) else Strong(label, 10.5f, WColors.ink, maxLines = 1)
    }
}
