package dev.conclave.core

import java.math.BigDecimal
import java.util.Collections
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Compose
import org.snakeyaml.engine.v2.api.lowlevel.Parse
import org.snakeyaml.engine.v2.events.CollectionStartEvent
import org.snakeyaml.engine.v2.events.Event
import org.snakeyaml.engine.v2.events.NodeEvent
import org.snakeyaml.engine.v2.events.ScalarEvent
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.resolver.CoreScalarResolver
import org.snakeyaml.engine.v2.schema.CoreSchema

data class SourceDocument(val file: String, val text: String)

/** Operational development limits, configurable by the host; never inferred from authored YAML. */
data class ContentLimits(
    val documentBytes: Int = 262_144,
    val catalogBytes: Long = 4_194_304,
    val files: Int = 256,
    val nesting: Int = 48,
    val nodesPerDocument: Int = 32_768,
) {
    init {
        require(documentBytes > 0 && catalogBytes >= documentBytes && files > 0)
        require(nesting in 1..128 && nodesPerDocument > 0)
    }
}

sealed interface YamlValue {
    val source: SourceLocation
    val parameterType: String?
        get() = null

    class Mapping(entries: Map<String, YamlValue>, override val source: SourceLocation) :
        YamlValue {
        val entries: Map<String, YamlValue> = Collections.unmodifiableMap(LinkedHashMap(entries))
    }

    class Sequence(entries: List<YamlValue>, override val source: SourceLocation) : YamlValue {
        val entries: List<YamlValue> = java.util.List.copyOf(entries)
    }

    data class Text(
        val value: String,
        override val source: SourceLocation,
        override val parameterType: String? = null,
    ) : YamlValue

    data class Number(
        val value: BigDecimal,
        val integer: Boolean,
        override val source: SourceLocation,
        override val parameterType: String? = null,
    ) : YamlValue

    data class Flag(
        val value: Boolean,
        override val source: SourceLocation,
        override val parameterType: String? = null,
    ) : YamlValue

    data class Null(override val source: SourceLocation) : YamlValue
}

/**
 * Reads data nodes only. No object construction, environment expansion, tags, aliases or includes.
 */
class YamlDocumentReader(private val limits: ContentLimits = ContentLimits()) {
    fun read(document: SourceDocument): Validation<YamlValue.Mapping> =
        try {
            val source = SourceLocation(document.file)
            // ASVS 1.5.2, 2.2.1, 15.2.2: bound the input before parser allocation and composition.
            if (
                document.text.length > limits.documentBytes ||
                    document.text.toByteArray(Charsets.UTF_8).size > limits.documentBytes
            ) {
                invalid(
                    "document_limit",
                    "Manifest exceeds the configured document size limit",
                    source,
                )
            }
            val settings =
                LoadSettings.builder()
                    .setLabel(document.file)
                    .setAllowDuplicateKeys(false)
                    .setAllowRecursiveKeys(false)
                    .setAllowNonScalarKeys(false)
                    .setMaxAliasesForCollections(0)
                    .setCodePointLimit(limits.documentBytes)
                    .setSchema(
                        object : CoreSchema() {
                            override fun getScalarResolver() = CoreScalarResolver(false)
                        }
                    )
                    .setUseMarks(true)
                    .build()
            var depth = 0
            var nodes = 0
            var documents = 0
            // Check nesting in the streaming parser before the recursive composer sees it.
            for (event in Parse(settings).parseString(document.text)) {
                val at =
                    event.startMark
                        .map { source.copy(line = it.line + 1, column = it.column + 1) }
                        .orElse(source)
                if (
                    event.eventId == Event.ID.Alias || event is NodeEvent && event.anchor.isPresent
                ) {
                    invalid(
                        "yaml_alias",
                        "Use explicit mechanic references instead of YAML anchors or aliases",
                        at,
                    )
                }
                val explicitTag =
                    when (event) {
                        is ScalarEvent -> event.tag.isPresent
                        is CollectionStartEvent -> event.tag.isPresent
                        else -> false
                    }
                if (explicitTag)
                    invalid(
                        "yaml_tag",
                        "Explicit YAML tags are not supported; use ordinary values",
                        at,
                    )
                when (event.eventId) {
                    Event.ID.DocumentStart ->
                        if (++documents > 1)
                            invalid("document_count", "Use one definition per file", at)
                    Event.ID.MappingStart,
                    Event.ID.SequenceStart -> {
                        if (++depth > limits.nesting)
                            invalid(
                                "nesting_limit",
                                "Manifest nesting exceeds the configured limit",
                                at,
                            )
                        nodes++
                    }
                    Event.ID.Scalar -> nodes++
                    Event.ID.MappingEnd,
                    Event.ID.SequenceEnd -> depth--
                    else -> Unit
                }
                if (nodes > limits.nodesPerDocument)
                    invalid("node_limit", "Manifest exceeds the configured node limit", at)
            }
            val node =
                Compose(settings).composeString(document.text).orElseThrow {
                    InvalidInput(Diagnostic("empty_document", "Manifest is empty", source))
                }
            val value = convert(node, source)
            if (value !is YamlValue.Mapping)
                invalid("root_type", "Manifest root must be a mapping", value.source)
            Validation.Valid(value)
        } catch (failure: InvalidInput) {
            Validation.Invalid(listOf(failure.diagnostic))
        } catch (failure: MarkedYamlEngineException) {
            val source =
                failure.problemMark
                    .map { SourceLocation(document.file, it.line + 1, it.column + 1) }
                    .orElse(SourceLocation(document.file))
            // ASVS 16.5.1: parser exceptions can embed private source text; expose only a stable
            // diagnostic.
            Validation.Invalid(listOf(Diagnostic("yaml_syntax", "Invalid YAML syntax", source)))
        } catch (_: YamlEngineException) {
            Validation.Invalid(
                listOf(
                    Diagnostic("yaml_syntax", "Invalid YAML syntax", SourceLocation(document.file))
                )
            )
        }

