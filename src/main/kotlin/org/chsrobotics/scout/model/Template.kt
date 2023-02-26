package org.chsrobotics.scout.model

import io.github.xn32.json5k.Json5
import io.github.xn32.json5k.decodeFromStream
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths

val format = Json5 { }

@Serializable
data class Template(
    val name: String,
    val vid: String,
    val pit: List<TemplateEntry>,
    val match: List<TemplateEntry>
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
    override val value: Int? = null
) : ValueTemplateEntry, PromptTemplateEntry

@Serializable
@SerialName("picture")
data class PictureEntry(
    override val name: String,
    override val prompt: String,
) : PromptTemplateEntry
