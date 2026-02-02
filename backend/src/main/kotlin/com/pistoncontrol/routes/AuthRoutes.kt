package com.pistoncontrol.routes

import com.pistoncontrol.services.AuthService
import com.pistoncontrol.services.EmailService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(jwtSecret: String, jwtIssuer: String, jwtAudience: String, emailService: EmailService) {
    val authService = AuthService(jwtSecret, jwtIssuer, jwtAudience, emailService)

    route("/auth") {
        post("/register") {
            try {
                val request = call.receive<RegisterRequest>()

                when (val result = authService.register(
                    request.firstName,
                    request.lastName,
                    request.email,
                    request.phoneNumber,
                    request.password
                )) {
                    is AuthService.AuthResult.Success -> {
                        call.respond(
                            HttpStatusCode.Created,
                            LoginResponse(result.token, result.userId)
                        )
                    }
                    is AuthService.AuthResult.VerificationRequired -> {
                        call.respond(
                            HttpStatusCode.Created,
                            RegisterResponse(result.userId, result.message)
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
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Registration failed: ${e.message}")
                )
            }
        }

        post("/login") {
            try {
                val request = call.receive<LoginRequest>()

                when (val result = authService.login(request.email, request.password)) {
                    is AuthService.AuthResult.Success -> {
                        call.respond(
                            HttpStatusCode.OK,
                            LoginResponse(result.token, result.userId)
                        )
                    }
                    is AuthService.AuthResult.VerificationRequired -> {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            ErrorResponse(result.message)
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
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Login failed: ${e.message}")
                )
            }
        }

        post("/verify-email") {
            try {
                val request = call.receive<VerifyEmailRequest>()

                when (val result = authService.verifyEmail(request.userId, request.code)) {
                    is AuthService.AuthResult.Success -> {
                        call.respond(
                            HttpStatusCode.OK,
                            LoginResponse(result.token, result.userId)
                        )
                    }
                    is AuthService.AuthResult.VerificationRequired -> {
                        call.respond(
                            HttpStatusCode.OK,
                            RegisterResponse(result.userId, result.message)
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
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Email verification failed: ${e.message}")
                )
            }
        }

        post("/resend-code") {
            try {
                val request = call.receive<ResendCodeRequest>()

                when (val result = authService.resendVerificationCode(request.userId)) {
                    is AuthService.AuthResult.Success -> {
                        call.respond(
                            HttpStatusCode.OK,
                            LoginResponse(result.token, result.userId)
                        )
                    }
                    is AuthService.AuthResult.VerificationRequired -> {
                        call.respond(
                            HttpStatusCode.OK,
                            RegisterResponse(result.userId, result.message)
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
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to resend code: ${e.message}")
                )
            }
        }
    }
}
