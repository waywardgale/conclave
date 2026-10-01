package dev.conclave.core

import java.math.BigDecimal

data class LocationPlacement(
    val position: Position,
    val yaw: BigDecimal = BigDecimal.ZERO,
    val pitch: BigDecimal = BigDecimal.ZERO,
) {
    internal fun canonical() =
        "${position.canonical()};${HorizontalRotation(yaw).degrees.normal()};${pitch.normal()}"
}

data class NamedLocation(val id: String, val name: String?, val placement: LocationPlacement)

data class WorldLocationDefinition(
    val id: DefinitionId,
    val name: String?,
    val dimension: String,
    val placement: LocationPlacement,
)

class ArenaEncounter(
    val encounter: DefinitionId,
    areas: Map<String, String>,
    locations: Map<String, String>,
    val source: SourceLocation = SourceLocation("<pairing>"),
) {
    val areas: Map<String, String> = java.util.Map.copyOf(areas)
    val locations: Map<String, String> = java.util.Map.copyOf(locations)

    fun area(logical: String) = areas[logical] ?: logical

    fun location(logical: String) = locations[logical] ?: logical
}

class ArenaDefinition(
    val id: DefinitionId,
    val name: String?,
    val dimension: String,
    val boundary: Geometry,
    locations: List<NamedLocation>,
    areas: List<NamedArea>,
    encounters: List<ArenaEncounter>,
) {
    val locations: Map<String, NamedLocation> =
        java.util.Map.copyOf(locations.associateBy { it.id })
    val areas: Map<String, NamedArea> = java.util.Map.copyOf(areas.associateBy { it.id })
    val encounters: Map<DefinitionId, ArenaEncounter> =
        java.util.Map.copyOf(encounters.associateBy { it.encounter })

    init {
        require(
            this.locations.size == locations.size &&
                this.areas.size == areas.size &&
                this.encounters.size == encounters.size
        )
    }

    internal fun canonical() = buildString {
        append(jsonString(id.toString()))
        append(jsonString(name ?: ""))
        append(jsonString(dimension))
        append(boundary.canonical())
        this@ArenaDefinition.locations.toSortedMap().forEach { (id, value) ->
            append("location:$id:")
            append(jsonString(value.name ?: ""))
            append(value.placement.canonical())
        }
        this@ArenaDefinition.areas.toSortedMap().forEach { (id, value) ->
            append("area:$id:")
            append(jsonString(value.name ?: ""))
            append(value.membership.name)
            append(value.geometry.canonical())
        }
        this@ArenaDefinition.encounters.toSortedMap().forEach { (id, value) ->
            append("encounter:$id:")
            append(value.areas.toSortedMap())
            append(value.locations.toSortedMap())
        }
    }
}

/**
 * Typed spatial dependencies are supplied by each installed capability, including configured
 * branches.
 */
class SpatialReferences(areas: Set<String> = emptySet(), locations: Set<String> = emptySet()) {
    val areas: Set<String> = java.util.Set.copyOf(areas)
    val locations: Set<String> = java.util.Set.copyOf(locations)

    operator fun plus(other: SpatialReferences) =
        SpatialReferences(areas + other.areas, locations + other.locations)

    companion object {
        val EMPTY = SpatialReferences()
    }
}

internal class ArenaCompiler {
    private val number =
        ConfigSchemas.decimal(
            "Finite coordinate in blocks",
            BigDecimal("-30000000"),
            BigDecimal("30000000"),
        )
    private val positive =
        ConfigSchemas.decimal(
            "Positive size in blocks",
            BigDecimal("0.000000001"),
            BigDecimal("30000000"),
        )
    private val angle =
        ConfigSchemas.decimal("Angle in degrees", BigDecimal("-1000000"), BigDecimal("1000000"))
    private val local = ConfigSchemas.identifier("Arena-local spatial ID")

    fun location(node: YamlValue.Mapping, namespace: String): WorldLocationDefinition {
        val fields = Fields(node)
        val id = local.decode(fields.required("id"))
        val name = fields.optional("name")?.let { ConfigSchemas.text("Display name").decode(it) }
        val dimension = NativeReferenceSchema.decode(fields.required("dimension"))
        val placement = placement(fields)
        fields.finish()
        return WorldLocationDefinition(DefinitionId(namespace, id), name, dimension, placement)
    }

