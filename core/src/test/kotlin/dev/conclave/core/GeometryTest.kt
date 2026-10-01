package dev.conclave.core

import java.math.BigDecimal
import kotlin.test.*
import org.junit.jupiter.api.Test

class GeometryTest {
    private fun number(value: String) = BigDecimal(value)

    private fun body(x: Double, y: Double, z: Double, xx: Double, yy: Double, zz: Double) =
        BodyBounds(Position(x, y, z), Position(xx, yy, zz))

    private fun box(
        x: Double = 0.0,
        y: Double = 0.0,
        z: Double = 0.0,
        width: String = "20",
        depth: String = "20",
        height: String = "10",
        rotation: String = "0",
    ) =
        Geometry.Box(
            Position(x, y, z),
            number(width),
            number(depth),
            number(height),
            number(rotation),
        )

    @Test
    fun `composite body tests preserve holes even when every corner is inside`() =
        GeometryEngine(500, 2_000_000).use { engine ->
            val area =
                Geometry.Composite(
                    listOf(box()),
                    listOf(Geometry.Sphere(Position(0.0, 2.0, 0.0), number("1"))),
                )
            val enclosing = body(-2.0, 0.0, -2.0, 2.0, 4.0, 2.0)
            assertTrue(enclosing.corners().all(area::contains))
            assertFalse(engine.contains(area, enclosing))
            assertTrue(engine.overlaps(area, enclosing))
            assertFalse(engine.overlaps(area, body(-0.1, 1.9, -0.1, 0.1, 2.1, 0.1)))
            assertFalse(area.contains(Position(1.0, 2.0, 0.0)))
            assertTrue(area.contains(Position(1.00000001, 2.0, 0.0)))
        }

    @Test
    fun `union containment spans pieces and respects a zero-width excluded seam`() =
        GeometryEngine(500).use { engine ->
            val left = box(-1.0, width = "2", depth = "4", height = "4")
            val right = box(1.0, width = "2", depth = "4", height = "4")
            val union = Geometry.Composite(listOf(left, right))
            val joined = body(-1.0, 1.0, -1.0, 1.0, 3.0, 1.0)
            assertTrue(engine.contains(union, joined))
            val slit =
                Geometry.Composite(listOf(box(width = "0.000000001", depth = "4", height = "4")))
            assertFalse(
                engine.contains(Geometry.Composite(listOf(left, right), listOf(slit)), joined)
            )
        }

    @Test
    fun `outer tangency counts while exclusion tangency does not`() =
        GeometryEngine(500).use { engine ->
            val sphere = Geometry.Sphere(Position.ZERO, number("1"))
            val tangent = body(1.0, 0.0, 0.0, 2.0, 1.0, 1.0)
            assertTrue(engine.overlaps(sphere, tangent))
            val excluded =
                Geometry.Composite(
                    listOf(sphere),
                    listOf(box(1.5, -1.0, 0.0, width = "1", depth = "4", height = "4")),
                )
            assertFalse(engine.overlaps(excluded, tangent))
            assertTrue(engine.intersects(box(), box(20.0)))
            assertFalse(engine.intersects(box(), box(20.000001)))
        }

    @Test
    fun `rotated boxes use the same exact coefficients in points and volume queries`() =
        GeometryEngine(500).use { engine ->
            val rotated = box(width = "2", depth = "6", height = "3", rotation = "90")
            assertTrue(rotated.contains(Position(2.0, 1.0, 0.0)))
            assertFalse(rotated.contains(Position(0.0, 1.0, 2.0)))
            assertTrue(engine.contains(rotated, body(-3.0, 0.0, -1.0, 3.0, 3.0, 1.0)))
            val diagonal = box(width = "2", depth = "2", height = "2", rotation = "45")
            assertFalse(engine.overlaps(diagonal, body(1.2, 0.5, 1.2, 1.3, 1.5, 1.3)))
            assertTrue(engine.overlaps(diagonal, body(1.0, 0.5, 0.0, 1.4, 1.5, 0.2)))
            assertTrue(engine.contains(box(), diagonal))
        }

    @Test
    fun `nonempty means volume and not only surviving boundary points`() =
        GeometryEngine(500).use { engine ->
            val cube = box(width = "2", depth = "2", height = "2")
            val removed = Geometry.Composite(listOf(cube), listOf(cube))
            assertFalse(engine.hasVolume(removed))
            assertFalse(engine.overlaps(removed, cube.bounds))
            assertTrue(
                engine.hasVolume(
                    Geometry.Composite(
                        listOf(cube),
                        listOf(Geometry.Sphere(Position(0.0, 1.0, 0.0), number("0.5"))),
                    )
                )
            )
        }

