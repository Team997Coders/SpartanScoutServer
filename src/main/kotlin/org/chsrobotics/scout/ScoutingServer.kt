package org.chsrobotics.scout

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import io.github.oshai.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.chsrobotics.scout.model.*
import org.chsrobotics.scout.util.encodeError
import org.chsrobotics.scout.model.JsonPrimitiveSerializer
import org.dizitart.kno2.filters.eq
import org.dizitart.kno2.filters.gt
import org.dizitart.kno2.nitrite
import org.dizitart.no2.mapper.JacksonMapper
import java.io.File

private val logger = KotlinLogging.logger {}
private val json = Json {}

@OptIn(ExperimentalSerializationApi::class)
fun main() {
    val path = System.getProperty("user.dir")
    println("Working Directory = $path")

    val module = SimpleModule()
    module.addSerializer(Instant::class.java, InstantSerializer())
    module.addDeserializer(Instant::class.java, InstantDeserializer())
    module.addSerializer(JsonPrimitive::class.java, JsonPrimitiveSerializer())
    module.addDeserializer(JsonPrimitive::class.java, JsonPrimitiveDeserializer())

    val db = nitrite {
        file = File(path).resolve("scouting.db")
        compress = true
        nitriteMapper = JacksonMapper(setOf(module))
    }

//    val testData = Json.decodeFromString<SimpleScoutingData>("""
//        {
//            "uuid": "23",
//            "templateVid": "23",
//            "type": "pit",
//            "created": "2011-10-05T14:48:00.000Z",
//            "updated": "2011-10-05T14:48:00.000Z",
//            "data": {
//                "test": "value1",
//                "key": "Wow cool"
//            }
//        }
//    """.trimIndent())

    val templates = Template.loadAll()
    for (template in templates) {
        println(template)
    }

    val defaultTemplate = templates.filter { it.uuid == Template.defaultUuid() }.maxByOrNull { it.version }

    embeddedServer(Netty, port = 8090, module = {
        install(CORS) {
//            allowMethod(HttpMethod.Options)
//            allowMethod(HttpMethod.Put)
//            allowMethod(HttpMethod.Post)
//            allowMethod(HttpMethod.Delete)
//            allowMethod(HttpMethod.Patch)
//            allowHeader(HttpHeaders.Authorization)
//            allowHeader(HttpHeaders.AccessControlAllowOrigin)
            HttpMethod.DefaultMethods.forEach { allowMethod(it) }
            allowHeaders { true }
            anyHost()
        }
        routing {
            get("/template") {
                withContext(Dispatchers.IO) {
                    val uuid = call.request.queryParameters["uuid"]
                    val version = call.request.queryParameters["version"]
                    if (uuid == null) {
                        if (defaultTemplate != null) {
                            call.respond(json.encodeToString(defaultTemplate))
                        } else {
                            call.respond(json.encodeError("Default template not set!"))
                        }
                    } else {
                        if (version == null) {
                            val template = templates.filter { it.uuid == uuid }.maxByOrNull { it.version }
                            if (template == null) {
                                call.respond(json.encodeError("No matching template found!"))
                            } else {
                                call.respond(json.encodeToString(template))
                            }
                        } else {
                            val template = templates.filter { it.uuid == uuid && it.version.toString() == version }
                            if (template.isEmpty()) {
                                call.respond(json.encodeError("No matching template found!"))
                            } else {
                                call.respond(json.encodeToString(template.first()))
                            }
                        }
                    }
                }
            }

            for (type in ScoutingType.values()) {
                val repo = db.getRepository(type.lowerName, DbScoutingData::class.java)

                route("/${type.lowerName}") {
                    get {
                        call.response.header(
                            HttpHeaders.ContentType,
                            "application/json"
                        )
                        withContext(Dispatchers.IO) {
                            try {
                                val data = call.request.queryParameters["since"]?.let {
                                    println("GOT THE SINCE")
                                    println(Instant.parse(it))
                                    repo.find(DbScoutingData::updated gt Instant.parse(it))
                                } ?: repo.find()
                                call.respond(json.encodeToString(data.map { it.toSimpleScoutingData() }.toList()))
                            } catch (e: Exception) {
                                e.printStackTrace()
                                call.respond(json.encodeError(e.toString()))
                            }
                        }
                    }
                    post {
                        call.response.header(
                            HttpHeaders.ContentType,
                            "application/json"
                        )
                        withContext(Dispatchers.IO) {
                            try {
                                val data: SimpleScoutingData = json.decodeFromStream(call.receiveStream())
                                data.storedAt = Clock.System.now()
                                val cursor = repo.find(SimpleScoutingData::uuid eq data.uuid)
                                if (cursor.size() > 0) {
                                    if (data.updated >= cursor.first().updated) {
                                        repo.update(data.toDbScoutingData())
                                    }
                                } else {
                                    repo.insert(data.toDbScoutingData())
                                }
                                val result =
                                    repo.find(SimpleScoutingData::uuid eq data.uuid).first().toSimpleScoutingData()
                                println(ObjectMapper().writer().writeValueAsString(data))
                                call.respond(json.encodeToString(result))
                            } catch (e: Exception) {
                                e.printStackTrace()
                                call.respond(json.encodeError(e.toString()))
                            }
                        }
                    }
                    delete {
                        call.response.header(
                            HttpHeaders.ContentType,
                            "application/json"
                        )
                        withContext(Dispatchers.IO) {
                            try {
                                val data: Map<String, String> = json.decodeFromStream(call.receiveStream())
                                val res = repo.remove(SimpleScoutingData::uuid eq data.get("uuid"))
                                call.respond(json.encodeToString(mapOf("success" to (res.affectedCount != 0))))
                            } catch (e: Exception) {
                                e.printStackTrace()
                                call.respond(json.encodeError(e.toString()))
                            }
                        }
                    }
                    get("csv") {
                        withContext(Dispatchers.IO) {
                            try {
                                call.response.header(
                                    HttpHeaders.ContentType,
                                    "text/csv"
                                )
                                val headers = mutableSetOf<String>()
                                val out = mutableListOf<MutableList<String?>>()
                                val query = repo.find()
                                // populate headers
                                for (data in query) {
                                    for (key in data.toSimpleScoutingData().data.keys) {
                                        headers.add(key)
                                    }
                                }
                                if (headers.isEmpty()) {
                                    call.respond("")
                                    return@withContext
                                }
                                out.add(0, headers.toMutableList())
                                for (data in query) {
                                    val outEntry = mutableListOf<String?>()
                                    for (header in headers) {
                                        outEntry.add(data.toSimpleScoutingData().data[header]?.content)
                                    }
                                    out.add(outEntry)
                                }
                                val csv = StringBuilder()
                                for (entry in out) {
                                    for (element in entry) {
                                        csv.append(element ?: "").append(',')
                                    }
                                    csv.deleteCharAt(csv.length - 1)
                                    csv.append("\n")
                                }
                                call.respond(csv.toString())
                            } catch (e: Exception) {
                                e.printStackTrace()
                                call.response.header(
                                    HttpHeaders.ContentType,
                                    "application/json"
                                )
                                call.respond(json.encodeError(e.toString()))
                            }
                        }
                    }
                }
            }
        }
    }).start(wait = true)
}

