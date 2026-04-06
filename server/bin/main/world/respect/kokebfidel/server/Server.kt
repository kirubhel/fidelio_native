package world.respect.kokebfidel.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun main() {
    embeddedServer(Netty, port = 8083, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    println("RESPECT: Current WorkDir: ${File(".").absolutePath}")
    var assetDir = File("app/src/main/assets").canonicalFile
    if (!assetDir.exists()) {
        println("RESPECT: Falling back to parent folder for assets...")
        assetDir = File("../app/src/main/assets").canonicalFile
    }
    println("RESPECT: Serving assets from: ${assetDir.absolutePath}")
    println("RESPECT: Does assets folder exist? ${assetDir.exists()}")
    
    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        // Serve manifest at root
        get("/RESPECT_MANIFEST.json") {
            val file = File(assetDir, "RESPECT_MANIFEST.json")
            if (file.exists()) {
                call.respondFile(file)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        
        // Serve other assets
        staticFiles("/", assetDir)
        
        // Android App Links
        get("/.well-known/assetlinks.json") {
            val file = File(assetDir, "assetlinks.json")
            if (file.exists()) {
                call.respondFile(file)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // Health check for validator
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        // Help text for the developer
        get("/info") {
            val manifestUrl = "http://localhost:8083/RESPECT_MANIFEST.json"
            val opdsUrl = "http://localhost:8083/opds.json"
            call.respondText("""
                RESPECT Metadata Server
                ------------------------
                Manifest: $manifestUrl
                OPDS Catalog: $opdsUrl
                
                To use with RESPECT Validator:
                1. Use ngrok: ngrok http 8083
                2. Copy the https URL provided by ngrok.
                3. Run: respect-cli validate --manifest <your-ngrok-url>/RESPECT_MANIFEST.json
            """.trimIndent())
        }
    }
}