    fun arena(
        node: YamlValue.Mapping,
        namespace: String,
        geometry: GeometryEngine,
    ): ArenaDefinition {
        val fields = Fields(node)
        val id = local.decode(fields.required("id"))
        val name = fields.optional("name")?.let { ConfigSchemas.text("Display name").decode(it) }
        val dimension = NativeReferenceSchema.decode(fields.required("dimension"))
        val locations =
            list(fields.optional("locations")).map { value ->
                val entry = Fields(value.mapping())
                val result =
                    NamedLocation(
                        local.decode(entry.required("id")),
                        entry.optional("name")?.let {
                            ConfigSchemas.text("Display name").decode(it)
                        },
                        placement(entry),
                    )
                entry.finish()
                result
            }
        if (locations.map { it.id }.distinct().size != locations.size)
            invalid("duplicate_location", "Location IDs must be unique in this arena", node.source)
        val locationMap = locations.associateBy { it.id }
        val rawAreas = linkedMapOf<String, YamlValue.Mapping>()
        for (value in list(fields.optional("areas"))) {
            val mapping = value.mapping()
            val key = local.decode(Fields(mapping).required("id"))
            if (rawAreas.putIfAbsent(key, mapping) != null)
                invalid("duplicate_area", "Area IDs must be unique in this arena", value.source)
        }
        val areas = linkedMapOf<String, NamedArea>()
        val visiting = mutableSetOf<String>()
        var expanded = 0
        lateinit var resolve: (YamlValue, Int) -> Geometry
        fun named(key: String, at: SourceLocation, depth: Int): NamedArea {
            if (depth > 16) invalid("geometry_depth", "Geometry exceeds 16 composition levels", at)
            if (!visiting.add(key))
                invalid("area_cycle", "Area references form a cycle at '$key'", at)
            try {
                val raw =
                    rawAreas[key]
                        ?: invalid("unknown_area", "Arena area '$key' is not declared", at)
                val metadata = Fields(raw)
                metadata.required("id")
                val display =
                    metadata.optional("name")?.let { ConfigSchemas.text("Display name").decode(it) }
                val mode =
                    metadata.optional("membership")?.let {
                        ConfigSchemas.choice(
                                "Area membership",
                                AreaMembership.entries.associateBy { it.name.lowercase() },
                            )
                            .decode(it)
                    } ?: AreaMembership.POSITION
                val shape =
                    resolve(
                        YamlValue.Mapping(
                            raw.entries - setOf("id", "name", "membership"),
                            raw.source,
                        ),
                        depth,
                    )
                return NamedArea(key, display, shape, mode).also { areas[key] = it }
            } finally {
                visiting.remove(key)
            }
        }
        resolve = { value, depth ->
            if (++expanded > 4096)
                invalid("geometry_size", "Arena exceeds 4096 expanded geometry nodes", value.source)
            if (depth > 16)
                invalid("geometry_depth", "Geometry exceeds 16 composition levels", value.source)
            if (value is YamlValue.Text)
                named(local.decode(value), value.source, depth + 1).geometry
            else {
                val shape = Fields(value.mapping())
                val kind = shape.required("type")
                val result =
                    if (kind.text() == "composite") {
                        val includeNode = shape.required("include")
                        val include = list(includeNode).map { resolve(it, depth + 1) }
                        if (include.isEmpty())
                            invalid(
                                "empty_geometry",
                                "A composite needs at least one included region",
                                includeNode.source,
                            )
                        val exclude = list(shape.optional("exclude")).map { resolve(it, depth + 1) }
                        try {
                            Geometry.Composite(include, exclude)
                        } catch (_: IllegalArgumentException) {
                            invalid(
                                "geometry_size",
                                "Geometry exceeds 256 expanded nodes or 16 composition levels",
                                value.source,
                            )
                        }
                    } else {
                        val position = shape.optional("position")
                        val location = shape.optional("location")
                        if ((position != null) == (location != null))
                            invalid(
                                "geometry_placement",
                                "Supply exactly one of position and location",
                                value.source,
                            )
                        val origin = location?.let {
                            locationMap[local.decode(it)]
                                ?: invalid(
                                    "unknown_location",
                                    "Arena location is not declared",
                                    it.source,
                                )
                        }
                        val offsetNode = shape.optional("offset")
                        if (offsetNode != null && origin == null)
                            invalid(
                                "geometry_offset",
                                "offset requires location",
                                offsetNode.source,
                            )
                        val offset = offsetNode?.let(::position) ?: Position.ZERO
                        val base =
                            if (origin == null) position(checkNotNull(position))
                            else
                                origin.placement.position +
                                    HorizontalRotation(origin.placement.yaw).apply(offset)
                        try {
                            when (kind.text()) {
                                "box" ->
                                    Geometry.Box(
                                        base,
                                        positive.decode(shape.required("width")),
                                        positive.decode(shape.required("depth")),
                                        positive.decode(shape.required("height")),
                                        (origin?.placement?.yaw ?: BigDecimal.ZERO) +
                                            (shape.optional("rotation")?.let { angle.decode(it) }
                                                ?: BigDecimal.ZERO),
                                    )
                                "cylinder" ->
                                    Geometry.Cylinder(
                                        base,
                                        positive.decode(shape.required("radius")),
                                        positive.decode(shape.required("height")),
                                    )
                                "sphere" ->
                                    Geometry.Sphere(base, positive.decode(shape.required("radius")))
                                else ->
                                    invalid(
                                        "geometry_type",
                                        "Choose box, cylinder, sphere, or composite",
                                        kind.source,
                                    )
                            }
                        } catch (_: IllegalArgumentException) {
                            invalid(
                                "geometry_range",
                                "Resolved geometry exceeds the finite coordinate or precision limits",
                                value.source,
                            )
                        }
                    }
                shape.finish()
                result
            }
        }
        for ((key, raw) in rawAreas) if (key !in areas) named(key, raw.source, 0)
        val boundaryNode = fields.required("boundary")
        val boundary = resolve(boundaryNode, 0)
        try {
            if (!geometry.hasVolume(boundary))
                invalid(
                    "empty_boundary",
                    "Arena boundary needs a nonempty volume",
                    boundaryNode.source,
                )
            for (location in locations) if (!boundary.contains(location.placement.position))
                invalid(
                    "location_outside",
                    "Location '${location.id}' is outside the arena boundary",
                    node.source.field("locations"),
                )
            for ((key, area) in areas) if (!geometry.contains(boundary, area.geometry))
                invalid(
                    "area_outside",
                    "Area '$key' extends outside the arena boundary",
                    rawAreas.getValue(key).source,
                )
        } catch (failure: GeometryCapacityException) {
            invalid("geometry_capacity", checkNotNull(failure.message), boundaryNode.source)
        }
        val encounters =
            list(fields.optional("encounters")).map { value ->
                val entry = Fields(value.mapping())
                val encounter =
                    DefinitionReferenceSchema.decode(
                        entry.required("encounter"),
                        SchemaContext(namespace),
                    )
                val binding = entry.optional("bindings")?.mapping()?.let(::Fields)
                fun bindings(key: String): Map<String, String> =
                    binding
                        ?.optional(key)
                        ?.mapping()
                        ?.entries
                        ?.map { (key, value) ->
                            if (!isAuthoredName(key))
                                invalid(
                                    "identifier",
                                    "Binding names must be snake_case",
                                    value.source,
                                )
                            key to local.decode(value)
                        }
                        ?.toMap() ?: emptyMap()
                val result =
                    ArenaEncounter(
                        encounter,
                        bindings("areas"),
                        bindings("locations"),
                        value.source,
                    )
                binding?.finish()
                entry.finish()
                result
            }
        if (encounters.map { it.encounter }.distinct().size != encounters.size)
            invalid(
                "duplicate_pairing",
                "An encounter is attached to this arena more than once",
                node.source.field("encounters"),
            )
        fields.finish()
        return ArenaDefinition(
            DefinitionId(namespace, id),
            name,
            dimension,
            boundary,
            locations,
            areas.values.toList(),
            encounters,
        )
    }

