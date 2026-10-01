package dev.conclave.fabric

import java.util.UUID
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

enum class WatchAction {
    PREVIOUS,
    NEXT,
    SELECT,
    WATCH,
    LEAVE,
}

data class WatchInputPayload(val attempt: UUID, val action: WatchAction, val target: UUID? = null) :
    CustomPacketPayload {
    init {
        require((action == WatchAction.SELECT) == (target != null))
    }

    override fun type() = TYPE

    companion object {
        val TYPE =
            CustomPacketPayload.Type<WatchInputPayload>(
                Identifier.fromNamespaceAndPath("conclave", "watch_input")
            )
        val CODEC =
            object : StreamCodec<RegistryFriendlyByteBuf, WatchInputPayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): WatchInputPayload {
                    val attempt = buffer.readUUID()
                    val action =
                        WatchAction.entries.getOrNull(buffer.readUnsignedByte().toInt())
                            ?: error("Invalid camera action")
                    return WatchInputPayload(
                        attempt,
                        action,
                        if (action == WatchAction.SELECT) buffer.readUUID() else null,
                    )
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, value: WatchInputPayload) {
                    buffer.writeUUID(value.attempt)
                    buffer.writeByte(value.action.ordinal)
                    if (value.action == WatchAction.SELECT)
                        buffer.writeUUID(checkNotNull(value.target))
                }
            }
    }
}

data class WatchChoice(val player: UUID, val name: String)

data class WatchStatePayload(
    val attempt: UUID?,
    val target: UUID? = null,
    val choices: List<WatchChoice> = emptyList(),
    val message: String = "",
    val free: Boolean = false,
    val living: Boolean = false,
    val active: Boolean = true,
) : CustomPacketPayload {
    init {
        require(
            choices.size <= 1024 &&
                choices.map { it.player }.distinct().size == choices.size &&
                choices.all { it.name.length <= 64 } &&
                message.length <= 256
        )
        require(target == null || choices.any { it.player == target })
        require(
            attempt != null ||
                target == null && choices.isEmpty() && message.isEmpty() && !free && !living
        )
        require(active || living && target == null)
    }

    override fun type() = TYPE

    companion object {
        val TYPE =
            CustomPacketPayload.Type<WatchStatePayload>(
                Identifier.fromNamespaceAndPath("conclave", "watch_state")
            )
        val CODEC =
            object : StreamCodec<RegistryFriendlyByteBuf, WatchStatePayload> {
                override fun decode(buffer: RegistryFriendlyByteBuf): WatchStatePayload {
                    if (!buffer.readBoolean()) return WatchStatePayload(null)
                    val attempt = buffer.readUUID()
                    val target = if (buffer.readBoolean()) buffer.readUUID() else null
                    val size = buffer.readVarInt().also { require(it in 0..1024) }
                    return WatchStatePayload(
                        attempt,
                        target,
                        List(size) { WatchChoice(buffer.readUUID(), buffer.readUtf(64)) },
                        buffer.readUtf(256),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                    )
                }

                override fun encode(buffer: RegistryFriendlyByteBuf, value: WatchStatePayload) {
                    buffer.writeBoolean(value.attempt != null)
                    if (value.attempt == null) return
                    buffer.writeUUID(value.attempt)
                    buffer.writeBoolean(value.target != null)
                    value.target?.let(buffer::writeUUID)
                    buffer.writeVarInt(value.choices.size)
                    value.choices.forEach {
                        buffer.writeUUID(it.player)
                        buffer.writeUtf(it.name, 64)
                    }
                    buffer.writeUtf(value.message, 256)
                    buffer.writeBoolean(value.free)
                    buffer.writeBoolean(value.living)
                    buffer.writeBoolean(value.active)
                }
            }
    }
}
