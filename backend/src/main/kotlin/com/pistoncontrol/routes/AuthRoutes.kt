package com.pistoncontrol.routes

import com.pistoncontrol.services.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * AuthRoutes - HTTP Layer for Authentication Endpoints
 *
 * This file only handles HTTP concerns:
 * - Request parsing (JSON deserialization)
 * - Response formatting (JSON serialization)
 * - HTTP status codes
 * - Error handling
 *
 * All business logic is delegated to AuthService
 */
fun Route.authRoutes(jwtSecret: String, jwtIssuer: String, jwtAudience: String) {
    // Instantiate service with JWT configuration
    val authService = AuthService(jwtSecret, jwtIssuer, jwtAudience)

    route("/auth") {
        /**
         * POST /auth/register
         *
         * Register a new user account
         *
         * Request Body:
         * {
         *   "email": "user@example.com",
         *   "password": "SecurePass123"
         * }
         *
         * Success Response (201 Created):
         * {
         *   "token": "eyJhbGci...",
         *   "userId": "uuid"
         * }
         *
         * Error Response (400 Bad Request / 409 Conflict):
         * {
         *   "error": "Error message"
         * }
         */
        post("/register") {
            try {
                // Parse request body
                val request = call.receive<RegisterRequest>()

                // Delegate business logic to service
                when (val result = authService.register(request.email, request.password)) {
                    is AuthService.AuthResult.Success -> {
                        call.respond(
                            HttpStatusCode.Created,
                            LoginResponse(result.token, result.userId)
                        )
                    }
                    is AuthService.AuthResult.Failure -> {
                        call.respond(
                            HttpStatusCode.fromValue(result.statusCode),
                            ErrorResponse(result.error)
                        )
                    }
                }
            } catch (e: Exception) {
                // Handle unexpected errors
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Registration failed: ${e.message}")
                )
            }
        }

        /**
         * POST /auth/login
         *
         * Authenticate user with email and password
         *
         * Request Body:
         * {
         *   "email": "user@example.com",
         *   "password": "SecurePass123"
         * }
         *
         * Success Response (200 OK):
         * {
         *   "token": "eyJhbGci...",
         *   "userId": "uuid"
         * }
         *
         * Error Response (401 Unauthorized):
         * {
         *   "error": "Invalid credentials"
         * }
         */
        post("/login") {
            try {
                // Parse request body
                val request = call.receive<LoginRequest>()

                // Delegate business logic to service
                when (val result = authService.login(request.email, request.password)) {
                    is AuthService.AuthResult.Success -> {
                        call.respond(
                            HttpStatusCode.OK,
                            LoginResponse(result.token, result.userId)
                        )
                    }
                    is AuthService.AuthResult.Failure -> {
                        call.respond(
                            HttpStatusCode.fromValue(result.statusCode),
                            ErrorResponse(result.error)
                        )
                    }
                }
            } catch (e: Exception) {
                // Handle unexpected errors
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Login failed: ${e.message}")
                )
            }
        }
    }
}
