package dev.conclave.fabric

import dev.conclave.core.HealthPercentage
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.portal.TeleportTransition
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Implemented on each native player. Overrides exist only during one synchronous respawn. */
interface ConclaveRespawnTarget {
    fun conclaveRespawnTarget(target: TeleportTransition?)
}

internal data class NativeDestination(
    val level: ServerLevel,
    val position: Vec3,
    val yaw: Float,
    val pitch: Float,
) {
    init {
        require(
            position.x.isFinite() &&
                position.y.isFinite() &&
                position.z.isFinite() &&
                yaw.isFinite() &&
                pitch.isFinite() &&
                pitch in -90f..90f
        )
    }

    fun transition() =
        TeleportTransition(level, position, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING)
}

/** Native player footing, space and readiness checks. Never loads or generates a chunk. */
internal object NativePlayerPlacement {
    fun usable(player: ServerPlayer, destination: NativeDestination): Boolean {
        val level = destination.level
        check(level.server.isSameThread)
        val box =
            EntityTypes.PLAYER.dimensions.scale(player.scale).makeBoundingBox(destination.position)
        if (
            !level.worldBorder.isWithinBounds(box) ||
                box.minY < level.minY ||
                box.maxY >= level.maxY
        )
            return false
        val checked = box.expandTowards(0.0, -0.05, 0.0)
        for (x in
            Math.floorDiv(kotlin.math.floor(checked.minX).toInt(), 16)..Math.floorDiv(
                    kotlin.math.floor(checked.maxX).toInt(),
                    16,
                )) for (z in
            Math.floorDiv(kotlin.math.floor(checked.minZ).toInt(), 16)..Math.floorDiv(
                    kotlin.math.floor(checked.maxZ).toInt(),
                    16,
                )) if (
            !level.areEntitiesActuallyLoadedAndTicking(net.minecraft.world.level.ChunkPos(x, z))
        )
            return false
        if (!level.noCollision(player, box)) return false
        // Native noCollision checks hard collision; ordinary living bodies can push through each
        // other. Recovery deliberately assigns separate free standing positions.
        if (
            level
                .getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java, box) {
                    it !== player && it.isAlive && !it.isSpectator
                }
                .isNotEmpty()
        )
            return false
        // A thin probe immediately below the feet also supports slabs and other partial shapes.
        val footing = AABB(box.minX, box.minY - 0.05, box.minZ, box.maxX, box.minY, box.maxZ)
        if (!level.getBlockCollisions(player, footing).iterator().hasNext()) return false
        for (position in
            BlockPos.betweenClosed(
                BlockPos.containing(checked.minX, checked.minY, checked.minZ),
                BlockPos.containing(checked.maxX, checked.maxY, checked.maxZ),
            )) {
            val state = level.getBlockState(position)
            if (!state.fluidState.isEmpty || hazardous.any { state.`is`(it) }) return false
            if (
                (state.`is`(Blocks.CAMPFIRE) || state.`is`(Blocks.SOUL_CAMPFIRE)) &&
                    state.getValue(BlockStateProperties.LIT)
            )
                return false
        }
        return true
    }

    private val hazardous =
        listOf(
            Blocks.FIRE,
            Blocks.SOUL_FIRE,
            Blocks.CACTUS,
            Blocks.MAGMA_BLOCK,
            Blocks.SWEET_BERRY_BUSH,
            Blocks.WITHER_ROSE,
            Blocks.POWDER_SNOW,
            Blocks.POINTED_DRIPSTONE,
            Blocks.NETHER_PORTAL,
            Blocks.END_PORTAL,
        )
}

/**
 * Executes only after the owning lifecycle has authorized revival or recovery. Vanilla death has
 * already run. Its death respawn path preserves gamerule semantics without dropping anything a
 * second time. The destination override avoids normal spawn searching and anchor consumption.
 */
internal object NativeRespawn {
    fun revive(
        player: ServerPlayer,
        destination: NativeDestination,
        health: HealthPercentage,
    ): ServerPlayer? {
        val server = player.level().server
        check(server.isSameThread)
        if (
            server.playerList.getPlayer(player.uuid) !== player ||
                player.isAlive ||
                player.hasDisconnected()
        )
            return null
        if (!NativePlayerPlacement.usable(player, destination)) return null
        val target = player as ConclaveRespawnTarget
        target.conclaveRespawnTarget(destination.transition())
        val replacement =
            try {
                server.playerList.respawn(player, false, Entity.RemovalReason.KILLED)
            } finally {
                target.conclaveRespawnTarget(null)
            }
        // These are the connection updates normally performed by handleClientCommand, after
        // PlayerList has replaced the entity. Retain vanilla's client world-loading protection.
        replacement.connection.player = replacement
        replacement.connection.resetPosition()
        (replacement.connection as dev.conclave.fabric.mixin.RespawnConnectionAccess)
            .conclaveRestartClientLoadTimer()
        // A positive authored fraction must remain positive after conversion to native float.
        replacement.health =
            health
                .of(replacement.maxHealth.toDouble())
                .toFloat()
                .coerceIn(Float.MIN_VALUE, replacement.maxHealth)
        replacement.invulnerableTime = 0
        replacement.fallDistance = 0.0
        return replacement
    }
}
