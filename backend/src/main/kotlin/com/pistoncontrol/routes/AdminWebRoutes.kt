package com.pistoncontrol.routes

import com.pistoncontrol.plugins.AdminSession
import com.pistoncontrol.services.AdminService
import com.pistoncontrol.services.AuditLogService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.mindrot.jbcrypt.BCrypt
import com.pistoncontrol.database.DatabaseFactory.dbQuery
import com.pistoncontrol.database.Users
import org.jetbrains.exposed.sql.select
import java.util.UUID

/**
 * AdminWebRoutes - HTML rendering routes for the admin dashboard
 *
 * This file contains all web-based admin routes that serve HTML pages
 * (as opposed to AdminRoutes.kt which serves JSON for mobile apps)
 *
 * Route Structure:
 * - GET  /admin/login          → Login page (HTML form)
 * - POST /admin/login          → Process login, create session
 * - GET  /admin/logout         → Destroy session, redirect to login
 * - GET  /admin/dashboard      → Main dashboard (stats overview)
 * - GET  /admin/users          → User management table
 * - GET  /admin/users/{id}     → User detail page
 * - POST /admin/users/{id}/role → Update user role (form submission)
 * - POST /admin/users/{id}/delete → Delete user (form submission)
 * - GET  /admin/audit-logs     → Audit log viewer
 *
 * Authentication Strategy:
 * - Public routes: /admin/login
 * - Protected routes: Everything else (requires AdminSession)
 */
