package com.pistoncontrol.routes

import com.pistoncontrol.models.CreateScheduleRequest
import com.pistoncontrol.models.Schedule
import com.pistoncontrol.models.UpdateScheduleRequest
import com.pistoncontrol.services.ScheduleService
import com.pistoncontrol.services.ScheduleExecutor
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleResponse(
    val id: String,
    val name: String,
    val deviceId: String,
    val pistonNumber: Int,
    val action: String,
    val cronExpression: String,
    val enabled: Boolean,
    val userId: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class SchedulesListResponse(
    val schedules: List<ScheduleResponse>
)

/**
 * Extension function to convert Schedule domain model to ScheduleResponse DTO
 * Eliminates repetitive mapping code throughout routes
 */
fun Schedule.toResponse(): ScheduleResponse {
    return ScheduleResponse(
        id = this.id,
        name = this.name,
        deviceId = this.deviceId,
        pistonNumber = this.pistonNumber,
        action = this.action,
        cronExpression = this.cronExpression,
        enabled = this.enabled,
        userId = this.userId,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}

/**
 * Extension function to convert list of Schedules to SchedulesListResponse
 */
fun List<Schedule>.toListResponse(): SchedulesListResponse {
    return SchedulesListResponse(schedules = this.map { it.toResponse() })
}

/**
 * ScheduleRoutes - HTTP Layer for Schedule Management Endpoints
 *
 * This file only handles HTTP concerns:
 * - Request parsing (JSON deserialization)
 * - Response formatting (JSON serialization)
 * - HTTP status codes
 * - Error handling
 * - User authentication (JWT extraction)
 *
 * All business logic (validation, ownership checks) is delegated to ScheduleService
 */
fun Route.scheduleRoutes(
    scheduleService: ScheduleService,
    scheduleExecutor: ScheduleExecutor
) {
    authenticate("auth-jwt") {
        route("/schedules") {

            /**
             * POST /schedules
             * Create a new schedule
             *
             * Request Body:
             * {
             *   "name": "Morning activation",
             *   "deviceId": "uuid",
             *   "pistonNumber": 1,
             *   "action": "ACTIVATE",
             *   "cronExpression": "0 0 8 * * ?",
             *   "enabled": true
             * }
             *
             * Success Response (201 Created):
             * {
             *   "id": "uuid",
             *   "name": "Morning activation",
             *   ...
             * }
             */
            post {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<CreateScheduleRequest>()

                    // Delegate validation and creation to service
                    when (val result = scheduleService.createSchedule(userId, request)) {
                        is ScheduleService.ScheduleResult.Success -> {
                            // Add to Quartz scheduler
                            scheduleExecutor.addSchedule(result.schedule)
                            call.respond(HttpStatusCode.Created, result.schedule.toResponse())
                        }
                        is ScheduleService.ScheduleResult.Failure -> {
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
                        ErrorResponse("Failed to create schedule: ${e.message}")
                    )
                }
            }

            /**
             * GET /schedules
             * Get all schedules for the current user
             *
             * Success Response (200 OK):
             * {
             *   "schedules": [...]
             * }
             */
            get {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()

                    when (val result = scheduleService.getSchedulesByUser(userId)) {
                        is ScheduleService.ScheduleResult.ListSuccess -> {
                            call.respond(HttpStatusCode.OK, result.schedules.toListResponse())
                        }
                        is ScheduleService.ScheduleResult.Failure -> {
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
                        ErrorResponse("Failed to fetch schedules: ${e.message}")
                    )
                }
            }

            /**
             * GET /schedules/{id}
             * Get a specific schedule by ID
             *
             * Success Response (200 OK):
             * {
             *   "id": "uuid",
             *   "name": "Morning activation",
             *   ...
             * }
             */
            get("/{id}") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val scheduleId = call.parameters["id"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing schedule ID")
                    )

                    // Service handles ownership verification
                    when (val result = scheduleService.getScheduleById(scheduleId, userId)) {
                        is ScheduleService.ScheduleResult.Success -> {
                            call.respond(HttpStatusCode.OK, result.schedule.toResponse())
                        }
                        is ScheduleService.ScheduleResult.Failure -> {
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
                        ErrorResponse("Failed to fetch schedule: ${e.message}")
                    )
                }
            }

            /**
             * GET /schedules/device/{deviceId}
             * Get all schedules for a specific device
             *
             * Success Response (200 OK):
             * {
             *   "schedules": [...]
             * }
             */
            get("/device/{deviceId}") {
                try {
                    val deviceId = call.parameters["deviceId"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing device ID")
                    )

                    when (val result = scheduleService.getSchedulesByDevice(deviceId)) {
                        is ScheduleService.ScheduleResult.ListSuccess -> {
                            call.respond(HttpStatusCode.OK, result.schedules.toListResponse())
                        }
                        is ScheduleService.ScheduleResult.Failure -> {
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
                        ErrorResponse("Failed to fetch schedules: ${e.message}")
                    )
                }
            }

            /**
             * PUT /schedules/{id}
             * Update a schedule
             *
             * Request Body:
             * {
             *   "name": "Updated name",
             *   "action": "DEACTIVATE",
             *   "cronExpression": "0 0 20 * * ?",
             *   "enabled": false
             * }
             *
             * Success Response (200 OK):
             * {
             *   "id": "uuid",
             *   "name": "Updated name",
             *   ...
             * }
             */
            put("/{id}") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val scheduleId = call.parameters["id"] ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing schedule ID")
                    )
                    val request = call.receive<UpdateScheduleRequest>()

                    // Service handles validation and ownership verification
                    when (val result = scheduleService.updateSchedule(scheduleId, userId, request)) {
                        is ScheduleService.ScheduleResult.Success -> {
                            // Update in Quartz scheduler
                            scheduleExecutor.updateSchedule(result.schedule)
                            call.respond(HttpStatusCode.OK, result.schedule.toResponse())
                        }
                        is ScheduleService.ScheduleResult.Failure -> {
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
                        ErrorResponse("Failed to update schedule: ${e.message}")
                    )
                }
            }

            /**
             * DELETE /schedules/{id}
             * Delete a schedule
             *
             * Success Response (200 OK):
             * {
             *   "message": "Schedule deleted successfully"
             * }
             */
            delete("/{id}") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val scheduleId = call.parameters["id"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing schedule ID")
                    )

                    // Service handles ownership verification
                    when (val result = scheduleService.deleteSchedule(scheduleId, userId)) {
                        is ScheduleService.ScheduleResult.DeleteSuccess -> {
                            // Remove from Quartz scheduler
                            scheduleExecutor.removeSchedule(scheduleId)
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to result.message)
                            )
                        }
                        is ScheduleService.ScheduleResult.Failure -> {
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
                        ErrorResponse("Failed to delete schedule: ${e.message}")
                    )
                }
            }
        }
    }
}