    private fun list(value: YamlValue?): List<YamlValue> =
        value?.sequence()?.also {
            if (it.size > 256) invalid("spatial_count", "Use at most 256 entries", value.source)
        } ?: emptyList()

    private fun position(value: YamlValue): Position {
        val fields = Fields(value.mapping())
        val coordinates =
            listOf("x", "y", "z").map { key ->
                number.decode(fields.required(key)).also {
                    if (it.scale() !in -16..32)
                        invalid(
                            "coordinate_precision",
                            "Coordinates support at most 32 fractional digits",
                            value.source.field(key),
                        )
                }
            }
        fields.finish()
        return Position(coordinates[0], coordinates[1], coordinates[2])
    }

    private fun placement(fields: Fields): LocationPlacement {
        val position = position(fields.required("position"))
        val facing = fields.optional("facing")?.mapping()?.let(::Fields)
        val yaw = facing?.optional("yaw")?.let { angle.decode(it) } ?: BigDecimal.ZERO
        val pitch =
            facing?.optional("pitch")?.let {
                ConfigSchemas.decimal("Vertical facing", BigDecimal(-90), BigDecimal(90)).decode(it)
            } ?: BigDecimal.ZERO
        facing?.finish()
        return LocationPlacement(position, HorizontalRotation(yaw).degrees, pitch)
    }
}
