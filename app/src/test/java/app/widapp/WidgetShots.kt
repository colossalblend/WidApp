package app.widapp

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import app.widapp.spec.DataSnapshot
import app.widapp.spec.Samples
import app.widapp.spec.WidgetPalette
import app.widapp.spec.WidgetSize
import app.widapp.spec.WidgetSpec
import app.widapp.ui.components.WidgetPreview
import app.widapp.ui.theme.WidTheme
import app.widapp.widget.GlanceRenderer
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class SpecWidget(private val spec: WidgetSpec) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceRenderer(context, spec, spec.state, DataSnapshot.capture(context, null), WidgetPalette.from(context, false), LocalSize.current).Root()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetShots {
    @get:Rule val compose = createComposeRule()

    /** Real Glance → RemoteViews → View pipeline, the same one the launcher runs. */
    @Test fun glance() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Samples.parsed().forEach { (id, spec) ->
            val size = WidgetSize.of(spec.size)
            val rv = runBlocking { SpecWidget(spec).compose(ctx, size = DpSize(size.widthDp.dp, size.heightDp.dp)) }
            val parent = FrameLayout(ctx)
            val view = rv.apply(ctx, parent)
            val d = ctx.resources.displayMetrics.density
            val w = (size.widthDp * d).toInt(); val h = (size.heightDp * d).toInt()
            parent.addView(view, FrameLayout.LayoutParams(w, h))
            parent.setBackgroundColor(0xFF6B7F8C.toInt())
            parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            parent.layout(0, 0, w, h)
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            parent.draw(android.graphics.Canvas(bmp))
            java.io.File("build/shots").mkdirs()
            java.io.FileOutputStream("build/shots/glance_$id.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun preview() {
        compose.setContent {
            WidTheme {
                Box(Modifier.background(Color(0xFF6B7F8C)).padding(16.dp)) {
                    androidx.compose.foundation.layout.Column {
                        Samples.parsed().forEach { (_, spec) ->
                            val s = WidgetSize.of(spec.size)
                            Box(Modifier.padding(bottom = 12.dp)) {
                                WidgetPreview(spec, spec.state, DataSnapshot.capture(ApplicationProvider.getApplicationContext(), null),
                                    WidgetPalette.from(ApplicationProvider.getApplicationContext(), false), false, Modifier.width(s.widthDp.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/shots/preview_samples.png")
    }
}
