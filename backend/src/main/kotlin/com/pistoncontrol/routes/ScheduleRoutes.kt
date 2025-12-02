package com.pistoncontrol.routes

import com.pistoncontrol.models.CreateScheduleRequest
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

fun Route.scheduleRoutes(
    scheduleService: ScheduleService,
    scheduleExecutor: ScheduleExecutor
) {
    authenticate("auth-jwt") {
        route("/schedules") {

            /**
             * POST /schedules
             * Create a new schedule
             */
            post {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<CreateScheduleRequest>()

                    // Validate action
                    if (request.action !in listOf("ACTIVATE", "DEACTIVATE")) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid action. Must be ACTIVATE or DEACTIVATE")
                        )
                    }

                    // Validate piston number
                    if (request.pistonNumber < 1 || request.pistonNumber > 8) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid piston number. Must be between 1 and 8")
                        )
                    }

                    val schedule = scheduleService.createSchedule(userId, request)
                    if (schedule == null) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Failed to create schedule. Check cron expression format.")
                        )
                    }

                    // Add to Quartz scheduler
                    scheduleExecutor.addSchedule(schedule)

                    call.respond(HttpStatusCode.Created, schedule)
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
             */
            get {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()

                    val schedules = scheduleService.getSchedulesByUser(userId)
                    call.respond(HttpStatusCode.OK, SchedulesListResponse(
                        schedules = schedules.map { schedule ->
                            ScheduleResponse(
                                id = schedule.id,
                                name = schedule.name,
                                deviceId = schedule.deviceId,
                                pistonNumber = schedule.pistonNumber,
                                action = schedule.action,
                                cronExpression = schedule.cronExpression,
                                enabled = schedule.enabled,
                                userId = schedule.userId,
                                createdAt = schedule.createdAt,
                                updatedAt = schedule.updatedAt
                            )
                        }
                    ))
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
             */
            get("/{id}") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val scheduleId = call.parameters["id"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing schedule ID")
                    )

                    val schedule = scheduleService.getScheduleById(scheduleId)
                    if (schedule == null) {
                        return@get call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("Schedule not found")
                        )
                    }

                    // Verify ownership
                    if (schedule.userId != userId) {
                        return@get call.respond(
                            HttpStatusCode.Forbidden,
                            ErrorResponse("Access denied")
                        )
                    }

                    call.respond(HttpStatusCode.OK, schedule)
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
             */
            get("/device/{deviceId}") {
                try {
                    val deviceId = call.parameters["deviceId"] ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing device ID")
                    )

                    val schedules = scheduleService.getSchedulesByDevice(deviceId)
                    call.respond(HttpStatusCode.OK, SchedulesListResponse(
                        schedules = schedules.map { schedule ->
                            ScheduleResponse(
                                id = schedule.id,
                                name = schedule.name,
                                deviceId = schedule.deviceId,
                                pistonNumber = schedule.pistonNumber,
                                action = schedule.action,
                                cronExpression = schedule.cronExpression,
                                enabled = schedule.enabled,
                                userId = schedule.userId,
                                createdAt = schedule.createdAt,
                                updatedAt = schedule.updatedAt
                            )
                        }
                    ))
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

                    // Validate action if provided
                    if (request.action != null && request.action !in listOf("ACTIVATE", "DEACTIVATE")) {
                        return@put call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid action. Must be ACTIVATE or DEACTIVATE")
                        )
                    }

                    val updatedSchedule = scheduleService.updateSchedule(scheduleId, userId, request)
                    if (updatedSchedule == null) {
                        return@put call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("Schedule not found or invalid cron expression")
                        )
                    }

                    // Update in Quartz scheduler
                    scheduleExecutor.updateSchedule(updatedSchedule)

                    call.respond(HttpStatusCode.OK, updatedSchedule)
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
             */
            delete("/{id}") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val scheduleId = call.parameters["id"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Missing schedule ID")
                    )

                    val deleted = scheduleService.deleteSchedule(scheduleId, userId)
                    if (!deleted) {
                        return@delete call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("Schedule not found")
                        )
                    }

                    // Remove from Quartz scheduler
                    scheduleExecutor.removeSchedule(scheduleId)

                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("message" to "Schedule deleted successfully")
                    )
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
