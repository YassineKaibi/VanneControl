package com.pistoncontrol.plugins

import com.pistoncontrol.mqtt.MqttManager
import com.pistoncontrol.services.DeviceMessageHandler
import com.pistoncontrol.services.UserService
import com.pistoncontrol.routes.*
import com.pistoncontrol.websocket.WebSocketManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: Long
)

fun Application.configureRouting(
    mqttManager: MqttManager,
    messageHandler: DeviceMessageHandler,
    scheduleService: com.pistoncontrol.services.ScheduleService,
    scheduleExecutor: com.pistoncontrol.services.ScheduleExecutor
) {
    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()

    // Get base URL for avatar URLs (used in responses)
    val baseUrl = environment.config.propertyOrNull("server.baseUrl")?.getString()
        ?: "http://localhost:8080/"

    val wsManager = WebSocketManager(mqttManager)
    wsManager.startMqttForwarding()

    val userService = UserService()

    routing {
        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                HealthResponse(
                    status = "healthy",
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        head("/health") {
            call.respond(HttpStatusCode.OK)
        }

        authRoutes(jwtSecret, jwtIssuer, jwtAudience)

        // Create DeviceService for device and admin routes
        val deviceService = com.pistoncontrol.services.DeviceService(mqttManager)

        deviceRoutes(mqttManager)
        userRoutes(userService)
        scheduleRoutes(scheduleService, scheduleExecutor)
        adminRoutes(deviceService)
        adminWebRoutes(deviceService)  // Admin web dashboard (HTML pages)
        avatarRoutes(baseUrl)  // Avatar upload and serving routes

        authenticate("auth-jwt") {
            get("/devices/{id}/stats") {
                val deviceId = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Missing device ID")
                )

                val stats = messageHandler.getDeviceStats(deviceId)

                if (stats == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(error = "Device not found")
                    )
                } else {
                    call.respond(HttpStatusCode.OK, stats)
                }
            }

            // WebSocket endpoint with JWT authentication
            webSocket("/ws") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                val sessionId = UUID.randomUUID().toString()

                // Pass userId to handle connection for device-specific filtering
                wsManager.handleConnection(sessionId, userId, this)
            }
        }
    }
}
