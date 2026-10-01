package dev.conclave.fabric

import dev.conclave.core.ChunkCoordinate
import dev.conclave.core.ChunkFootprint
import java.util.UUID
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ChunkLevel
import net.minecraft.server.level.FullChunkStatus
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos

/**
 * Reference-counted Conclave tickets; vanilla forced chunks and other ticket types remain
 * untouched.
 */
internal class NativeChunkClaims(
    private val server: MinecraftServer,
    private val maximumRetained: Int = 16_384,
) {
    private data class Key(val dimension: String, val chunk: ChunkCoordinate)

    private data class Claim(val level: ServerLevel, val footprint: ChunkFootprint)

    private val owners = linkedMapOf<UUID, Claim>()
    private val primary = mutableMapOf<Key, Int>()
    private val retained = mutableMapOf<Key, Int>()
    val retainedCount
        get() = retained.size

    fun owns(owner: UUID): Boolean {
        check(server.isSameThread)
        return owner in owners
    }

    fun retain(owner: UUID, dimension: String, footprint: ChunkFootprint): Boolean {
        check(server.isSameThread)
        require(owner !in owners && footprint.primary.isNotEmpty())
        val level =
            server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)))
                ?: return false
        val keys = footprint.retained.map { Key(dimension, it) }
        if (keys.count { it !in retained } > maximumRetained - retained.size) return false
        val created = mutableListOf<Key>()
        try {
            for (chunk in footprint.primary) {
                val key = Key(dimension, chunk)
                if (key !in primary) {
                    level.chunkSource.addTicketWithRadius(
                        type,
                        ChunkPos(chunk.x, chunk.z),
                        ticketRadius,
                    )
                    created += key
                }
            }
        } catch (failure: Exception) {
            created.forEach {
                level.chunkSource.removeTicketWithRadius(
                    type,
                    ChunkPos(it.chunk.x, it.chunk.z),
                    ticketRadius,
                )
            }
            throw failure
        }
        footprint.primary.forEach {
            val key = Key(dimension, it)
            primary[key] = (primary[key] ?: 0) + 1
        }
        keys.forEach { retained[it] = (retained[it] ?: 0) + 1 }
        owners[owner] = Claim(level, footprint)
        return true
    }

    fun ready(owner: UUID): Boolean {
        check(server.isSameThread)
        val claim = owners[owner] ?: return false
        // A ticket or loaded block chunk alone does not establish native entity simulation
        // readiness.
        return claim.footprint.primary.all {
            claim.level.areEntitiesActuallyLoadedAndTicking(ChunkPos(it.x, it.z))
        }
    }

    fun release(owner: UUID): Boolean {
        check(server.isSameThread)
        val claim = owners.remove(owner) ?: return false
        val dimension = claim.level.dimension().identifier().toString()
        for (chunk in claim.footprint.primary) {
            val key = Key(dimension, chunk)
            val count = primary.getValue(key) - 1
            if (count == 0) {
                primary.remove(key)
                claim.level.chunkSource.removeTicketWithRadius(
                    type,
                    ChunkPos(chunk.x, chunk.z),
                    ticketRadius,
                )
            } else primary[key] = count
        }
        for (chunk in claim.footprint.retained) {
            val key = Key(dimension, chunk)
            val count = retained.getValue(key) - 1
            if (count == 0) retained.remove(key) else retained[key] = count
        }
        return true
    }

    fun close() {
        check(server.isSameThread)
        owners.keys.toList().forEach(::release)
    }

    companion object {
        private lateinit var type: TicketType
        private val ticketRadius
            get() =
                ChunkLevel.byStatus(FullChunkStatus.FULL) -
                    ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING)

        val propagationRadius
            get() = ChunkLevel.MAX_LEVEL - ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING)

        fun register() {
            type =
                Registry.register(
                    BuiltInRegistries.TICKET_TYPE,
                    Identifier.fromNamespaceAndPath("conclave", "attempt"),
                    TicketType(
                        TicketType.NO_TIMEOUT,
                        TicketType.FLAG_LOADING or
                            TicketType.FLAG_SIMULATION or
                            TicketType.FLAG_KEEP_DIMENSION_ACTIVE,
                    ),
                )
        }
    }
}
