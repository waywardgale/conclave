package dev.conclave.fabric

import dev.conclave.fabric.mixin.*
import java.util.UUID
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.Interaction
import net.minecraft.world.phys.Vec3

internal class GraveVisual(
    val grave: UUID,
    val player: UUID,
    name: String,
    val destination: NativeDestination,
) {
    // Native IDs come from the server's allocator. These objects are presentation only: they are
    // never added to a level, saved, simulated, or treated as native interaction authority.
    val target =
        Interaction(EntityTypes.INTERACTION, destination.level).also {
            it.uuid = grave
            it.snapTo(destination.position)
            (it as GraveInteractionAccess).conclaveWidth(0.9f)
            (it as GraveInteractionAccess).conclaveHeight(0.9f)
        }
    val label =
        Display.TextDisplay(EntityTypes.TEXT_DISPLAY, destination.level).also {
            it.snapTo(destination.position.add(0.0, 1.0, 0.0))
            (it as GraveTextAccess).conclaveText(Component.literal("$name\nGrave"))
            (it as GraveDisplayAccess).conclaveBillboard(Display.BillboardConstraints.CENTER)
        }

    fun show(viewer: ServerPlayer) {
        for (entity in listOf(target, label)) {
            viewer.connection.send(
                ClientboundAddEntityPacket(
                    entity.id,
                    entity.uuid,
                    entity.x,
                    entity.y,
                    entity.z,
                    entity.xRot,
                    entity.yRot,
                    entity.type,
                    0,
                    Vec3.ZERO,
                    0.0,
                )
            )
            entity.entityData.nonDefaultValues?.let {
                viewer.connection.send(ClientboundSetEntityDataPacket(entity.id, it))
            }
        }
        ServerPlayNetworking.send(viewer, GraveMarkerPayload(grave, target.id, true))
    }

    fun hide(viewer: ServerPlayer) {
        viewer.connection.send(ClientboundRemoveEntitiesPacket(target.id, label.id))
        ServerPlayNetworking.send(viewer, GraveMarkerPayload(grave, target.id, false))
    }
}

/**
 * Per-connection projections clear naturally with the client world; no saved grave entity leaks.
 */
internal class GraveVisuals(private val session: ServerSession) {
    private class Viewer(
        val native: ServerPlayer,
        val level: net.minecraft.server.level.ServerLevel = native.level(),
        val visible: MutableMap<UUID, GraveVisual> = linkedMapOf(),
    )

    private val viewers = mutableMapOf<UUID, Viewer>()

    fun update(graves: Collection<GraveVisual>) {
        check(session.server.isSameThread)
        val online = session.server.playerList.players
        viewers.keys.retainAll(online.map { it.uuid }.toSet())
        for (player in online) {
            if (!ServerPlayNetworking.canSend(player, GraveMarkerPayload.TYPE)) continue
            var viewer = viewers[player.uuid]
            if (viewer?.native !== player || viewer.level !== player.level()) {
                // A respawn replaced the body on the existing connection. Remove any old
                // presentation before selecting the new dimension's visible set.
                if (viewer?.native?.connection === player.connection)
                    viewer.visible.values.forEach { it.hide(player) }
                viewer = Viewer(player)
                viewers[player.uuid] = viewer
            }
            val desired =
                graves
                    .asSequence()
                    .filter {
                        it.destination.level === player.level() &&
                            !(it.player == player.uuid &&
                                (player as ConclavePlayerViewing).conclaveViewing()?.mode ==
                                    dev.conclave.core.SpectatorMode.FREE) &&
                            it.destination.position.distanceToSqr(player.position()) <= 64.0 * 64.0
                    }
                    .sortedWith(
                        compareBy<GraveVisual> { it.player != player.uuid }
                            .thenBy { it.destination.position.distanceToSqr(player.position()) }
                            .thenBy { it.grave }
                    )
                    .take(128)
                    .associateBy { it.grave }
            for ((id, old) in viewer.visible.toMap()) if (desired[id] !== old) {
                old.hide(player)
                viewer.visible.remove(id)
            }
            for ((id, next) in desired) if (id !in viewer.visible) {
                next.show(player)
                viewer.visible[id] = next
            }
        }
    }

    fun disconnected(player: UUID) {
        viewers.remove(player)
    }
}
