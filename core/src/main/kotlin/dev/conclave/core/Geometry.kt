package dev.conclave.core

import java.math.BigDecimal

private val two = BigDecimal(2)

internal fun Double.coordinate(): BigDecimal = BigDecimal.valueOf(this).stripTrailingZeros()

internal fun BigDecimal.square(): BigDecimal = multiply(this)

/** Resolved coordinates are finite decimal values. Geometry never adds a membership epsilon. */
data class Position(val x: BigDecimal, val y: BigDecimal, val z: BigDecimal) {
    constructor(
        x: Double,
        y: Double,
        z: Double,
    ) : this(x.coordinate(), y.coordinate(), z.coordinate())

    init {
        require(
            listOf(x, y, z).all { it.abs() <= BigDecimal("60000000") && it.scale() in -16..128 }
        )
    }

    operator fun plus(other: Position) = Position(x + other.x, y + other.y, z + other.z)

    companion object {
        val ZERO = Position(0.0, 0.0, 0.0)
    }
}

data class BodyBounds(val min: Position, val max: Position) {
    init {
        require(min.x <= max.x && min.y <= max.y && min.z <= max.z)
    }

    fun contains(p: Position) =
        p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y && p.z >= min.z && p.z <= max.z

    fun contains(b: BodyBounds) = contains(b.min) && contains(b.max)

    fun intersects(b: BodyBounds) =
        min.x <= b.max.x &&
            max.x >= b.min.x &&
            min.y <= b.max.y &&
            max.y >= b.min.y &&
            min.z <= b.max.z &&
            max.z >= b.min.z

    fun corners(): List<Position> =
        listOf(min.x, max.x).flatMap { x ->
            listOf(min.y, max.y).flatMap { y ->
                listOf(min.z, max.z).map { z -> Position(x, y, z) }
            }
        }

    fun union(b: BodyBounds) =
        BodyBounds(
            Position(min.x.min(b.min.x), min.y.min(b.min.y), min.z.min(b.min.z)),
            Position(max.x.max(b.max.x), max.y.max(b.max.y), max.z.max(b.max.z)),
        )
}

/** Minecraft yaw turns a local forward (+Z) vector toward -X. Cardinal rotations stay exact. */
class HorizontalRotation(degrees: BigDecimal) {
    val degrees: BigDecimal =
        degrees
            .remainder(BigDecimal(360))
            .let { if (it.signum() < 0) it + BigDecimal(360) else it }
            .stripTrailingZeros()
    val cosine: BigDecimal
    val sine: BigDecimal

    init {
        val pair =
            when (this.degrees.toDouble()) {
                0.0 -> 1.0 to 0.0
                90.0 -> 0.0 to 1.0
                180.0 -> -1.0 to 0.0
                270.0 -> 0.0 to -1.0
                else ->
                    StrictMath.toRadians(this.degrees.toDouble()).let {
                        StrictMath.cos(it) to StrictMath.sin(it)
                    }
            }
        cosine = pair.first.coordinate()
        sine = pair.second.coordinate()
    }

    fun apply(p: Position) = Position(p.x * cosine - p.z * sine, p.y, p.x * sine + p.z * cosine)
}

internal enum class VolumeRelation {
    OUTSIDE,
    INSIDE,
    MIXED,
}

sealed class Geometry {
    abstract val bounds: BodyBounds

    abstract fun contains(position: Position): Boolean

    internal abstract fun classify(body: BodyBounds): VolumeRelation

    internal abstract fun canonical(): String

    class Box(
        val base: Position,
        val width: BigDecimal,
        val depth: BigDecimal,
        val height: BigDecimal,
        rotation: BigDecimal = BigDecimal.ZERO,
    ) : Geometry() {
        val rotation = HorizontalRotation(rotation)
        internal val determinant = this.rotation.cosine.square() + this.rotation.sine.square()
        internal val halfWidth = width.divide(two)
        internal val halfDepth = depth.divide(two)

        init {
            dimensions(width, depth, height)
        }

        private val extentX =
            this.rotation.cosine.abs() * halfWidth + this.rotation.sine.abs() * halfDepth
        private val extentZ =
            this.rotation.sine.abs() * halfWidth + this.rotation.cosine.abs() * halfDepth
        override val bounds =
            BodyBounds(
                Position(base.x - extentX, base.y, base.z - extentZ),
                Position(base.x + extentX, base.y + height, base.z + extentZ),
            )

        internal fun localX(p: Position) =
            (p.x - base.x) * rotation.cosine + (p.z - base.z) * rotation.sine

        internal fun localZ(p: Position) =
            -(p.x - base.x) * rotation.sine + (p.z - base.z) * rotation.cosine

        override fun contains(position: Position) =
            position.y >= base.y &&
                position.y <= base.y + height &&
                localX(position).abs() <= halfWidth * determinant &&
                localZ(position).abs() <= halfDepth * determinant

        override fun classify(body: BodyBounds): VolumeRelation {
            if (!bounds.intersects(body)) return VolumeRelation.OUTSIDE
            val corners = body.corners()
            if (corners.all(::contains)) return VolumeRelation.INSIDE
            // Separating-axis test for two upright convex boxes, including shared boundaries.
            for ((coordinates, extent) in
                listOf(
                    corners.map(::localX) to halfWidth * determinant,
                    corners.map(::localZ) to halfDepth * determinant,
                )) {
                if (coordinates.min() > extent || coordinates.max() < -extent)
                    return VolumeRelation.OUTSIDE
            }
            return VolumeRelation.MIXED
        }

        override fun canonical() =
            "box(${base.canonical()},${width.normal()},${depth.normal()},${height.normal()},${rotation.degrees.normal()})"
    }

