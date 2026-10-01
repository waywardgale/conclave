package dev.conclave.core

private val authoredName = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")

/** Authored definition IDs are distinct from native Minecraft resource identifiers. */
data class DefinitionId(val namespace: String, val name: String) : Comparable<DefinitionId> {
    init {
        require(isAuthoredName(namespace)) { "Namespace must be snake_case" }
        require(isAuthoredName(name)) { "Definition name must be snake_case" }
    }

    override fun toString() = "$namespace:$name"

    override fun compareTo(other: DefinitionId) = toString().compareTo(other.toString())

    companion object {
        fun parse(text: String, namespace: String = "local"): DefinitionId {
            val parts = text.split(':')
            require(parts.size in 1..2) { "Use an ID or namespace:ID" }
            return if (parts.size == 1) DefinitionId(namespace, parts[0])
            else DefinitionId(parts[0], parts[1])
        }
    }
}

data class DefinitionKey(val kind: String, val id: DefinitionId) : Comparable<DefinitionKey> {
    init {
        require(isAuthoredName(kind))
    }

    override fun toString() = "$kind/$id"

    override fun compareTo(other: DefinitionKey) = toString().compareTo(other.toString())
}

fun isAuthoredName(value: String): Boolean = value.length <= 128 && authoredName.matches(value)
