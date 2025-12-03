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

/**
 * UserRoutes - HTTP Layer for User Profile Management Endpoints
 *
 * This file only handles HTTP concerns:
 * - Request parsing (JSON deserialization)
 * - Response formatting (JSON serialization)
 * - HTTP status codes
 * - Error handling
 * - User authentication (JWT extraction)
 *
 * All business logic (validation, data access) is delegated to UserService
 */
fun Route.userRoutes(userService: UserService) {
    authenticate("auth-jwt") {
        route("/user") {
            /**
             * GET /user/profile
             * Get current user's profile
             *
             * Success Response (200 OK):
             * {
             *   "id": "uuid",
             *   "email": "user@example.com",
             *   "role": "user",
             *   "firstName": "John",
             *   ...
             * }
             */
            get("/profile") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()

                    when (val result = userService.getUserById(userId)) {
                        is UserService.UserResult.Success -> {
                            call.respond(HttpStatusCode.OK, result.profile)
                        }
                        is UserService.UserResult.Failure -> {
                            call.respond(
                                HttpStatusCode.fromValue(result.statusCode),
                                ErrorResponse(result.error)
                            )
                        }
                    }
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
             *
             * Request Body:
             * {
             *   "firstName": "John",
             *   "lastName": "Doe",
             *   "phoneNumber": "+1234567890",
             *   "dateOfBirth": "1990-01-01",
             *   "location": "New York",
             *   "avatarUrl": "https://example.com/avatar.jpg"
             * }
             *
             * Success Response (200 OK):
             * {
             *   "id": "uuid",
             *   "email": "user@example.com",
             *   "firstName": "John",
             *   ...
             * }
             */
            put("/profile") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<UpdateProfileRequest>()

                    // Service handles date validation
                    when (val result = userService.updateUserDetails(userId, request)) {
                        is UserService.UserResult.Success -> {
                            call.respond(HttpStatusCode.OK, result.profile)
                        }
                        is UserService.UserResult.Failure -> {
                            call.respond(
                                HttpStatusCode.fromValue(result.statusCode),
                                ErrorResponse(result.error)
                            )
                        }
                    }
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
             *
             * Request Body:
             * {
             *   "preferences": "{\"theme\": \"dark\", \"notifications\": true}"
             * }
             *
             * Success Response (200 OK):
             * {
             *   "id": "uuid",
             *   "email": "user@example.com",
             *   "preferences": "{\"theme\": \"dark\", \"notifications\": true}",
             *   ...
             * }
             */
            put("/preferences") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<UpdatePreferencesRequest>()

                    // Service handles JSON validation
                    when (val result = userService.updateUserPreferences(userId, request)) {
                        is UserService.UserResult.Success -> {
                            call.respond(HttpStatusCode.OK, result.profile)
                        }
                        is UserService.UserResult.Failure -> {
                            call.respond(
                                HttpStatusCode.fromValue(result.statusCode),
                                ErrorResponse(result.error)
                            )
                        }
                    }
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