    @Test
    fun `membership checks native dimension and pose without changing the geometry`() =
        GeometryEngine().use { engine ->
            val shape = box(width = "2", depth = "2", height = "1")
            val native =
                SpatialBody(
                    "minecraft:overworld",
                    Position.ZERO,
                    body(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
                )
            assertTrue(
                engine.member(NamedArea("floor", null, shape), "minecraft:overworld", native)
            )
            assertFalse(
                engine.member(NamedArea("floor", null, shape), "minecraft:the_nether", native)
            )
            assertFalse(
                engine.member(
                    NamedArea("floor", null, shape, AreaMembership.CONTAINED),
                    "minecraft:overworld",
                    native,
                )
            )
            assertTrue(
                engine.member(
                    NamedArea("floor", null, shape, AreaMembership.CONTAINED),
                    "minecraft:overworld",
                    native.copy(bounds = body(-0.3, 0.0, -0.3, 0.3, 0.6, 0.3)),
                )
            )
        }

    private val arena =
        """
        schema: 1
        arena:
          id: hall
          dimension: minecraft:overworld
          boundary:
            type: box
            position: {x: 0, y: 60, z: 0}
            width: 40
            depth: 40
            height: 20
          locations:
            - id: origin
              position: {x: 0, y: 64, z: 0}
              facing: {yaw: 90}
          areas:
            - id: plate
              type: box
              location: origin
              offset: {x: 2, y: 0, z: 0}
              width: 2
              depth: 6
              height: 3
        """
            .trimIndent()

    private fun compile(text: String) =
        CatalogCompiler().compile(listOf(SourceDocument("arena.yaml", text)))

    @Test
    fun `arena resolves rotated locations and canonicalizes numeric spellings`() {
        val result = assertIs<Validation.Valid<CompiledCatalog>>(compile(arena)).value
        val resolved = result.arenas.getValue(DefinitionId("local", "hall"))
        assertTrue(resolved.areas.getValue("plate").geometry.contains(Position(2.0, 65.0, 2.0)))
        assertEquals(
            result.revision,
            assertIs<Validation.Valid<CompiledCatalog>>(
                    compile(arena.replace("width: 2\n", "width: 2.0\n"))
                )
                .value
                .revision,
        )
        val problem =
            assertIs<Validation.Invalid>(compile(arena.replace("width: 2\n", "width: 100\n")))
        assertEquals("area_outside", problem.diagnostics.single().code)
    }

    @Test
    fun `arena rejects cycles and irrelevant fields with source diagnostics`() {
        val cycle =
            arena.substringBefore("  areas:") +
                "  areas:\n    - id: a\n      type: composite\n      include: [b]\n    - id: b\n      type: composite\n      include: [a]\n"
        assertEquals(
            "area_cycle",
            assertIs<Validation.Invalid>(compile(cycle)).diagnostics.single().code,
        )
        val invalid =
            assertIs<Validation.Invalid>(
                compile(arena.replace("type: box\n      location", "type: sphere\n      location"))
            )
        assertTrue(invalid.diagnostics.isNotEmpty())
    }

    @Test
    fun `declared encounter pairings require every spatial reference including filters`() {
        val encounter =
            SourceDocument(
                "encounter.yaml",
                """
                schema: 1
                encounter:
                  id: ritual
                  start: first
                  phases:
                    - id: first
                      objectives:
                        - id: plate
                          type: capture
                          area: north
                          duration: 1s
                          players: {where: {not: {in_area: forbidden}}}
                      success: {complete: true}
                """
                    .trimIndent(),
            )
        val pair =
            arena +
                "\n  encounters:\n    - encounter: ritual\n      bindings:\n        areas: {north: plate, forbidden: plate}\n"
        assertIs<Validation.Valid<CompiledCatalog>>(
            CatalogCompiler().compile(listOf(SourceDocument("arena.yaml", pair), encounter))
        )
        val invalid =
            assertIs<Validation.Invalid>(
                CatalogCompiler()
                    .compile(
                        listOf(
                            SourceDocument("arena.yaml", pair.replace(", forbidden: plate", "")),
                            encounter,
                        )
                    )
            )
        assertEquals("unknown_area_binding", invalid.diagnostics.single().code)
    }
}
