package org.chsrobotics.scout

import io.github.oshai.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.chsrobotics.scout.model.Template

private val logger = KotlinLogging.logger {}

fun main() {
    val templates = Template.loadAll()
    for (template in templates) {
        println(template)
    }
    embeddedServer(Netty, port = 8080, module = Application::scoutingModule).start(wait = true)
}

enum class ScoutingType {
    PIT,
    MATCH
}

fun Application.scoutingModule() {
    routing {
        for (type in ScoutingType.values()) {
            route("/${type.toString().lowercase()}") {
                get {
                    call.respond("Called ${type.toString().lowercase()}")
                }
//                post {
//                    call.
//                }
            }
        }
    }
}
