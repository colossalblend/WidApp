package app.widapp

import app.widapp.spec.Evaluator
import app.widapp.spec.Samples
import app.widapp.spec.SpecParser
import app.widapp.spec.StateOps
import app.widapp.spec.WAction
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpecTest {
    private fun ev(vararg pairs: Pair<String, Any>) = Evaluator(pairs.associate { (k, v) ->
        k to when (v) { is Boolean -> JsonPrimitive(v); is Number -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) }
    }, null)

    @Test fun templates() {
        val e = ev("count" to 3, "on" to true, "name" to "Ann")
        assertEquals("3 cups", e.template("{count} cups"))
        assertEquals("Hi Ann", e.template("Hi {name}"))
        assertEquals("ON", e.template("{on ? 'ON' : 'OFF'}"))
        assertEquals("few", e.template("{count >= 5 ? 'many' : 'few'}"))
        assertEquals("@primary", e.template("{on ? '@primary' : '@surface'}"))
        assertEquals("3", e.template("{count >= 8 ? 'Done' : count}"))
    }

    @Test fun conditions() {
        val e = ev("page" to 1, "done" to false, "mood" to "calm")
        assertTrue(e.cond("page == 1"))
        assertFalse(e.cond("page != 1"))
        assertTrue(e.cond("!done"))
        assertTrue(e.cond("mood == 'calm' && page >= 1"))
        assertTrue(e.cond("done || page > 0"))
        assertFalse(e.cond("page < 1"))
    }

    @Test fun stateOps() {
        var s = mapOf("c" to JsonPrimitive(0))
        s = StateOps.apply(s, listOf(WAction("inc", "c", max = 2.0)), null)
        s = StateOps.apply(s, listOf(WAction("inc", "c", max = 2.0), WAction("inc", "c", max = 2.0)), null)
        assertEquals(2.0, s["c"]!!.content.toDouble(), 0.0)
        s = StateOps.apply(s, listOf(WAction("cycle", "p", count = 3)), null)
        s = StateOps.apply(s, listOf(WAction("prev", "p", count = 3)), null)
        s = StateOps.apply(s, listOf(WAction("prev", "p", count = 3)), null)
        assertEquals(2, s["p"]!!.content.toDouble().toInt())
        s = StateOps.apply(s, listOf(WAction("toggle", "t")), null)
        assertEquals("true", s["t"]!!.content)
        s = StateOps.apply(s, listOf(WAction("reset")), null)
        assertTrue(s.isEmpty())
    }

    @Test fun samplesParse() {
        val all = Samples.all()
        val parsed = Samples.parsed()
        assertEquals(all.size, parsed.size)
    }

    @Test fun extractsJsonFromChattyReply() {
        val reply = "Sure! Here is your widget:\n```json\n{\"name\":\"X\",\"size\":\"2x2\",\"root\":{\"type\":\"col\",\"children\":[{\"type\":\"label\",\"text\":\"a}b\"}]}}\n```\nEnjoy."
        val spec = SpecParser.parse(reply)
        assertEquals("column", spec.root.type)
        assertEquals("text", spec.root.children[0].type)
        assertEquals("a}b", spec.root.children[0].text)
    }
}
