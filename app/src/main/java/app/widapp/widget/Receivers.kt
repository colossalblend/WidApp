package app.widapp.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import app.widapp.spec.WidgetSize

/** One receiver per default grid size so pinning from the app lands at the right size. */
abstract class WidAppReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidAppWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}

class Widget1x1Receiver : WidAppReceiver()
class Widget2x1Receiver : WidAppReceiver()
class Widget2x2Receiver : WidAppReceiver()
class Widget3x2Receiver : WidAppReceiver()
class Widget4x1Receiver : WidAppReceiver()
class Widget4x2Receiver : WidAppReceiver()
class Widget4x3Receiver : WidAppReceiver()
class Widget4x4Receiver : WidAppReceiver()

fun receiverFor(size: WidgetSize): Class<out WidAppReceiver> = when (size) {
    WidgetSize.S1x1 -> Widget1x1Receiver::class.java
    WidgetSize.S2x1 -> Widget2x1Receiver::class.java
    WidgetSize.S2x2 -> Widget2x2Receiver::class.java
    WidgetSize.S3x2 -> Widget3x2Receiver::class.java
    WidgetSize.S4x1 -> Widget4x1Receiver::class.java
    WidgetSize.S4x2 -> Widget4x2Receiver::class.java
    WidgetSize.S4x3 -> Widget4x3Receiver::class.java
    WidgetSize.S4x4 -> Widget4x4Receiver::class.java
}
