package dev.conclave.fabric

import dev.conclave.core.GraveStatus
import java.util.UUID
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

enum class GraveInputAction {
    PRESS,
    CONTINUE,
    RELEASE,
    SELF,
    LOOK,
}

/** Only the currently eligible helper receives this prompt and its own committed hold progress. */
data class GraveHelpPayload(
    val grave: UUID?,
    val name: String = "",
    val progress: Long = 0,
    val required: Long = 0,
    val holding: Boolean = false,
) : CustomPacketPayload {
    init {
        require(name.length <= 64 && progress >= 0 && required >= progress)
    }

    override fun type() = TYPE

    companion object {
        val TYPE =
            CustomPacketPayload.Type<GraveHelpPayload>(
                Identifier.fromNamespaceAndPath("conclave", "grave_help")
            )
        val CODEC =
            object : StreamCodec<RegistryFriendlyByteBuf, GraveHelpPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): GraveHelpPayload {
                    if (!buffer.readBoolean()) return GraveHelpPayload(null)
                    return GraveHelpPayload(
                        buffer.readUUID(),
                        buffer.readUtf(64),
                        buffer.readVarLong(),
                        buffer.readVarLong(),
                        buffer.readBoolean(),
                    )
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, value: GraveHelpPayload) {
                    buffer.writeBoolean(value.grave != null)
                    if (value.grave == null) return
                    buffer.writeUUID(value.grave)
                    buffer.writeUtf(value.name, 64)
                    buffer.writeVarLong(value.progress)
                    buffer.writeVarLong(value.required)
                    buffer.writeBoolean(value.holding)
                }
            }
    }
}

data class GraveInputPayload(val grave: UUID, val sequence: Long, val action: GraveInputAction) :
    CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE =
            CustomPacketPayload.Type<GraveInputPayload>(
                Identifier.fromNamespaceAndPath("conclave", "grave_input")
            )
        val CODEC =
            object : StreamCodec<RegistryFriendlyByteBuf, GraveInputPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): GraveInputPayload {
                    val grave = buffer.readUUID()
                    val sequence = buffer.readVarLong().also { require(it > 0) }
                    val action =
                        GraveInputAction.entries.getOrNull(buffer.readUnsignedByte().toInt())
                            ?: error("Invalid grave input")
                    return GraveInputPayload(grave, sequence, action)
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, value: GraveInputPayload) {
                    require(value.sequence > 0)
                    buffer.writeUUID(value.grave)
                    buffer.writeVarLong(value.sequence)
                    buffer.writeByte(value.action.ordinal)
                }
            }
    }
}

/** Only the dead player receives this policy/status snapshot. An empty grave closes the view. */
data class GraveStatePayload(
    val grave: UUID?,
    val status: GraveStatus = GraveStatus.CLOSED,
    val inAttempt: Boolean = false,
    val combat: Boolean = false,
    val remaining: Long = -1,
    val delay: Long = 0,
    val selfAllowed: Boolean = false,
    val message: String = "",
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE =
            CustomPacketPayload.Type<GraveStatePayload>(
                Identifier.fromNamespaceAndPath("conclave", "grave_state")
            )
        val CODEC =
            object : StreamCodec<RegistryFriendlyByteBuf, GraveStatePayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): GraveStatePayload {
                    if (!buffer.readBoolean()) return GraveStatePayload(null)
                    val grave = buffer.readUUID()
                    val status =
                        GraveStatus.entries.getOrNull(buffer.readUnsignedByte().toInt())
                            ?: error("Invalid grave state")
                    val inAttempt = buffer.readBoolean()
                    val combat = buffer.readBoolean()
                    val remaining = buffer.readLong().also { require(it >= -1) }
                    val delay = buffer.readVarLong().also { require(it >= 0) }
                    return GraveStatePayload(
                        grave,
                        status,
                        inAttempt,
                        combat,
                        remaining,
                        delay,
                        buffer.readBoolean(),
                        buffer.readUtf(512),
                    )
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, value: GraveStatePayload) {
                    buffer.writeBoolean(value.grave != null)
                    if (value.grave == null) return
                    require(value.remaining >= -1 && value.delay >= 0)
                    buffer.writeUUID(value.grave)
                    buffer.writeByte(value.status.ordinal)
                    buffer.writeBoolean(value.inAttempt)
                    buffer.writeBoolean(value.combat)
                    buffer.writeLong(value.remaining)
                    buffer.writeVarLong(value.delay)
                    buffer.writeBoolean(value.selfAllowed)
                    buffer.writeUtf(value.message, 512)
                }
            }
    }
}

/** The native marker identity is public world presentation, never a claim of revival authority. */
data class GraveMarkerPayload(val grave: UUID, val entity: Int, val visible: Boolean) :
    CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE =
            CustomPacketPayload.Type<GraveMarkerPayload>(
                Identifier.fromNamespaceAndPath("conclave", "grave_marker")
            )
        val CODEC =
            object : StreamCodec<RegistryFriendlyByteBuf, GraveMarkerPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf) =
                    GraveMarkerPayload(buffer.readUUID(), buffer.readInt(), buffer.readBoolean())

                override fun encode(buffer: RegistryFriendlyByteBuf, value: GraveMarkerPayload) {
                    buffer.writeUUID(value.grave)
                    buffer.writeInt(value.entity)
                    buffer.writeBoolean(value.visible)
                }
            }
    }
}
