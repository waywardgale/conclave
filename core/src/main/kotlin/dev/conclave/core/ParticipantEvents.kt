package dev.conclave.core

/** Lifecycle sources include the retained roster, including dead, offline, and observer members. */
object LifecycleSelectionSchema : ConfigSchema<PlayerSelection> {
    override val description =
        PlayerSelectionSchema.description.copy(
            help =
                "Match the participant immediately before the transition; guards read current state",
            properties =
                PlayerSelectionSchema.description.properties +
                    mapOf(
                        "online" to
                            PlayerSelectionSchema.description.properties
                                .getValue("online")
                                .copy(defaultJson = "\"any\""),
                        "state" to lifeSchema.description.copy(defaultJson = "\"any\""),
                        "participation" to
                            participationSchema.description.copy(defaultJson = "\"any\""),
                    ),
        )

    override fun decode(value: YamlValue, context: SchemaContext): PlayerSelection {
        val node = value.mapping()
        // Supply contextual defaults before ordinary validation, including online-collection
        // limits.
        val defaults =
            listOf("online", "state", "participation").associateWith {
                YamlValue.Text("any", node.source.field(it))
            }
        return PlayerSelectionSchema.decode(
            YamlValue.Mapping(defaults + node.entries, node.source),
            context,
        )
    }

    override fun encode(value: PlayerSelection): String = PlayerSelectionSchema.encode(value)
}

internal fun participantEventContract(event: String): EventContract? {
    if (ParticipantTransition.entries.none { it.name.lowercase() == event }) return null
    val fields =
        linkedMapOf(
            "player" to EventField(EventValueKind.PLAYER),
            "online_before" to EventField(EventValueKind.BOOLEAN),
            "online_after" to EventField(EventValueKind.BOOLEAN),
            "state_before" to
                EventField(
                    EventValueKind.STRING,
                    choices = LifeState.entries.map { it.name.lowercase() }.toSet(),
                ),
            "state_after" to
                EventField(
                    EventValueKind.STRING,
                    choices = LifeState.entries.map { it.name.lowercase() }.toSet(),
                ),
            "participation_before" to
                EventField(
                    EventValueKind.STRING,
                    choices = Participation.entries.map { it.name.lowercase() }.toSet(),
                ),
            "participation_after" to
                EventField(
                    EventValueKind.STRING,
                    choices = Participation.entries.map { it.name.lowercase() }.toSet(),
                ),
        )
    if (event == "revived") {
        fields["method"] =
            EventField(
                EventValueKind.STRING,
                choices = RevivalMethod.entries.map { it.name.lowercase() }.toSet(),
            )
        fields["helper"] = EventField(EventValueKind.PLAYER, required = false)
    }
    return EventContract(fields, "player")
}

internal fun ParticipantEvent.payload(): EventPayload {
    val values =
        linkedMapOf<String, EventDatum>(
            "player" to EventDatum.Player(after.player),
            "online_before" to EventDatum.Flag(before.online),
            "online_after" to EventDatum.Flag(after.online),
            "state_before" to EventDatum.Text(before.life.name.lowercase()),
            "state_after" to EventDatum.Text(after.life.name.lowercase()),
            "participation_before" to EventDatum.Text(before.participation.name.lowercase()),
            "participation_after" to EventDatum.Text(after.participation.name.lowercase()),
        )
    method?.let { values["method"] = EventDatum.Text(it.name.lowercase()) }
    helper?.let { values["helper"] = EventDatum.Player(it) }
    return EventPayload(checkNotNull(participantEventContract(type.name.lowercase())), values)
}
