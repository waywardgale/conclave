package dev.conclave.core

import com.microsoft.z3.ArithExpr
import com.microsoft.z3.BoolExpr
import com.microsoft.z3.Context
import com.microsoft.z3.RealSort
import com.microsoft.z3.Status
import java.math.BigDecimal

class GeometryCapacityException(message: String) : RuntimeException(message)

/**
 * Exact set queries over the resolved finite coefficients. Own and close on one worker/server
 * thread.
 */
class GeometryEngine(
    private val queryTimeoutMillis: Int = 20,
    private val queryWorkLimit: Int = 200_000,
) : AutoCloseable {
    private val owner = Thread.currentThread()
    private var closed = false
    private var context: Context? = null
    private val cache =
        object : LinkedHashMap<Query, Boolean>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Query, Boolean>) =
                size > 1024
        }

    init {
        require(queryTimeoutMillis in 1..1000 && queryWorkLimit > 0)
    }

    fun member(area: NamedArea, dimension: String, body: SpatialBody): Boolean {
        checkAccess()
        if (dimension != body.dimension) return false
        return when (area.membership) {
            AreaMembership.POSITION -> area.geometry.contains(body.position)
            AreaMembership.OVERLAP -> overlaps(area.geometry, body.bounds)
            AreaMembership.CONTAINED -> contains(area.geometry, body.bounds)
        }
    }

    fun overlaps(area: Geometry, body: BodyBounds): Boolean {
        checkAccess()
        return when (area.classify(body)) {
            VolumeRelation.OUTSIDE -> false
            VolumeRelation.INSIDE -> true
            VolumeRelation.MIXED ->
                if (area !is Geometry.Composite) true else query(Query(area, body, false))
        }
    }

    fun contains(area: Geometry, body: BodyBounds): Boolean {
        checkAccess()
        return when (area.classify(body)) {
            VolumeRelation.OUTSIDE -> false
            VolumeRelation.INSIDE -> true
            VolumeRelation.MIXED ->
                if (area !is Geometry.Composite) false else !query(Query(area, body, true))
        }
    }

    fun intersects(first: Geometry, second: Geometry): Boolean {
        checkAccess()
        if (!first.bounds.intersects(second.bounds)) return false
        return query(Query(first, second, false))
    }

    fun contains(outer: Geometry, inner: Geometry): Boolean {
        checkAccess()
        if (outer === inner || outer.classify(inner.bounds) == VolumeRelation.INSIDE) return true
        return !query(Query(outer, inner, true))
    }

    fun hasVolume(area: Geometry): Boolean {
        checkAccess()
        if (area !is Geometry.Composite) return true
        return query(Query(area, null, false))
    }

    private data class Query(val area: Geometry, val body: Any?, val outside: Boolean)

    private fun query(query: Query): Boolean {
        cache[query]?.let {
            return it
        }
        val ctx = context ?: Context().also { context = it }
        val x = ctx.mkRealConst("x")
        val y = ctx.mkRealConst("y")
        val z = ctx.mkRealConst("z")
        fun number(n: BigDecimal) = ctx.mkReal(n.toPlainString())
        fun square(n: ArithExpr<RealSort>) = ctx.mkMul(n, n)
        val surfaces = mutableListOf<BoolExpr>()
        fun below(left: ArithExpr<RealSort>, right: ArithExpr<RealSort>): BoolExpr {
            if (query.body == null) surfaces += ctx.mkNot(ctx.mkEq(left, right))
            return ctx.mkLe(left, right)
        }
        fun slab(axis: ArithExpr<RealSort>, low: BigDecimal, high: BigDecimal) =
            ctx.mkAnd(below(number(low), axis), below(axis, number(high)))
        fun bounds(body: BodyBounds): BoolExpr =
            ctx.mkAnd(
                slab(x, body.min.x, body.max.x),
                slab(y, body.min.y, body.max.y),
                slab(z, body.min.z, body.max.z),
            )
        fun shape(area: Geometry): BoolExpr =
            when (area) {
                is Geometry.Box -> {
                    val dx = ctx.mkSub(x, number(area.base.x))
                    val dz = ctx.mkSub(z, number(area.base.z))
                    val localX =
                        ctx.mkAdd(
                            ctx.mkMul(dx, number(area.rotation.cosine)),
                            ctx.mkMul(dz, number(area.rotation.sine)),
                        )
                    val localZ =
                        ctx.mkAdd(
                            ctx.mkMul(dx, number(-area.rotation.sine)),
                            ctx.mkMul(dz, number(area.rotation.cosine)),
                        )
                    ctx.mkAnd(
                        slab(
                            localX,
                            -area.halfWidth * area.determinant,
                            area.halfWidth * area.determinant,
                        ),
                        slab(
                            localZ,
                            -area.halfDepth * area.determinant,
                            area.halfDepth * area.determinant,
                        ),
                        slab(y, area.base.y, area.base.y + area.height),
                    )
                }
                is Geometry.Sphere ->
                    below(
                        ctx.mkAdd(
                            square(ctx.mkSub(x, number(area.center.x))),
                            square(ctx.mkSub(y, number(area.center.y))),
                            square(ctx.mkSub(z, number(area.center.z))),
                        ),
                        number(area.radius.square()),
                    )
                is Geometry.Cylinder ->
                    ctx.mkAnd(
                        slab(y, area.base.y, area.base.y + area.height),
                        below(
                            ctx.mkAdd(
                                square(ctx.mkSub(x, number(area.base.x))),
                                square(ctx.mkSub(z, number(area.base.z))),
                            ),
                            number(area.radius.square()),
                        ),
                    )
                is Geometry.Composite ->
                    ctx.mkAnd(
                        ctx.mkOr(*area.include.map { shape(it) }.toTypedArray()),
                        ctx.mkNot(ctx.mkOr(*area.exclude.map { shape(it) }.toTypedArray())),
                    )
            }
        val region = shape(query.area)
        val body =
            when (val b = query.body) {
                null -> ctx.mkTrue()
                is Geometry -> shape(b)
                is BodyBounds -> bounds(b)
                else -> error("Unsupported geometry query")
            }
        val formula =
            ctx.mkAnd(
                body,
                if (query.outside) ctx.mkNot(region) else region,
                *surfaces.toTypedArray(),
            )
        val solver = ctx.mkSolver("QF_NRA")
        // ASVS 15.2.2: bounded exact work. Unknown is an engine capacity error, never a guessed
        // membership.
        solver.setParameters(
            ctx.mkParams().apply {
                add("timeout", queryTimeoutMillis)
                add("rlimit", queryWorkLimit)
            }
        )
        solver.add(formula)
        val result =
            when (solver.check()) {
                Status.SATISFIABLE -> true
                Status.UNSATISFIABLE -> false
                else ->
                    throw GeometryCapacityException(
                        "Exact area query exceeded its work budget; simplify the composite geometry"
                    )
            }
        cache[query] = result
        return result
    }

    private fun checkAccess() {
        check(Thread.currentThread() === owner && !closed) {
            "Geometry engine is closed or used by another thread"
        }
    }

    override fun close() {
        checkAccess()
        closed = true
        cache.clear()
        // ASVS 1.4.3: native expressions never escape their owning context.
        context?.close()
        context = null
    }
}
