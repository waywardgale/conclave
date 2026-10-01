package dev.conclave.fabric

import dev.conclave.core.*
import java.math.RoundingMode
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.HitResult

/**
 * Pinned location bindings with generations for actual block replacements, including break/rebuild.
 */
internal class NativeBlockTargets(arena: ArenaDefinition, definition: EncounterDefinition) {
    private class Cell(val position: BlockPos, val names: List<String>) {
        var generation = UUID.randomUUID()
        var block: Block? = null
    }

    private val dimension = arena.dimension
    private val cells =
        definition
            .spatialReferences()
            .locations
            .sorted()
            .groupBy { name ->
                val placement =
                    arena.locations
                        .getValue(arena.encounters.getValue(definition.id).location(name))
                        .placement
                        .position
                BlockPos(
                    placement.x.setScale(0, RoundingMode.FLOOR).intValueExact(),
                    placement.y.setScale(0, RoundingMode.FLOOR).intValueExact(),
                    placement.z.setScale(0, RoundingMode.FLOOR).intValueExact(),
                )
            }
            .mapValues { (position, names) -> Cell(position, names) }
    private val named = cells.values.flatMap { cell -> cell.names.map { it to cell } }.toMap()

    fun changed(level: ServerLevel, position: BlockPos, before: BlockState, after: BlockState) {
        if (level.dimension().identifier().toString() != dimension || before.block === after.block)
            return
        cells[position]?.let {
            it.generation = UUID.randomUUID()
            it.block = after.block
        }
    }

    fun handles(level: ServerLevel, position: BlockPos): List<TargetHandle> {
        if (level.dimension().identifier().toString() != dimension || !level.isLoaded(position))
            return emptyList()
        val cell = cells[position] ?: return emptyList()
        val state = level.getBlockState(position)
        if (cell.block !== state.block) {
            cell.block = state.block
            cell.generation = UUID.randomUUID()
        }
        if (state.isAir) return emptyList()
        return cell.names.map {
            TargetHandle(TargetReference(TargetKind.BLOCK, it), cell.generation)
        }
    }

    fun valid(player: ServerPlayer, target: TargetHandle, maximumReach: Double?): Boolean {
        if (target.reference.kind != TargetKind.BLOCK) return false
        val cell = named[target.reference.id] ?: return false
        return handles(player.level(), cell.position).any { it == target } &&
            aimed(player, cell.position, maximumReach)
    }

    companion object {
        fun physical(level: ServerLevel, position: BlockPos): UUID =
            UUID.nameUUIDFromBytes(
                "${level.dimension().identifier()}:${position.x},${position.y},${position.z}"
                    .toByteArray(Charsets.UTF_8)
            )

        /** Native outline targeting and reach; inspecting a ray never loads missing chunks. */
        fun aimed(player: ServerPlayer, position: BlockPos, maximumReach: Double? = null): Boolean {
            if (
                !player.isAlive ||
                    player.isRemoved ||
                    player.isSpectator ||
                    !player.connection.isAcceptingMessages
            )
                return false
            val reach =
                minOf(player.blockInteractionRange(), maximumReach ?: Double.POSITIVE_INFINITY)
            if (
                !reach.isFinite() ||
                    reach <= 0 ||
                    !player.isWithinBlockInteractionRange(position, 0.0)
            )
                return false
            val eye = player.eyePosition
            // Stop just beyond the target cell, so an extreme reach attribute cannot create an
            // unbounded ray.
            val distance = eye.distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(position)) + 1.0
            val end = eye.add(player.lookAngle.scale(minOf(reach, distance)))
            val minX = Math.floorDiv(kotlin.math.floor(minOf(eye.x, end.x)).toInt(), 16)
            val maxX = Math.floorDiv(kotlin.math.floor(maxOf(eye.x, end.x)).toInt(), 16)
            val minZ = Math.floorDiv(kotlin.math.floor(minOf(eye.z, end.z)).toInt(), 16)
            val maxZ = Math.floorDiv(kotlin.math.floor(maxOf(eye.z, end.z)).toInt(), 16)
            // ASVS 2.3.2: bound geometric work even for externally modified reach attributes.
            if ((maxX.toLong() - minX + 1) * (maxZ.toLong() - minZ + 1) > 64 || distance > 128)
                return false
            val level = player.level()
            for (x in minX..maxX) for (z in minZ..maxZ) if (
                !level.areEntitiesActuallyLoadedAndTicking(ChunkPos(x, z))
            )
                return false
            val hit =
                level.clip(
                    ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)
                )
            return hit.type == HitResult.Type.BLOCK &&
                hit.blockPos == position &&
                hit.location.distanceToSqr(eye) <= reach * reach + 0.000001
        }
    }
}
