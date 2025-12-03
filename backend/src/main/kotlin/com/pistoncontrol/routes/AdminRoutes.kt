package com.pistoncontrol.routes

import com.pistoncontrol.services.AdminService
import com.pistoncontrol.services.AuditLogService
import com.pistoncontrol.models.UpdateUserRoleRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/**
 * AdminRoutes - HTTP Layer for Admin Endpoints
 *
 * This file handles admin-only operations:
 * - User management (list, view, update role, delete)
 * - System statistics
 * - Audit log viewing
 *
 * All routes require admin authentication via "admin-jwt"
 */
fun Route.adminRoutes() {
    val auditLogService = AuditLogService()
    val adminService = AdminService(auditLogService)

    // All admin routes require admin authentication
    authenticate("admin-jwt") {
        route("/admin") {
            /**
             * GET /admin/users
             *
             * Get a list of all users in the system
             *
             * Query Parameters:
             * - limit: Max number of users to return (default: 100)
             * - offset: Number of users to skip (default: 0)
             *
             * Success Response (200 OK):
             * {
             *   "users": [...],
             *   "total": 150
             * }
             */
            get("/users") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val adminUserId = UUID.fromString(principal.payload.getClaim("userId").asString())

                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0

                    // Log the action
                    auditLogService.logAction(
                        userId = adminUserId,
                        action = "VIEW_USERS_LIST"
                    )

                    val users = adminService.getAllUsers(limit, offset)
                    val total = adminService.getUserCount()

                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "users" to users,
                            "total" to total
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to retrieve users: ${e.message}")
                    )
                }
            }

            /**
             * GET /admin/users/{id}
             *
             * Get details of a specific user
             *
             * Success Response (200 OK):
             * {
             *   "id": "uuid",
             *   "email": "user@example.com",
             *   "role": "user",
             *   ...
             * }
             */
            get("/users/{id}") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val adminUserId = UUID.fromString(principal.payload.getClaim("userId").asString())

                    val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid user ID")
                        )

                    // Log the action
                    auditLogService.logAction(
                        userId = adminUserId,
                        action = "VIEW_USER_DETAILS",
                        targetUserId = userId,
                        targetResourceType = "USER",
                        targetResourceId = userId.toString()
                    )

                    val user = adminService.getUserById(userId)

                    if (user == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse("User not found")
                        )
                    } else {
                        call.respond(HttpStatusCode.OK, user)
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to retrieve user: ${e.message}")
                    )
                }
            }

            /**
             * PATCH /admin/users/{id}/role
             *
             * Update a user's role
             *
             * Request Body:
             * {
             *   "role": "admin"
             * }
             *
             * Success Response (200 OK):
             * {
             *   "message": "User role updated successfully"
             * }
             */
            patch("/users/{id}/role") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val adminUserId = UUID.fromString(principal.payload.getClaim("userId").asString())

                    val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                        ?: return@patch call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid user ID")
                        )

                    val request = call.receive<UpdateUserRoleRequest>()

                    when (val result = adminService.updateUserRole(
                        adminUserId = adminUserId,
                        targetUserId = userId,
                        newRole = request.role
                    )) {
                        is AdminService.AdminResult.Success<*> -> {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to "User role updated successfully")
                            )
                        }
                        is AdminService.AdminResult.Failure -> {
                            call.respond(
                                HttpStatusCode.fromValue(result.statusCode),
                                ErrorResponse(result.error)
                            )
                        }
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to update user role: ${e.message}")
                    )
                }
            }

            /**
             * DELETE /admin/users/{id}
             *
             * Delete a user
             *
             * Success Response (200 OK):
             * {
             *   "message": "User deleted successfully"
             * }
             */
            delete("/users/{id}") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val adminUserId = UUID.fromString(principal.payload.getClaim("userId").asString())

                    val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                        ?: return@delete call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid user ID")
                        )

                    when (val result = adminService.deleteUser(
                        adminUserId = adminUserId,
                        targetUserId = userId
                    )) {
                        is AdminService.AdminResult.Success<*> -> {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to "User deleted successfully")
                            )
                        }
                        is AdminService.AdminResult.Failure -> {
                            call.respond(
                                HttpStatusCode.fromValue(result.statusCode),
                                ErrorResponse(result.error)
                            )
                        }
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to delete user: ${e.message}")
                    )
                }
            }

            /**
             * GET /admin/stats
             *
             * Get system statistics for the admin dashboard
             *
             * Success Response (200 OK):
             * {
             *   "totalUsers": 150,
             *   "totalAdmins": 5,
             *   "totalDevices": 75,
             *   "totalSchedules": 200,
             *   "recentAuditLogs": [...]
             * }
             */
            get("/stats") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val adminUserId = UUID.fromString(principal.payload.getClaim("userId").asString())

                    val stats = adminService.getAdminStats(adminUserId = adminUserId)

                    call.respond(HttpStatusCode.OK, stats)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to retrieve stats: ${e.message}")
                    )
                }
            }

            /**
             * GET /admin/audit-logs
             *
             * Get audit logs
             *
             * Query Parameters:
             * - limit: Max number of logs to return (default: 100)
             * - offset: Number of logs to skip (default: 0)
             * - userId: Filter by admin user ID (optional)
             * - action: Filter by action type (optional)
             *
             * Success Response (200 OK):
             * {
             *   "logs": [...],
             *   "total": 500
             * }
             */
            get("/audit-logs") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val adminUserId = UUID.fromString(principal.payload.getClaim("userId").asString())

                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0
                    val filterUserId = call.request.queryParameters["userId"]?.let { UUID.fromString(it) }
                    val filterAction = call.request.queryParameters["action"]

                    // Log the action
                    auditLogService.logAction(
                        userId = adminUserId,
                        action = "VIEW_AUDIT_LOGS"
                    )

                    val logs = auditLogService.getAuditLogs(
                        limit = limit,
                        offset = offset,
                        userId = filterUserId,
                        action = filterAction
                    )
                    val total = auditLogService.getAuditLogCount()

                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "logs" to logs,
                            "total" to total
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to retrieve audit logs: ${e.message}")
                    )
                }
            }
        }
    }
}
