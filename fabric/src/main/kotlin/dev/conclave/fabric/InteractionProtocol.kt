package dev.conclave.fabric

import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

enum class InteractionAction {
    LOOK,
    PRESS,
    CONTINUE,
    RELEASE,
}

/** Private acknowledgement and progress for one admitted Use gesture, with no mechanic IDs. */
data class InteractionProgressPayload(
    val offer: UUID,
    val holding: Boolean,
    val progress: Long = 0,
    val required: Long = 0,
) : CustomPacketPayload {
    init {
        require(progress >= 0 && required >= progress && (holding || required == 0L))
    }

    override fun type() = TYPE

    companion object {
        val TYPE =
            CustomPacketPayload.Type<InteractionProgressPayload>(
                Identifier.fromNamespaceAndPath("conclave", "interaction_progress")
            )
        val CODEC =
            object : StreamCodec<RegistryFriendlyByteBuf, InteractionProgressPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf) =
                    InteractionProgressPayload(
                        buffer.readUUID(),
                        buffer.readBoolean(),
                        buffer.readVarLong(),
                        buffer.readVarLong(),
                    )

                override fun encode(
                    buffer: RegistryFriendlyByteBuf,
                    value: InteractionProgressPayload,
                ) {
                    buffer.writeUUID(value.offer)
                    buffer.writeBoolean(value.holding)
                    buffer.writeVarLong(value.progress)
                    buffer.writeVarLong(value.required)
                }
            }
    }
}

/**
 * Opaque server offer pins recipients and target generation without exposing encounter internals.
 */
data class InteractionOfferPayload(
    val offer: UUID?,
    val position: BlockPos = BlockPos.ZERO,
    val consume: Boolean = false,
    val hold: Boolean = false,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE =
            CustomPacketPayload.Type<InteractionOfferPayload>(
                Identifier.fromNamespaceAndPath("conclave", "interaction_offer")
            )
        val CODEC =
            object : StreamCodec<RegistryFriendlyByteBuf, InteractionOfferPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): InteractionOfferPayload =
                    if (!buffer.readBoolean()) InteractionOfferPayload(null)
                    else
                        InteractionOfferPayload(
                            buffer.readUUID(),
                            buffer.readBlockPos(),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                        )

                override fun encode(
                    buffer: RegistryFriendlyByteBuf,
                    value: InteractionOfferPayload,
                ) {
                    buffer.writeBoolean(value.offer != null)
                    if (value.offer == null) return
                    buffer.writeUUID(value.offer)
                    buffer.writeBlockPos(value.position)
                    buffer.writeBoolean(value.consume)
                    buffer.writeBoolean(value.hold)
                }
            }
    }
}

data class InteractionInputPayload(
    val action: InteractionAction,
    val position: BlockPos,
    val sequence: Long,
    val offer: UUID? = null,
) : CustomPacketPayload {
    init {
        // ASVS 2.2.1: one bounded message shape for each operation, no client-selected mechanic
        // IDs.
        require(sequence > 0 && (action == InteractionAction.LOOK) == (offer == null))
    }

    override fun type() = TYPE

    companion object {
        val TYPE =
            CustomPacketPayload.Type<InteractionInputPayload>(
                Identifier.fromNamespaceAndPath("conclave", "interaction_input")
            )
        val CODEC =
            object : StreamCodec<RegistryFriendlyByteBuf, InteractionInputPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): InteractionInputPayload {
                    val action =
                        InteractionAction.entries.getOrNull(buffer.readUnsignedByte().toInt())
                            ?: error("Invalid interaction action")
                    return InteractionInputPayload(
                        action,
                        buffer.readBlockPos(),
                        buffer.readVarLong(),
                        if (action == InteractionAction.LOOK) null else buffer.readUUID(),
                    )
                }

                override fun encode(
                    buffer: RegistryFriendlyByteBuf,
                    value: InteractionInputPayload,
                ) {
                    buffer.writeByte(value.action.ordinal)
                    buffer.writeBlockPos(value.position)
                    buffer.writeVarLong(value.sequence)
                    value.offer?.let(buffer::writeUUID)
                }
            }
    }
}
