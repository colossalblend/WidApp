package app.widapp

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.widapp.data.ProviderConfig
import app.widapp.data.ProviderKind
import app.widapp.spec.Samples
import app.widapp.spec.WidgetSize
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/**
 * Background generation end to end: a slow OpenAI-compatible gateway (headers first, body much
 * later — the case that used to freeze the UI) → worker → design saved, without any screen open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JobsTest {
    private lateinit var server: HttpServer
    private lateinit var app: WidApp

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val widget = Samples.all().first { it.id == "water" }.json
        val reply = buildJsonObject {
            put("choices", buildJsonArray { add(buildJsonObject { put("message", buildJsonObject { put("role", "assistant"); put("content", JsonPrimitive("Here you go:\n```json\n$widget\n```")) }) }) })
        }.toString().toByteArray()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { ex ->
            ex.sendResponseHeaders(200, 0) // chunked: headers now, body later
            ex.responseBody.use { out ->
                out.write(" ".toByteArray()); out.flush()
                Thread.sleep(1500)
                out.write(reply)
            }
        }
        server.start()
        app.graph.providers.upsert(ProviderConfig("p", "custom", ProviderKind.OPENAI, "Local", "http://127.0.0.1:${server.address.port}/v1", model = "test-model"))
        app.graph.settings.update { it.copy(activeProviderId = "p") }
    }

    @After fun tearDown() { server.stop(0) }

    private fun awaitJob(id: String): String {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val s = app.jobs.get(id)?.status
            if (s == "done" || s == "failed" || s == "cancelled") return s
            Thread.sleep(100)
        }
        return app.jobs.get(id)?.status ?: "missing"
    }

    @Test fun surpriseFinishesInBackground() {
        val before = app.graph.designs.designs.size
        val id = app.jobs.enqueueSurprise(WidgetSize.S2x2)
        assertEquals(app.jobs.get(id)?.error, "done", awaitJob(id))
        val job = app.jobs.get(id)!!
        val design = app.graph.designs.get(job.resultDesignId)
        assertNotNull(design)
        assertEquals("2x2", design!!.spec.size)
        assertEquals(before + 1, app.graph.designs.designs.size)
        assertEquals("test-model", job.model)
    }

    @Test fun refineAddsVersion() {
        val d = app.graph.designs.create(Samples.parsed().first().second, "clock", "m")
        val id = app.jobs.enqueueRefine(d.id, "Make it darker", null)
        assertEquals(app.jobs.get(id)?.error, "done", awaitJob(id))
        assertEquals(2, app.graph.designs.get(d.id)!!.versions.size)
    }

    @Test fun failureIsRecorded() {
        app.graph.providers.upsert(ProviderConfig("p", "custom", ProviderKind.OPENAI, "Local", "http://127.0.0.1:1/v1", model = "m"))
        val id = app.jobs.enqueueCreate("anything", WidgetSize.S4x2, null, null)
        assertEquals("failed", awaitJob(id))
        assertNotNull(app.jobs.get(id)!!.error)
    }
}