fun Route.adminWebRoutes(deviceService: com.pistoncontrol.services.DeviceService) {
    val auditLogService = AuditLogService()
    val adminService = AdminService(auditLogService, deviceService)

    route("/admin") {
        /**
         * GET /admin/login
         *
         * Renders the admin login page with an HTML form
         * If already logged in, redirects to dashboard
         */
        get("/login") {
            val session = call.sessions.get<AdminSession>()

            if (session != null) {
                // Already logged in, redirect to dashboard
                call.respondRedirect("/admin/dashboard")
                return@get
            }

            // Render login page
            call.respond(FreeMarkerContent("admin/login.ftl", mapOf(
                "error" to call.request.queryParameters["error"]
            )))
        }

        /**
         * POST /admin/login
         *
         * Processes login form submission:
         * 1. Validates credentials
         * 2. Checks if user has admin role
         * 3. Creates encrypted session cookie
         * 4. Redirects to dashboard
         *
         * Form parameters:
         * - email: Admin email address
         * - password: Admin password
         */
        post("/login") {
            val params = call.receiveParameters()
            val email = params["email"]?.trim()
            val password = params["password"]

            // Validation
            if (email.isNullOrBlank() || password.isNullOrBlank()) {
                call.respondRedirect("/admin/login?error=missing_fields")
                return@post
            }

            // Lookup user in database
            val user = dbQuery {
                Users.select { Users.email eq email }
                    .singleOrNull()
            }

            if (user == null) {
                call.respondRedirect("/admin/login?error=invalid_credentials")
                return@post
            }

            // Check password
            if (!BCrypt.checkpw(password, user[Users.passwordHash])) {
                call.respondRedirect("/admin/login?error=invalid_credentials")
                return@post
            }

            // Check admin role
            if (user[Users.role] != "admin") {
                call.respondRedirect("/admin/login?error=not_authorized")
                return@post
            }

            // Create session
            call.sessions.set(AdminSession(
                userId = user[Users.id].toString(),
                email = user[Users.email],
                role = user[Users.role]
            ))

            // Log successful login
            auditLogService.logAction(
                userId = user[Users.id],
                action = "ADMIN_WEB_LOGIN",
                details = mapOf("method" to "web_form")
            )

            // Redirect to dashboard
            call.respondRedirect("/admin/dashboard")
        }

        /**
         * GET /admin/logout
         *
         * Destroys the admin session and redirects to login page
         */
        get("/logout") {
            val session = call.sessions.get<AdminSession>()

            if (session != null) {
                // Log logout action
                auditLogService.logAction(
                    userId = UUID.fromString(session.userId),
                    action = "ADMIN_WEB_LOGOUT"
                )
            }

            call.sessions.clear<AdminSession>()
            call.respondRedirect("/admin/login")
        }

        /**
         * ALL routes below require authentication
         * Middleware checks for valid session, redirects to login if missing
         */
        intercept(ApplicationCallPipeline.Call) {
            val session = call.sessions.get<AdminSession>()

            // Skip authentication check for login/logout pages and static content
            if (call.request.path().startsWith("/admin/login") ||
                call.request.path() == "/admin/logout" ||
                call.request.path().startsWith("/admin/static")) {
                return@intercept
            }

            if (session == null) {
                call.respondRedirect("/admin/login")
                finish()
                return@intercept
            }

            // Session is valid, continue to route handler
        }

        /**
         * GET /admin or GET /admin/
         *
         * Redirect root admin path to dashboard
         */
        get("") {
            call.respondRedirect("/admin/dashboard")
        }

        get("/") {
            call.respondRedirect("/admin/dashboard")
        }

        /**
         * GET /admin/dashboard
         *
         * Main admin dashboard showing:
         * - System statistics (user count, device count, etc.)
         * - Recent activity
         * - Quick links to management pages
         */
        get("/dashboard") {
            val session = call.sessions.get<AdminSession>()!!

            // Fetch dashboard data
            val stats = adminService.getAdminStats(UUID.fromString(session.userId))

            call.respond(FreeMarkerContent("admin/dashboard.ftl", mapOf(
                "session" to session,
                "stats" to stats,
                "recentLogs" to stats.recentAuditLogs
            )))
        }

        /**
         * GET /admin/users
         *
         * User management page showing:
         * - Paginated table of all users
         * - Search/filter capabilities
         * - Actions (edit role, delete)
         *
         * Query parameters:
         * - page: Page number (default: 1)
         * - limit: Results per page (default: 50)
         */
        get("/users") {
            val session = call.sessions.get<AdminSession>()!!

            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val offset = ((page - 1) * limit).toLong()

            val users = adminService.getAllUsers(limit, offset)
            val totalUsers = adminService.getUserCount()
            val totalPages = ((totalUsers + limit - 1) / limit).toInt()

            call.respond(FreeMarkerContent("admin/users.ftl", mapOf(
                "session" to session,
                "users" to users,
                "currentPage" to page,
                "totalPages" to totalPages,
                "totalUsers" to totalUsers
            )))
        }

        /**
         * GET /admin/users/{id}
         *
         * Detailed user view showing:
         * - Complete user profile
         * - Devices owned by user
         * - Recent activity
         * - Edit options (role, delete)
         */
        get("/users/{id}") {
            val session = call.sessions.get<AdminSession>()!!
            val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

            val user = adminService.getUserById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@get
            }

            call.respond(FreeMarkerContent("admin/user-detail.ftl", mapOf(
                "session" to session,
                "user" to user,
                "devices" to emptyList<Any>(),
                "schedules" to emptyList<Any>()
            )))
        }

        /**
         * POST /admin/users/{id}/role
         *
         * Updates a user's role (admin form submission)
         *
         * Form parameters:
         * - role: New role ("user" or "admin")
         */
        post("/users/{id}/role") {
            val session = call.sessions.get<AdminSession>()!!
            val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

            val params = call.receiveParameters()
            val newRole = params["role"]

            if (newRole.isNullOrBlank() || newRole !in listOf("user", "admin")) {
                call.respondRedirect("/admin/users/$userId?error=invalid_role")
                return@post
            }

            val result = adminService.updateUserRole(
                adminUserId = UUID.fromString(session.userId),
                targetUserId = userId,
                newRole = newRole
            )

            when (result) {
                is AdminService.AdminResult.Success<*> -> {
                    call.respondRedirect("/admin/users/$userId?success=role_updated")
                }
                is AdminService.AdminResult.Failure -> {
                    call.respondRedirect("/admin/users/$userId?error=${result.error}")
                }
            }
        }

        /**
         * POST /admin/users/{id}/delete
         *
         * Deletes a user (admin form submission)
         * Redirects to users list after deletion
         */
        post("/users/{id}/delete") {
            val session = call.sessions.get<AdminSession>()!!
            val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

            val result = adminService.deleteUser(
                adminUserId = UUID.fromString(session.userId),
                targetUserId = userId
            )

            when (result) {
                is AdminService.AdminResult.Success<*> -> {
                    call.respondRedirect("/admin/users?success=user_deleted")
                }
                is AdminService.AdminResult.Failure -> {
                    call.respondRedirect("/admin/users?error=${result.error}")
                }
            }
        }

        /**
         * POST /admin/users/{id}/clear-history
         *
         * Clears all history and statistics for a user (admin form submission)
         * This deletes all telemetry data associated with the user's devices
         * Redirects back to user detail page after clearing
         */
        post("/users/{id}/clear-history") {
            val session = call.sessions.get<AdminSession>()!!
            val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

            val result = adminService.clearUserHistory(
                adminUserId = UUID.fromString(session.userId),
                targetUserId = userId
            )

            when (result) {
                is AdminService.AdminResult.Success<*> -> {
                    call.respondRedirect("/admin/users/$userId?success=history_cleared")
                }
                is AdminService.AdminResult.Failure -> {
                    call.respondRedirect("/admin/users/$userId?error=${result.error}")
                }
            }
        }

        /**
         * GET /admin/audit-logs
         *
         * Audit log viewer showing:
         * - Paginated list of all admin actions
         * - Filtering by action type, user, date range
         * - Detailed action information
         *
         * Query parameters:
         * - page: Page number
         * - limit: Results per page
         * - action: Filter by action type
         * - userId: Filter by user
         */
        get("/audit-logs") {
            val session = call.sessions.get<AdminSession>()!!

            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val offset = ((page - 1) * limit).toLong()

            val logs = auditLogService.getAuditLogs(limit, offset)
            val totalLogs = auditLogService.getAuditLogCount()
            val totalPages = ((totalLogs + limit - 1) / limit).toInt()

            call.respond(FreeMarkerContent("admin/audit-logs.ftl", mapOf(
                "session" to session,
                "logs" to logs,
                "currentPage" to page,
                "totalPages" to totalPages,
                "totalLogs" to totalLogs
            )))
        }

        /**
         * GET /admin/users/{id}/devices
         *
         * User devices management page showing:
         * - All devices owned by the user
         * - Device status and pistons
         * - Control buttons for each piston
         * - Telemetry/history
         */
        get("/users/{id}/devices") {
            val session = call.sessions.get<AdminSession>()!!
            val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

            val user = adminService.getUserById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@get
            }

            val devices = adminService.getUserDevices(
                adminUserId = UUID.fromString(session.userId),
                targetUserId = userId
            )

            val telemetry = adminService.getUserTelemetry(
                adminUserId = UUID.fromString(session.userId),
                targetUserId = userId,
                limit = 50
            )

            call.respond(FreeMarkerContent("admin/user-devices.ftl", mapOf(
                "session" to session,
                "user" to user,
                "devices" to devices,
                "telemetry" to telemetry
            )))
        }

        /**
         * POST /admin/users/{id}/devices/{deviceId}/pistons/{pistonNumber}/control
         *
         * Control a piston on a user's device (form submission)
         *
         * Form parameters:
         * - action: "activate" or "deactivate"
         */
        post("/users/{id}/devices/{deviceId}/pistons/{pistonNumber}/control") {
            val session = call.sessions.get<AdminSession>()!!
            val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
            val deviceId = call.parameters["deviceId"]?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid device ID")
            val pistonNumber = call.parameters["pistonNumber"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid piston number")

            val params = call.receiveParameters()
            val action = params["action"]

            if (action.isNullOrBlank() || action !in listOf("activate", "deactivate")) {
                call.respondRedirect("/admin/users/$userId/devices?error=invalid_action")
                return@post
            }

            val result = adminService.controlUserPiston(
                adminUserId = UUID.fromString(session.userId),
                targetUserId = userId,
                deviceId = deviceId,
                pistonNumber = pistonNumber,
                action = action
            )

            when (result) {
                is AdminService.AdminResult.Success<*> -> {
                    call.respondRedirect("/admin/users/$userId/devices?success=piston_controlled")
                }
                is AdminService.AdminResult.Failure -> {
                    call.respondRedirect("/admin/users/$userId/devices?error=${result.error}")
                }
            }
        }

        /**
         * POST /admin/users/{id}/devices/create
         *
         * Create a new device for the specified user (form submission)
         *
         * Form parameters:
         * - name: Device name
         * - mqttClientId: Unique MQTT client ID
         */
        post("/users/{id}/devices/create") {
            val session = call.sessions.get<AdminSession>()!!
            val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

            val params = call.receiveParameters()
            val deviceName = params["name"]?.trim()
            val mqttClientId = params["mqttClientId"]?.trim()

            // Validate form inputs
            if (deviceName.isNullOrBlank() || mqttClientId.isNullOrBlank()) {
                call.respondRedirect("/admin/users/$userId/devices?error=missing_fields")
                return@post
            }

            val result = adminService.createDeviceForUser(
                adminUserId = UUID.fromString(session.userId),
                targetUserId = userId,
                deviceName = deviceName,
                mqttClientId = mqttClientId
            )

            when (result) {
                is AdminService.AdminResult.Success<*> -> {
                    call.respondRedirect("/admin/users/$userId/devices?success=device_created")
                }
                is AdminService.AdminResult.Failure -> {
                    // URL encode the error message to handle special characters
                    val encodedError = java.net.URLEncoder.encode(result.error, "UTF-8")
                    call.respondRedirect("/admin/users/$userId/devices?error=$encodedError")
                }
            }
        }

        /**
         * GET /admin/users/{id}/history
         *
         * Valve activation/deactivation history page showing:
         * - Full history of all valve activations and deactivations
         * - Filtering by piston number, action type, date range
         * - Pagination support
         *
         * Query parameters:
         * - pistonNumber: Filter by piston number (1-8)
         * - action: Filter by action type ("activated" or "deactivated")
         * - startDate: Filter by start date (ISO format)
         * - endDate: Filter by end date (ISO format)
         * - limit: Maximum results (default: 1000)
         */
        get("/users/{id}/history") {
            val session = call.sessions.get<AdminSession>()!!
            val userId = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

            val user = adminService.getUserById(userId)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@get
            }

            // Get filter parameters
            val pistonNumber = call.request.queryParameters["pistonNumber"]?.toIntOrNull()
            val action = call.request.queryParameters["action"]
            val startDate = call.request.queryParameters["startDate"]
            val endDate = call.request.queryParameters["endDate"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000

            // Get filtered history
            val history = adminService.getUserValveHistory(
                adminUserId = UUID.fromString(session.userId),
                targetUserId = userId,
                pistonNumber = pistonNumber,
                action = action,
                startDate = startDate,
                endDate = endDate,
                limit = limit
            )

            call.respond(FreeMarkerContent("admin/user-history.ftl", mapOf(
                "session" to session,
                "user" to user,
                "history" to history,
                "filters" to mapOf(
                    "pistonNumber" to pistonNumber,
                    "action" to action,
                    "startDate" to startDate,
                    "endDate" to endDate
                )
            )))
        }
    }
}
