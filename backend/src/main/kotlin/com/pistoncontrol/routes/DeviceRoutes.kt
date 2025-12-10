package com.pistoncontrol.routes

import com.pistoncontrol.mqtt.MqttManager
import com.pistoncontrol.services.DeviceService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/**
 * DeviceRoutes - HTTP Layer for Device and Piston Management Endpoints
 *
 * This file only handles HTTP concerns:
 * - Request parsing (JSON deserialization, path parameters)
 * - Response formatting (JSON serialization)
 * - HTTP status codes
 * - Error handling
 * - User authentication (JWT extraction)
 *
 * All business logic is delegated to DeviceService
 */
fun Route.deviceRoutes(mqttManager: MqttManager) {
    // Instantiate service with MQTT manager
    val deviceService = DeviceService(mqttManager)

    authenticate("auth-jwt") {
        route("/devices") {

            /**
             * POST /devices
             * Create a new device
             *
             * Request Body:
             * {
             *   "name": "Device Name",
             *   "mqttClientId": "unique-client-id"
             * }
             *
             * Success Response (201 Created):
             * {
             *   "id": "uuid",
             *   "name": "Device Name",
             *   "mqttClientId": "unique-client-id",
             *   "status": "offline"
             * }
             */
            post {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = UUID.fromString(principal.payload.getClaim("userId").asString())
                    val request = call.receive<CreateDeviceRequest>()

                    when (val result = deviceService.createDevice(userId, request.name, request.mqttClientId)) {
                        is DeviceService.DeviceResult.Success -> {
                            call.respond(HttpStatusCode.Created, result.device)
                        }
                        is DeviceService.DeviceResult.Failure -> {
                            call.respond(
                                HttpStatusCode.fromValue(result.statusCode),
                                ErrorResponse(result.error)
                            )
                        }
                        else -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponse("Unexpected result type")
                            )
                        }
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to create device: ${e.message}")
                    )
                }
            }

            /**
             * GET /devices
             * Get all devices for the current user
             *
             * Success Response (200 OK):
             * {
             *   "devices": [
             *     {
             *       "id": "uuid",
             *       "name": "Device Name",
             *       "device_id": "mqtt-client-id",
             *       "status": "online",
             *       "last_seen": null,
             *       "pistons": [...]
             *     }
             *   ]
             * }
             */
            get {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                    when (val result = deviceService.getUserDevices(userId)) {
                        is DeviceService.DeviceResult.DevicesListSuccess -> {
                            call.respond(HttpStatusCode.OK, DevicesListResponse(devices = result.devices))
                        }
                        is DeviceService.DeviceResult.Failure -> {
                            call.respond(
                                HttpStatusCode.fromValue(result.statusCode),
                                ErrorResponse(result.error)
                            )
                        }
                        else -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponse("Unexpected result type")
                            )
                        }
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(e.message ?: "Unknown error")
                    )
                }
            }

            /**
             * GET /devices/{id}
             * Get a specific device by ID
             *
             * Success Response (200 OK):
             * {
             *   "device": {
             *     "id": "uuid",
             *     "name": "Device Name",
             *     "device_id": "mqtt-client-id",
             *     "status": "online",
             *     "pistons": [...]
             *   }
             * }
             */
            get("/{id}") {
                val deviceIdParam = call.parameters["id"]
                if (deviceIdParam == null) {
                    return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing device ID"))
                }

                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = UUID.fromString(principal.payload.getClaim("userId").asString())
                    val deviceId = try {
                        UUID.fromString(deviceIdParam)
                    } catch (e: IllegalArgumentException) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid device ID format")
                        )
                    }

                    when (val result = deviceService.getDeviceById(userId, deviceId)) {
                        is DeviceService.DeviceResult.DeviceWithPistonsSuccess -> {
                            // Wrap in object matching mobile's DeviceResponse expectation
                            call.respond(HttpStatusCode.OK, mapOf("device" to result.device))
                        }
                        is DeviceService.DeviceResult.Failure -> {
                            call.respond(
                                HttpStatusCode.fromValue(result.statusCode),
                                ErrorResponse(result.error)
                            )
                        }
                        else -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponse("Unexpected result type")
                            )
                        }
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(e.message ?: "Unknown error")
                    )
                }
            }

            /**
             * POST /devices/{deviceId}/pistons/{pistonNumber}
             * Control a piston (activate or deactivate)
             *
             * Request Body:
             * {
             *   "action": "activate" | "deactivate",
             *   "piston_number": 1-8
             * }
             *
             * Success Response (200 OK):
             * {
             *   "message": "Piston activated",
             *   "piston": {
             *     "id": "uuid",
             *     "piston_number": 1,
             *     "state": "active",
             *     "last_triggered": "timestamp"
             *   }
             * }
             */
            post("/{deviceId}/pistons/{pistonNumber}") {
                val deviceIdParam = call.parameters["deviceId"]
                val pistonNumberStr = call.parameters["pistonNumber"]

                if (deviceIdParam == null) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing device ID"))
                }

                val pistonNumber = pistonNumberStr?.toIntOrNull()
                if (pistonNumber == null || pistonNumber !in 1..8) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid piston number")
                    )
                }

                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = UUID.fromString(principal.payload.getClaim("userId").asString())
                    val request = call.receive<PistonCommand>()
                    val deviceId = UUID.fromString(deviceIdParam)

                    when (val result = deviceService.controlPiston(userId, deviceId, pistonNumber, request.action)) {
                        is DeviceService.DeviceResult.PistonSuccess -> {
                            call.respond(
                                HttpStatusCode.OK,
                                PistonControlResponse(
                                    message = result.message,
                                    piston = result.piston
                                )
                            )
                        }
                        is DeviceService.DeviceResult.Failure -> {
                            call.respond(
                                HttpStatusCode.fromValue(result.statusCode),
                                ErrorResponse(result.error)
                            )
                        }
                        else -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponse("Unexpected result type")
                            )
                        }
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(e.message ?: "Unknown error")
                    )
                }
            }

            /**
             * GET /devices/{deviceId}/pistons
             * Get all pistons for a device
             *
             * Success Response (200 OK):
             * [
             *   {
             *     "piston_number": 1,
             *     "state": "active",
             *     "last_triggered": "timestamp"
             *   }
             * ]
             */
            get("/{deviceId}/pistons") {
                val deviceIdParam = call.parameters["deviceId"]
                if (deviceIdParam == null) {
                    return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing device ID"))
                }

                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = UUID.fromString(principal.payload.getClaim("userId").asString())
                    val deviceId = UUID.fromString(deviceIdParam)

                    when (val result = deviceService.getPistonsForDevice(userId, deviceId)) {
                        is DeviceService.DeviceResult.PistonsListSuccess -> {
                            call.respond(HttpStatusCode.OK, result.pistons)
                        }
                        is DeviceService.DeviceResult.Failure -> {
                            call.respond(
                                HttpStatusCode.fromValue(result.statusCode),
                                ErrorResponse(result.error)
                            )
                        }
                        else -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponse("Unexpected result type")
                            )
                        }
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(e.message ?: "Unknown error")
                    )
                }
            }

            /**
             * GET /devices/{deviceId}/stats
             * Get statistics for a specific device
             *
             * Success Response (200 OK):
             * {
             *   "deviceId": "uuid",
             *   "deviceName": "Device Name",
             *   "status": "online",
             *   "activePistons": 3,
             *   "totalPistons": 8,
             *   "totalEvents": 150,
             *   "lastActivity": "2024-01-15T10:30:00Z"
             * }
             */
            get("/{deviceId}/stats") {
                val deviceIdParam = call.parameters["deviceId"]
                if (deviceIdParam == null) {
                    return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing device ID"))
                }

                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = UUID.fromString(principal.payload.getClaim("userId").asString())
                    val deviceId = UUID.fromString(deviceIdParam)

                    when (val result = deviceService.getDeviceStats(userId, deviceId)) {
                        is DeviceService.DeviceResult.DeviceStatsSuccess -> {
                            call.respond(HttpStatusCode.OK, result.stats)
                        }
                        is DeviceService.DeviceResult.Failure -> {
                            call.respond(
                                HttpStatusCode.fromValue(result.statusCode),
                                ErrorResponse(result.error)
                            )
                        }
                        else -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponse("Unexpected result type")
                            )
                        }
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(e.message ?: "Unknown error")
                    )
                }
            }
        }

        /**
         * GET /telemetry
         * Get telemetry/history for all user's devices with optional filtering
         *
         * Query Parameters:
         * - deviceId: Filter by device (optional)
         * - pistonNumber: Filter by piston (1-8, optional)
         * - action: Filter by action type - "activated" or "deactivated" (optional)
         * - startDate: Filter by start date - ISO format (optional)
         * - endDate: Filter by end date - ISO format (optional)
         * - limit: Maximum results (default 100, max 1000)
         *
         * Success Response (200 OK):
         * {
         *   "telemetry": [
         *     {
         *       "id": 123,
         *       "deviceId": "uuid",
         *       "pistonId": "uuid",
         *       "eventType": "activated",
         *       "payload": "{\"piston_number\":1}",
         *       "createdAt": "2024-01-15T10:30:00Z"
         *     }
         *   ]
         * }
         */
        get("/telemetry") {
            try {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = UUID.fromString(principal.payload.getClaim("userId").asString())

                // Parse query parameters
                val deviceId = call.request.queryParameters["deviceId"]?.let { UUID.fromString(it) }
                val pistonNumber = call.request.queryParameters["pistonNumber"]?.toIntOrNull()
                val action = call.request.queryParameters["action"]
                val startDate = call.request.queryParameters["startDate"]
                val endDate = call.request.queryParameters["endDate"]
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100

                when (val result = deviceService.getUserTelemetry(
                    userId, deviceId, pistonNumber, action, startDate, endDate, limit
                )) {
                    is DeviceService.DeviceResult.TelemetryListSuccess -> {
                        call.respond(HttpStatusCode.OK, TelemetryListResponse(result.telemetry))
                    }
                    is DeviceService.DeviceResult.Failure -> {
                        call.respond(
                            HttpStatusCode.fromValue(result.statusCode),
                            ErrorResponse(result.error)
                        )
                    }
                    else -> {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Unexpected result type")
                        )
                    }
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Unknown error")
                )
            }
        }
    }
}
