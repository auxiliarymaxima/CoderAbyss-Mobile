package com.coderabyss.mobile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import com.coderabyss.mobile.models.Service
import com.coderabyss.mobile.platformui.WorkspaceScreen
import com.coderabyss.mobile.platformui.WorkspaceViewModel
import com.coderabyss.mobile.presentation.AbyssTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** Host-render the actual Compose screens, without credentials, inference, or sample results. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkflowScreenshotTest {
    @Test fun renderAppInput() = render(Service.APP)
    @Test fun renderVideoInput() = render(Service.VIDEO)
    @Test fun renderResearchInput() = render(Service.RESEARCH)
    @Test fun renderVisualInput() = render(Service.VISUAL)
    private fun render(service: Service) {
        val app = RuntimeEnvironment.getApplication()
        val vm = WorkspaceViewModel(app)
        run {
            val id = vm.projects.create(service)
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            try {
                val activity = controller.get()
                activity.setContent { AbyssTheme { androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color(0xFF020810)) { Column { Box(Modifier.weight(1f)) { WorkspaceScreen(id, vm, {}, {}, {}) }; com.coderabyss.mobile.platformui.WorkflowNavigation(service, {}, {}, {}, {}, {}, {}) } } } }
                val root = activity.window.decorView
                repeat(12) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                    root.measure(View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(780, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 411, 780)
                }
                val bitmap = Bitmap.createBitmap(411, 780, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val colors = (0 until 780 step 8).flatMap { y -> (0 until 411 step 8).map { x -> bitmap.getPixel(x, y) } }.toSet()
                assertTrue("${service.name} must render real screen content", colors.size > 20)
                val output = File("build/reports/workflow-screenshots/${service.name.lowercase()}.png")
                output.parentFile.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            } finally { controller.pause().stop().destroy() }
        }
    }
}
