package dev.conclave.core

import kotlin.test.*
import org.junit.jupiter.api.Test

class ConfigSchemaTest {
    private data class Input(val area: String, val duration: SimulationDuration, val count: Long)

    private val schema =
        ConfigRecordBuilder().run {
            val area = required("area", ConfigSchemas.identifier("Bound capture area"))
            val duration = required("duration", ConfigSchemas.duration("Positive capture duration"))
            val count =
                defaulted(
                    "players_required",
                    ConfigSchemas.integer("Required distinct players", 1, 64),
                    1L,
                )
            build(
                "Capture configuration",
                { Input(it[area], it[duration], it[count]) },
                { mapOf(area to it.area, duration to it.duration, count to it.count) },
            )
        }

    private fun yaml(text: String) =
        assertIs<Validation.Valid<YamlValue.Mapping>>(
                YamlDocumentReader().read(SourceDocument("config.yaml", text))
            )
            .value

    @Test
    fun `event contracts retain their declared choices when provider collections change`() {
        val options = mutableSetOf("allowed")
        val contract =
            EventContract(mapOf("reason" to EventField(EventValueKind.STRING, choices = options)))
        options.clear()
        options += "forged"
        EventPayload(contract, mapOf("reason" to EventDatum.Text("allowed")))
        assertFailsWith<IllegalArgumentException> {
            EventPayload(contract, mapOf("reason" to EventDatum.Text("forged")))
        }
    }

    @Test
    fun `description and decoding share fields defaults and constraints`() {
        val value =
            assertIs<Validation.Valid<Input>>(schema.validate(yaml("area: north\nduration: 10s")))
                .value
        assertEquals(Input("north", SimulationDuration(200), 1), value)
        assertEquals(setOf("area", "duration"), schema.description.required)
        assertEquals("1", schema.description.properties.getValue("players_required").defaultJson)
        assertEquals("missing_field", diagnostic(schema.validate(yaml("area: north"))).code)
        assertEquals(
            "number_range",
            diagnostic(schema.validate(yaml("area: north\nduration: 1s\nplayers_required: 0")))
                .code,
        )
        assertEquals(
            "unknown_field",
            diagnostic(schema.validate(yaml("area: north\nduration: 1s\nextra: true"))).code,
        )
    }

    @Test
    fun `canonical encoding materializes defaults and round trips`() {
        val value = Input("north", SimulationDuration(21), 3)
        assertEquals(value, schema.decode(yaml(schema.encode(value))))
        val description = yaml(schema.description.json())
        assertIs<YamlValue.Mapping>(description.entries.getValue("properties"))
    }

    @Test
    fun `field handles cannot be confused across schemas`() {
        val builder = ConfigRecordBuilder()
        builder.required("id", ConfigSchemas.identifier("ID"))
        assertFails { builder.required("id", ConfigSchemas.flag("Flag")) }
    }
}
