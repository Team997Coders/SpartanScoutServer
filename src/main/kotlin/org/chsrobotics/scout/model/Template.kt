package org.chsrobotics.scout.model

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import io.github.xn32.json5k.Json5
import io.github.xn32.json5k.decodeFromStream
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import org.dizitart.no2.IndexType
import org.dizitart.no2.objects.Id
import org.dizitart.no2.objects.Index
import org.dizitart.no2.objects.Indices
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths

val format = Json5 { }

@Serializable
enum class ScoutingType {
    @SerialName("pit")
    PIT,
    @SerialName("match")
    MATCH;
    val lowerName: String
        get() = this.name.lowercase()
}

@Serializable
data class TemplateTab(
    val title: String,
    val entries: List<TemplateEntry>
)

@Serializable
data class Template(
    val name: String,
    val uuid: String,
    val version: Int,
    val pit: TemplateTab,
    val match: TemplateTab,
) {
    companion object {
        fun loadAll(): List<Template> {
            val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
            val resourcesPath = Paths.get(projectDirAbsolutePath, "/src/main/resources/templates")
            return Files.walk(resourcesPath)
                .filter { item -> Files.isRegularFile(item) }
                .filter { item ->
                    item.toString().endsWith(".json", true)
                            || item.toString().endsWith(".json5", true)
                            || item.toString().endsWith(".jsonc", true) }
                .map { item ->
                    format.decodeFromStream<Template>(FileInputStream(item.toFile()))
                }.toList()
        }
        fun defaultUuid(): String {
            val projectDirAbsolutePath = Paths.get("").toAbsolutePath().toString()
            val resourcesPath = Paths.get(projectDirAbsolutePath, "/src/main/resources/default-template")
            return InputStreamReader(FileInputStream(resourcesPath.toFile())).readText().trim()
        }
    }
}

object InstantEpochSerializer: KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Instant =
        Instant.fromEpochMilliseconds(decoder.decodeLong())

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilliseconds())
    }

}

class InstantSerializer : JsonSerializer<Instant>() {
    override fun serialize(value: Instant, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeNumber(value.toEpochMilliseconds())
    }
}

class InstantDeserializer : JsonDeserializer<Instant>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Instant {
        return Instant.fromEpochMilliseconds(p.numberValue.toLong())
    }
}

class JsonPrimitiveSerializer : JsonSerializer<JsonPrimitive>() {
    override fun serialize(value: JsonPrimitive, gen: JsonGenerator, provider: SerializerProvider) {
        value
        if (value.longOrNull != null) {
            gen.writeNumber(value.long)
        } else if (value.doubleOrNull != null) {
            gen.writeNumber(value.double)
        } else if (value.booleanOrNull != null) {
            gen.writeBoolean(value.boolean)
        }
        gen.writeString(value.content)
    }
}

class JsonPrimitiveDeserializer : JsonDeserializer<JsonPrimitive>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JsonPrimitive {
        if (p.currentToken == JsonToken.VALUE_NUMBER_FLOAT) {
            return JsonPrimitive(p.valueAsDouble)
        } else if (p.currentToken == JsonToken.VALUE_NUMBER_INT) {
            return JsonPrimitive(p.valueAsLong)
        } else if (p.currentToken.isBoolean) {
            return JsonPrimitive(p.valueAsBoolean)
        }
        return JsonPrimitive(p.valueAsString)
    }
}

@Serializable
data class SimpleScoutingData (
    @Id val uuid: String,
    val templateUuid: String,
    val templateVersion: Int,
    val type: ScoutingType,
    @Serializable(with=InstantEpochSerializer::class) val created: Instant,
    @Serializable(with=InstantEpochSerializer::class) val updated: Instant,
    @Serializable(with=InstantEpochSerializer::class) var storedAt: Instant? = null,
    val data: Map<String, JsonPrimitive>
) {
    fun toDbScoutingData(): DbScoutingData {
        return DbScoutingData(
            uuid,
            templateUuid,
            templateVersion,
            type,
            created,
            updated,
            storedAt,
            Json.encodeToString(data)
        )
    }
}

@Indices(
    Index(value = "uuid", type = IndexType.Unique)
)
data class DbScoutingData (
    @Id val uuid: String,
    val templateUuid: String,
    val templateVersion: Int,
    val type: ScoutingType,
    val created: Instant,
    val updated: Instant,
    var storedAt: Instant? = null,
    val data: String
) {
    fun toSimpleScoutingData(): SimpleScoutingData {
        return SimpleScoutingData(
            uuid,
            templateUuid,
            templateVersion,
            type,
            created,
            updated,
            storedAt,
            Json.decodeFromString(data)
        )
    }
}

@Serializable
enum class EntryType {
    @SerialName("section")
    SECTION,
    @SerialName("spacer")
    SPACER,
    @SerialName("text")
    TEXT,
    @SerialName("segment")
    SEGMENT,
    @SerialName("checkbox")
    CHECKBOX,
    @SerialName("counter")
    COUNTER,
    @SerialName("picture")
    PICTURE,
}

@Polymorphic
@Serializable
sealed interface TemplateEntry {
    val name: String?
//    val type: EntryType
}

sealed interface ValueTemplateEntry : TemplateEntry {
    val value: Any?
}

sealed interface PromptTemplateEntry : TemplateEntry {
    val prompt: String
}

@Serializable
@SerialName("section")
data class SectionEntry(
    override val name: String? = null,
    override val prompt: String
) : PromptTemplateEntry

@Serializable
@SerialName("spacer")
data class SpacerEntry(
    override val name: String? = null,
) : TemplateEntry

@Serializable
@SerialName("text")
data class TextEntry(
    override val name: String,
    override val prompt: String,
    override val value: String? = null,
    val numeric: Boolean = false,
    val multiline: Boolean = false,
    val length: Int? = null
) : ValueTemplateEntry, PromptTemplateEntry

@Serializable
@SerialName("segment")
data class SegmentEntry(
    override val name: String,
    override val prompt: String,
    override val value: String? = null,
    val segments: Map<String, String>
) : ValueTemplateEntry, PromptTemplateEntry

@Serializable
@SerialName("checkbox")
data class CheckboxEntry(
    override val name: String,
    override val prompt: String,
    override val value: Boolean = false,
    val excludes: List<String> = listOf()
) : ValueTemplateEntry, PromptTemplateEntry

@Serializable
@SerialName("counter")
data class CounterEntry(
    override val name: String,
    override val prompt: String,
    override val value: Int? = null,
    val maxValue: Int? = null
) : ValueTemplateEntry, PromptTemplateEntry

@Serializable
@SerialName("picture")
data class PictureEntry(
    override val name: String,
    override val prompt: String,
) : PromptTemplateEntry