    private fun convert(node: Node, parent: SourceLocation): YamlValue {
        val at =
            node.startMark
                .map { parent.copy(line = it.line + 1, column = it.column + 1) }
                .orElse(parent)
        val allowed = setOf(Tag.MAP, Tag.SEQ, Tag.STR, Tag.INT, Tag.FLOAT, Tag.BOOL, Tag.NULL)
        if (node.tag !in allowed) invalid("yaml_tag", "Custom YAML tags are not supported", at)
        return when (node) {
            is MappingNode -> {
                val entries = linkedMapOf<String, YamlValue>()
                for (entry in node.value) {
                    val key =
                        entry.keyNode as? ScalarNode
                            ?: invalid("mapping_key", "Mapping keys must be strings", at)
                    if (key.tag != Tag.STR)
                        invalid("mapping_key", "Mapping keys must be strings", at)
                    if (key.value == "<<")
                        invalid("yaml_merge", "YAML merge keys are not supported", at)
                    if (entries.containsKey(key.value)) {
                        val keyAt =
                            key.startMark
                                .map {
                                    at.field(key.value)
                                        .copy(line = it.line + 1, column = it.column + 1)
                                }
                                .orElse(at)
                        invalid("duplicate_key", "Mapping key is declared more than once", keyAt)
                    }
                    entries[key.value] = convert(entry.valueNode, at.field(key.value))
                }
                YamlValue.Mapping(entries, at)
            }
            is SequenceNode ->
                YamlValue.Sequence(
                    node.value.mapIndexed { index, child -> convert(child, at.index(index)) },
                    at,
                )
            is ScalarNode ->
                when (node.tag) {
                    Tag.STR -> YamlValue.Text(node.value, at)
                    Tag.NULL -> YamlValue.Null(at)
                    Tag.BOOL -> YamlValue.Flag(node.value.equals("true", ignoreCase = true), at)
                    Tag.INT,
                    Tag.FLOAT -> {
                        val decimal =
                            try {
                                BigDecimal(node.value)
                            } catch (_: NumberFormatException) {
                                invalid("number_format", "Use a finite decimal number", at)
                            }
                        // Bound scale before later canonicalization can expand an exponent into
                        // gigabytes.
                        if (decimal.precision() > 128 || decimal.scale() !in -128..128) {
                            invalid(
                                "number_range",
                                "Number exceeds the supported decimal range",
                                at,
                            )
                        }
                        YamlValue.Number(decimal, node.tag == Tag.INT, at)
                    }
                    else -> invalid("yaml_scalar", "Unsupported scalar value", at)
                }
            else -> invalid("yaml_node", "Unsupported YAML value", at)
        }
    }
}

/** Shared strict field access used by codecs; client and server do not maintain separate rules. */
class Fields(val node: YamlValue.Mapping) {
    private val used = mutableSetOf<String>()

    fun optional(name: String): YamlValue? {
        used += name
        return node.entries[name]
    }

    fun required(name: String): YamlValue =
        optional(name)
            ?: invalid(
                "missing_field",
                "Required field '$name' is missing",
                node.source.field(name),
            )

    fun text(name: String, default: String? = null): String {
        val value =
            optional(name)
                ?: return default
                    ?: invalid(
                        "missing_field",
                        "Required field '$name' is missing",
                        node.source.field(name),
                    )
        return value.text()
    }

    fun finish() {
        node.entries.keys
            .firstOrNull { it !in used }
            ?.let {
                invalid("unknown_field", "Unknown field '$it'", node.entries.getValue(it).source)
            }
    }
}

fun YamlValue.text(): String =
    (this as? YamlValue.Text)?.value ?: invalid("field_type", "Expected a string", source)

fun YamlValue.mapping(): YamlValue.Mapping =
    this as? YamlValue.Mapping ?: invalid("field_type", "Expected a mapping", source)

fun YamlValue.sequence(): List<YamlValue> =
    (this as? YamlValue.Sequence)?.entries ?: invalid("field_type", "Expected a list", source)

fun YamlValue.integer(): Long {
    if (this !is YamlValue.Number || !integer) invalid("field_type", "Expected an integer", source)
    return try {
        value.longValueExact()
    } catch (_: ArithmeticException) {
        invalid("number_range", "Integer exceeds the supported range", source)
    }
}
