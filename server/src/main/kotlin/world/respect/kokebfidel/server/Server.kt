package world.respect.kokebfidel.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.path
import java.io.File

fun main() {
    embeddedServer(Netty, port = 9018, host = "0.0.0.0", module = Application::module)
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
    install(AutoHeadResponse)
    install(ConditionalHeaders)
    install(PartialContent)
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }
    install(CachingHeaders) {
        options { _, outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                // Use manual headers in routing for JSON to ensure single header with no-transform
                ContentType.Application.Json -> null
                else -> null
            }
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        // Root path info
        get("/") {
            val manifestUrl = "https://kokeb.et/native_app/RESPECT_MANIFEST.json"
            val opdsUrl = "https://kokeb.et/native_app/opds.json"
            call.respondText("""
                RESPECT REST API - Alive
                ------------------------
                Manifest: $manifestUrl
                OPDS Catalog: $opdsUrl
                
                Metadata server is running on port 9018.
                To validate:
                respect-cli validate --url $manifestUrl
            """.trimIndent())
        }
        
        // Serve manifest at root
        get("/RESPECT_MANIFEST.json") {
            val file = File(assetDir, "RESPECT_MANIFEST.json")
            if (file.exists()) {
                // Manually add no-transform and specify identity encoding to prevent 
                // CDN compression stripping Content-Length.
                call.response.header(HttpHeaders.CacheControl, "no-cache, public, no-transform")
                call.response.header(HttpHeaders.ContentEncoding, "identity")
                call.respondFile(file)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        
        // Serve other assets with static files
        staticFiles("/", assetDir) {
           modify { _, call ->
               // Add no-transform and identity to all static JSON assets
               if (call.request.path().endsWith(".json")) {
                   call.response.header(HttpHeaders.CacheControl, "no-cache, public, no-transform")
                   call.response.header(HttpHeaders.ContentEncoding, "identity")
               }
           }
        }
        
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
            val manifestUrl = "http://localhost:9018/RESPECT_MANIFEST.json"
            val opdsUrl = "http://localhost:9018/opds.json"
            call.respondText("""
                RESPECT Metadata Server
                ------------------------
                Manifest: $manifestUrl
                OPDS Catalog: $opdsUrl
                
                To use with RESPECT Validator:
                1. Use ngrok: ngrok http 9018
                2. Copy the https URL provided by ngrok.
                3. Run: respect-cli validate --url <your-ngrok-url>/RESPECT_MANIFEST.json
            """.trimIndent())
        }
    }
}