//class KotlinxFacade(
//    private val json: Json
//) : MapperFacade {
//    private fun loadDocument(node: JsonElement): Document {
//        val objectMap: MutableMap<String, Any?> = LinkedHashMap()
//        if (node is JsonObject) {
//            for ((key, value) in node) {
//                objectMap[key] = loadObject(value)
//            }
//        }
////        val fields = node.fields()
////        while (fields.hasNext()) {
////            val (name, value) = fields.next()
////            val `object` = loadObject(value)
////            objectMap[name] = `object`
////        }
//        return Document(objectMap)
//    }
//
//    private fun loadObject(node: JsonElement): Any? {
//        return when (node) {
//            is JsonObject -> loadDocument(node)
//            is JsonArray -> loadArray(node)
//            is JsonPrimitive -> getPrimitive(node)
//            else -> null
//        }
//        //        return if (node == null) null else try {
//        //            when (node.nodeType) {
//        //                JsonNodeType.ARRAY -> loadArray(node)
//        //                JsonNodeType.BINARY -> node.binaryValue()
//        //                JsonNodeType.BOOLEAN -> node.booleanValue()
//        //                JsonNodeType.MISSING, JsonNodeType.NULL -> null
//        //                JsonNodeType.NUMBER -> node.numberValue()
//        //                JsonNodeType.OBJECT -> loadDocument(node)
//        //                JsonNodeType.POJO -> loadDocument(node)
//        //                JsonNodeType.STRING -> node.textValue()
//        //            }
//        //        } catch (e: IOException) {
//        //            null
//        //        }
//        //        return null
//    }
//
//    private fun getPrimitive(node: JsonElement): Any? {
//        if (node is JsonPrimitive) {
//            if (node.booleanOrNull != null) {
//                return node.boolean
//            } else if (node.longOrNull != null) {
//                return node.long
//            } else if (node.doubleOrNull != null) {
//                return node.double
//            }
//            return node.toString()
//        }
//        return null
//    }
//
//    private fun loadArray(array: JsonArray): List<*> {
//        val list = mutableListOf<Any?>()
//        for (element in array) {
//            list.add(loadObject(element))
//        }
//        return list
////        val list: MutableList<*> = ArrayList<Any?>()
////        val iterator: Iterator<*> = array.elements()
////        while (iterator.hasNext()) {
////            val element = iterator.next()!!
////            if (element is JsonNode) {
////                list.add(loadObject(element))
////            } else {
////                list.add(element)
////            }
////        }
////        return list
//    }
//
//    fun asDocument(data: Any?): Document? {
//        return try {
//            val node = json.encodeToJsonElement(data)
////            val node: JsonNode = objectMapper.convertValue<JsonNode>(
////                `object`,
////                JsonNode::class.java
////            )
//            loadDocument(node)
//        } catch (iae: IllegalArgumentException) {
//            logger.error("Error while converting object to document ", iae)
//            if (iae.cause is JsonMappingException) {
//                val jme = iae.cause as JsonMappingException?
//                if (jme!!.cause is StackOverflowError) {
//                    throw ObjectMappingException(
//                        ErrorMessage.errorMessage(
//                            "cyclic reference detected. " + jme.pathReference,
//                            ErrorCodes.OME_CYCLE_DETECTED
//                        )
//                    )
//                }
//            }
//            throw iae
//        }
//    }
//
//    override fun <T> asObject(document: Document, type: Class<T>?): T {
//        return try {
//            // json.decodeFromJsonElement(document.toJsonObject())
//            json.decodeFromJsonElement(typeOf<T>(T).javaClass)
//            ObjectMapper().convertValue<T>(document, type)
//        } catch (iae: IllegalArgumentException) {
//            logger.error("Error while converting document to object ", iae)
//            if (iae.cause is JsonMappingException) {
//                val jme = iae.cause as JsonMappingException?
//                if (jme!!.message!!.contains("Cannot construct instance")) {
//                    throw ObjectMappingException(ErrorMessage.errorMessage(jme.message, ErrorCodes.OME_NO_DEFAULT_CTOR))
//                }
//            }
//            throw iae
//        }
//    }
//
//    override fun asValue(data: Any?): Any? {
//        val node = json.encodeToJsonElement(data)
//        return getPrimitive(node)
////        return when (node.nodeType) {
////            JsonNodeType.NUMBER -> node.numberValue()
////            JsonNodeType.STRING -> node.textValue()
////            JsonNodeType.BOOLEAN -> node.booleanValue()
////            JsonNodeType.ARRAY, JsonNodeType.BINARY, JsonNodeType.MISSING, JsonNodeType.NULL, JsonNodeType.OBJECT, JsonNodeType.POJO -> null
////            else -> null
////        }
//    }
//
//    override fun isValueType(data: Any?): Boolean {
//        val node = json.encodeToJsonElement(data)
//        return node is JsonPrimitive
//    }
//
//    @OptIn(InternalSerializationApi::class)
//    override fun parse(str: String): Document? {
//        return try {
////            val node: JsonNode = objectMapper.readValue<JsonNode>(
////                json,
////                JsonNode::class.java
////            )
//            val node = json.decodeStringToJsonTree(
//                String.serializer(),
//                str
//            )
//            loadDocument(node)
//        } catch (e: IOException) {
//            logger.error("Error while parsing json", e)
//            throw ObjectMappingException(
//                ErrorMessage.errorMessage(
//                    "failed to parse json $json",
//                    ErrorCodes.OME_PARSE_JSON_FAILED
//                )
//            )
//        }
//    }
//
//    override fun toJson(data: Any?): String? {
//        return try {
////            val stringWriter = StringWriter()
////            objectMapper.writeValue(stringWriter, `object`)
////            stringWriter.toString()
//            json.encodeToString(data)
//
//        } catch (e: IOException) {
//            logger.error("Error while serializing object to json", e)
//            throw ObjectMappingException(ErrorMessage.JSON_SERIALIZATION_FAILED)
//        }
//    }
//
//}
//
//class KotlinxMapper(json: Json = Json) : GenericMapper(KotlinxFacade(json))
