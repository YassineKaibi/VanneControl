package com.pistoncontrol.routes

import com.pistoncontrol.models.UpdatePreferencesRequest
import com.pistoncontrol.models.UpdateProfileRequest
import com.pistoncontrol.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(userService: UserService) {
    authenticate("auth-jwt") {
        route("/user") {
            /**
             * GET /user/profile
             * Get current user's profile
             */
            get("/profile") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()

                    val profile = userService.getUserById(userId)
                    if (profile == null) {
                        return@get call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("User not found")
                        )
                    }

                    call.respond(HttpStatusCode.OK, profile)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to fetch profile: ${e.message}")
                    )
                }
            }

            /**
             * PUT /user/profile
             * Update current user's profile details
             */
            put("/profile") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<UpdateProfileRequest>()

                    val updatedProfile = userService.updateUserDetails(userId, request)
                    if (updatedProfile == null) {
                        return@put call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("User not found")
                        )
                    }

                    call.respond(HttpStatusCode.OK, updatedProfile)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to update profile: ${e.message}")
                    )
                }
            }

            /**
             * PUT /user/preferences
             * Update current user's preferences (JSONB field)
             */
            put("/preferences") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<UpdatePreferencesRequest>()

                    // Validate JSON format
                    try {
                        kotlinx.serialization.json.Json.parseToJsonElement(request.preferences)
                    } catch (e: Exception) {
                        return@put call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid JSON format for preferences")
                        )
                    }

                    val updatedProfile = userService.updateUserPreferences(userId, request)
                    if (updatedProfile == null) {
                        return@put call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("User not found")
                        )
                    }

                    call.respond(HttpStatusCode.OK, updatedProfile)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to update preferences: ${e.message}")
                    )
                }
            }
        }
    }
}