    class Sphere(val center: Position, val radius: BigDecimal) : Geometry() {
        init {
            dimensions(radius)
        }

        override val bounds =
            BodyBounds(
                Position(center.x - radius, center.y - radius, center.z - radius),
                Position(center.x + radius, center.y + radius, center.z + radius),
            )

        override fun contains(position: Position) =
            (position.x - center.x).square() +
                (position.y - center.y).square() +
                (position.z - center.z).square() <= radius.square()

        override fun classify(body: BodyBounds): VolumeRelation {
            if (
                !bounds.intersects(body) ||
                    distance(center.x, body.min.x, body.max.x) +
                        distance(center.y, body.min.y, body.max.y) +
                        distance(center.z, body.min.z, body.max.z) > radius.square()
            )
                return VolumeRelation.OUTSIDE
            return if (body.corners().all(::contains)) VolumeRelation.INSIDE
            else VolumeRelation.MIXED
        }

        override fun canonical() = "sphere(${center.canonical()},${radius.normal()})"
    }

    class Cylinder(val base: Position, val radius: BigDecimal, val height: BigDecimal) :
        Geometry() {
        init {
            dimensions(radius, height)
        }

        override val bounds =
            BodyBounds(
                Position(base.x - radius, base.y, base.z - radius),
                Position(base.x + radius, base.y + height, base.z + radius),
            )

        override fun contains(position: Position) =
            position.y >= base.y &&
                position.y <= base.y + height &&
                (position.x - base.x).square() + (position.z - base.z).square() <= radius.square()

        override fun classify(body: BodyBounds): VolumeRelation {
            if (
                !bounds.intersects(body) ||
                    distance(base.x, body.min.x, body.max.x) +
                        distance(base.z, body.min.z, body.max.z) > radius.square()
            )
                return VolumeRelation.OUTSIDE
            return if (body.corners().all(::contains)) VolumeRelation.INSIDE
            else VolumeRelation.MIXED
        }

        override fun canonical() =
            "cylinder(${base.canonical()},${radius.normal()},${height.normal()})"
    }

    class Composite(include: List<Geometry>, exclude: List<Geometry> = emptyList()) : Geometry() {
        val include = java.util.List.copyOf(include)
        val exclude = java.util.List.copyOf(exclude)

        init {
            require(include.isNotEmpty())
            require(complexity() <= 256 && depth() <= 16) {
                "Geometry exceeds 256 expanded nodes or 16 composition levels"
            }
        }

        override val bounds = include.map { it.bounds }.reduce(BodyBounds::union)

        override fun contains(position: Position) =
            bounds.contains(position) &&
                include.any { it.contains(position) } &&
                exclude.none { it.contains(position) }

        override fun classify(body: BodyBounds): VolumeRelation {
            if (!bounds.intersects(body)) return VolumeRelation.OUTSIDE
            val included = include.map { it.classify(body) }
            if (included.all { it == VolumeRelation.OUTSIDE }) return VolumeRelation.OUTSIDE
            val excluded = exclude.map { it.classify(body) }
            if (excluded.any { it == VolumeRelation.INSIDE }) return VolumeRelation.OUTSIDE
            if (
                included.any { it == VolumeRelation.INSIDE } &&
                    excluded.all { it == VolumeRelation.OUTSIDE }
            )
                return VolumeRelation.INSIDE
            return VolumeRelation.MIXED
        }

        override fun canonical() =
            "composite(${include.map { it.canonical() }.distinct().sorted()},${exclude.map { it.canonical() }.distinct().sorted()})"
    }

    internal fun complexity(): Int =
        if (this is Composite) 1 + (include + exclude).sumOf { it.complexity() } else 1

    internal fun depth(): Int =
        if (this is Composite) 1 + (include + exclude).maxOf { it.depth() } else 0
}

private fun dimensions(vararg values: BigDecimal) {
    require(values.all { it.signum() > 0 && it <= BigDecimal("60000000") && it.scale() in -16..32 })
}

private fun distance(point: BigDecimal, min: BigDecimal, max: BigDecimal): BigDecimal =
    when {
        point < min -> (min - point).square()
        point > max -> (point - max).square()
        else -> BigDecimal.ZERO
    }

internal fun BigDecimal.normal(): String = stripTrailingZeros().toPlainString()

internal fun Position.canonical() = "${x.normal()},${y.normal()},${z.normal()}"

enum class AreaMembership {
    POSITION,
    OVERLAP,
    CONTAINED,
}

data class NamedArea(
    val id: String,
    val name: String?,
    val geometry: Geometry,
    val membership: AreaMembership = AreaMembership.POSITION,
)

/** A spatial observation is dimension-specific; camera positions never participate. */
data class SpatialBody(val dimension: String, val position: Position, val bounds: BodyBounds)
