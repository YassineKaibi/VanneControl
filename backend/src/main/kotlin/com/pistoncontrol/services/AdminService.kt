package com.pistoncontrol.services

import com.pistoncontrol.database.DatabaseFactory.dbQuery
import com.pistoncontrol.database.Users
import com.pistoncontrol.database.Devices
import com.pistoncontrol.database.Schedules
import com.pistoncontrol.database.Telemetry
import com.pistoncontrol.models.User
import com.pistoncontrol.models.AdminStatsResponse
import com.pistoncontrol.models.TelemetryEvent
import com.pistoncontrol.routes.DeviceWithPistonsResponse
import com.pistoncontrol.routes.PistonWithIdResponse
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

/**
 * AdminService - Business Logic for Admin Operations
 *
 * This service handles all admin-only operations including:
 * - User management (listing, updating roles, deleting)
 * - System statistics
 * - Audit log integration
 * - User device management and control
 */
class AdminService(
    private val auditLogService: AuditLogService,
    private val deviceService: DeviceService
) {

    /**
     * Sealed class for type-safe admin operation results
     */
    sealed class AdminResult {
        data class Success<T>(val data: T) : AdminResult()
        data class Failure(val error: String, val statusCode: Int = 400) : AdminResult()
    }

    /**
     * Get all users in the system
     *
     * @param limit Maximum number of users to return
     * @param offset Number of users to skip (for pagination)
     * @return List of users
     */
    suspend fun getAllUsers(limit: Int = 100, offset: Long = 0): List<User> {
        return dbQuery {
            Users.selectAll()
                .limit(limit, offset)
                .orderBy(Users.createdAt to SortOrder.DESC)
                .map { row ->
                    User(
                        id = row[Users.id].toString(),
                        email = row[Users.email],
                        role = row[Users.role],
                        firstName = row[Users.firstName],
                        lastName = row[Users.lastName],
                        phoneNumber = row[Users.phoneNumber],
                        dateOfBirth = row[Users.dateOfBirth]?.toString(),
                        location = row[Users.location],
                        avatarUrl = row[Users.avatarUrl],
                        preferences = row[Users.preferences]
                    )
                }
        }
    }

    /**
     * Get a specific user by ID
     *
     * @param userId ID of the user to retrieve
     * @return User if found, null otherwise
     */
    suspend fun getUserById(userId: UUID): User? {
        return dbQuery {
            Users.select { Users.id eq userId }
                .singleOrNull()
                ?.let { row ->
                    User(
                        id = row[Users.id].toString(),
                        email = row[Users.email],
                        role = row[Users.role],
                        firstName = row[Users.firstName],
                        lastName = row[Users.lastName],
                        phoneNumber = row[Users.phoneNumber],
                        dateOfBirth = row[Users.dateOfBirth]?.toString(),
                        location = row[Users.location],
                        avatarUrl = row[Users.avatarUrl],
                        preferences = row[Users.preferences]
                    )
                }
        }
    }

    /**
     * Update a user's role
     *
     * @param adminUserId ID of the admin performing the action
     * @param targetUserId ID of the user whose role is being updated
     * @param newRole New role to assign ("user" or "admin")
     * @return AdminResult with success or failure
     */
    suspend fun updateUserRole(
        adminUserId: UUID,
        targetUserId: UUID,
        newRole: String
    ): AdminResult {
        // Validate role
        if (newRole !in listOf("user", "admin")) {
            return AdminResult.Failure("Invalid role. Must be 'user' or 'admin'")
        }

        // Get old role for audit log
        val oldRole = dbQuery {
            Users.select { Users.id eq targetUserId }
                .singleOrNull()
                ?.get(Users.role)
        }

        if (oldRole == null) {
            return AdminResult.Failure("User not found", statusCode = 404)
        }

        // Update role
        val updated = dbQuery {
            Users.update({ Users.id eq targetUserId }) {
                it[Users.role] = newRole
                it[Users.updatedAt] = Instant.now()
            }
        }

        if (updated == 0) {
            return AdminResult.Failure("Failed to update user role", statusCode = 500)
        }

        // Log the action
        auditLogService.logAction(
            userId = adminUserId,
            action = "UPDATE_USER_ROLE",
            targetUserId = targetUserId,
            targetResourceType = "USER",
            targetResourceId = targetUserId.toString(),
            details = mapOf(
                "oldRole" to oldRole,
                "newRole" to newRole
            )
        )

        return AdminResult.Success(true)
    }

    /**
     * Delete a user (soft delete by setting active flag, or hard delete)
     * Currently implements hard delete
     *
     * @param adminUserId ID of the admin performing the action
     * @param targetUserId ID of the user to delete
     * @return AdminResult with success or failure
     */
    suspend fun deleteUser(
        adminUserId: UUID,
        targetUserId: UUID
    ): AdminResult {
        // Prevent admin from deleting themselves
        if (adminUserId == targetUserId) {
            return AdminResult.Failure("Cannot delete your own account", statusCode = 400)
        }

        // Get user details for audit log
        val user = dbQuery {
            Users.select { Users.id eq targetUserId }
                .singleOrNull()
        }

        if (user == null) {
            return AdminResult.Failure("User not found", statusCode = 404)
        }

        val userEmail = user[Users.email]

        // Delete user
        val deleted = dbQuery {
            Users.deleteWhere { Users.id eq targetUserId }
        }

        if (deleted == 0) {
            return AdminResult.Failure("Failed to delete user", statusCode = 500)
        }

        // Log the action
        auditLogService.logAction(
            userId = adminUserId,
            action = "DELETE_USER",
            targetUserId = targetUserId,
            targetResourceType = "USER",
            targetResourceId = targetUserId.toString(),
            details = mapOf(
                "email" to userEmail
            )
        )

        return AdminResult.Success(true)
    }

    /**
     * Get system statistics for admin dashboard
     *
     * @param adminUserId ID of the admin requesting stats
     * @return AdminStatsResponse with system statistics
     */
    suspend fun getAdminStats(adminUserId: UUID): AdminStatsResponse {
        // Log the action
        auditLogService.logAction(
            userId = adminUserId,
            action = "VIEW_ADMIN_STATS"
        )

        val stats = dbQuery {
            val totalUsers = Users.selectAll().count()
            val totalAdmins = Users.select { Users.role eq "admin" }.count()
            val totalDevices = Devices.selectAll().count()
            val totalSchedules = Schedules.selectAll().count()

            AdminStatsResponse(
                totalUsers = totalUsers,
                totalAdmins = totalAdmins,
                totalDevices = totalDevices,
                totalSchedules = totalSchedules,
                recentAuditLogs = emptyList() // Will be populated separately
            )
        }

        // Get recent audit logs
        val recentLogs = auditLogService.getAuditLogs(limit = 10)

        return stats.copy(recentAuditLogs = recentLogs)
    }

    /**
     * Get total user count (for pagination)
     */
    suspend fun getUserCount(): Long {
        return dbQuery {
            Users.selectAll().count()
        }
    }

    /**
     * Get all devices owned by a specific user (admin access)
     *
     * @param adminUserId ID of the admin performing the action
     * @param targetUserId ID of the user whose devices to retrieve
     * @return List of devices with pistons
     */
    suspend fun getUserDevices(
        adminUserId: UUID,
        targetUserId: UUID
    ): List<DeviceWithPistonsResponse> {
        // Log the action
        auditLogService.logAction(
            userId = adminUserId,
            action = "VIEW_USER_DEVICES",
            targetUserId = targetUserId,
            targetResourceType = "USER",
            targetResourceId = targetUserId.toString()
        )

        // Delegate to DeviceService
        return when (val result = deviceService.getUserDevices(targetUserId)) {
            is DeviceService.DeviceResult.DevicesListSuccess -> result.devices
            else -> emptyList()
        }
    }

    /**
     * Control a user's piston (admin access)
     *
     * @param adminUserId ID of the admin performing the action
     * @param targetUserId ID of the user who owns the device
     * @param deviceId ID of the device
     * @param pistonNumber Piston number (1-8)
     * @param action Action to perform ("activate" or "deactivate")
     * @return AdminResult with success or failure
     */
    suspend fun controlUserPiston(
        adminUserId: UUID,
        targetUserId: UUID,
        deviceId: UUID,
        pistonNumber: Int,
        action: String
    ): AdminResult {
        // Verify device belongs to target user
        val deviceOwner = dbQuery {
            Devices.select { Devices.id eq deviceId }
                .singleOrNull()
                ?.get(Devices.ownerId)
        }

        if (deviceOwner != targetUserId) {
            return AdminResult.Failure("Device does not belong to specified user", statusCode = 403)
        }

        // Log the action
        auditLogService.logAction(
            userId = adminUserId,
            action = "CONTROL_USER_PISTON",
            targetUserId = targetUserId,
            targetResourceType = "DEVICE",
            targetResourceId = deviceId.toString(),
            details = mapOf(
                "pistonNumber" to pistonNumber.toString(),
                "action" to action
            )
        )

        // Delegate to DeviceService (using targetUserId as the owner)
        return when (val result = deviceService.controlPiston(targetUserId, deviceId, pistonNumber, action)) {
            is DeviceService.DeviceResult.PistonSuccess -> {
                AdminResult.Success(result.piston)
            }
            is DeviceService.DeviceResult.Failure -> {
                AdminResult.Failure(result.error, result.statusCode)
            }
            else -> AdminResult.Failure("Unexpected error controlling piston", statusCode = 500)
        }
    }

    /**
     * Get telemetry/history for a specific user's devices (admin access)
     *
     * @param adminUserId ID of the admin performing the action
     * @param targetUserId ID of the user whose telemetry to retrieve
     * @param limit Maximum number of entries to return
     * @return List of telemetry events
     */
    suspend fun getUserTelemetry(
        adminUserId: UUID,
        targetUserId: UUID,
        limit: Int = 100
    ): List<TelemetryEvent> {
        // Log the action
        auditLogService.logAction(
            userId = adminUserId,
            action = "VIEW_USER_TELEMETRY",
            targetUserId = targetUserId,
            targetResourceType = "USER",
            targetResourceId = targetUserId.toString()
        )

        // Get all device IDs for the user
        val userDeviceIds = dbQuery {
            Devices.select { Devices.ownerId eq targetUserId }
                .map { it[Devices.id] }
        }

        if (userDeviceIds.isEmpty()) {
            return emptyList()
        }

        // Get telemetry for all user devices
        return dbQuery {
            Telemetry
                .select { Telemetry.deviceId inList userDeviceIds }
                .orderBy(Telemetry.createdAt to SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    TelemetryEvent(
                        id = row[Telemetry.id],
                        deviceId = row[Telemetry.deviceId].toString(),
                        pistonId = row[Telemetry.pistonId]?.toString(),
                        eventType = row[Telemetry.eventType],
                        payload = row[Telemetry.payload],
                        createdAt = row[Telemetry.createdAt].toString()
                    )
                }
        }
    }

    /**
     * Get valve activation/deactivation history with filtering (admin access)
     *
     * @param adminUserId ID of the admin performing the action
     * @param targetUserId ID of the user whose history to retrieve
     * @param pistonNumber Filter by piston number (optional)
     * @param action Filter by action type: "activated" or "deactivated" (optional)
     * @param startDate Filter by start date (ISO format, optional)
     * @param endDate Filter by end date (ISO format, optional)
     * @param limit Maximum number of entries to return
     * @return List of filtered telemetry events
     */
    suspend fun getUserValveHistory(
        adminUserId: UUID,
        targetUserId: UUID,
        pistonNumber: Int? = null,
        action: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        limit: Int = 1000
    ): List<TelemetryEvent> {
        // Log the action
        auditLogService.logAction(
            userId = adminUserId,
            action = "VIEW_USER_VALVE_HISTORY",
            targetUserId = targetUserId,
            targetResourceType = "USER",
            targetResourceId = targetUserId.toString(),
            details = mapOf(
                "pistonNumber" to (pistonNumber?.toString() ?: "all"),
                "action" to (action ?: "all"),
                "startDate" to (startDate ?: "none"),
                "endDate" to (endDate ?: "none")
            )
        )

        // Get all device IDs and piston IDs for the user
        val userDeviceIds = dbQuery {
            Devices.select { Devices.ownerId eq targetUserId }
                .map { it[Devices.id] }
        }

        if (userDeviceIds.isEmpty()) {
            return emptyList()
        }

        // Get telemetry with filters
        return dbQuery {
            var query = Telemetry
                .select {
                    (Telemetry.deviceId inList userDeviceIds) and
                    (Telemetry.eventType inList listOf("activated", "deactivated"))
                }

            // Filter by action type (activated/deactivated)
            if (action != null && action in listOf("activated", "deactivated")) {
                query = query.andWhere { Telemetry.eventType eq action }
            }

            // Filter by date range
            if (startDate != null) {
                try {
                    val start = Instant.parse(startDate)
                    query = query.andWhere { Telemetry.createdAt greaterEq start }
                } catch (e: Exception) {
                    // Invalid date format, skip filter
                }
            }

            if (endDate != null) {
                try {
                    val end = Instant.parse(endDate)
                    query = query.andWhere { Telemetry.createdAt lessEq end }
                } catch (e: Exception) {
                    // Invalid date format, skip filter
                }
            }

            val results = query
                .orderBy(Telemetry.createdAt to SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    TelemetryEvent(
                        id = row[Telemetry.id],
                        deviceId = row[Telemetry.deviceId].toString(),
                        pistonId = row[Telemetry.pistonId]?.toString(),
                        eventType = row[Telemetry.eventType],
                        payload = row[Telemetry.payload],
                        createdAt = row[Telemetry.createdAt].toString()
                    )
                }

            // Filter by piston number (if specified) by checking payload
            if (pistonNumber != null) {
                results.filter { event ->
                    event.payload?.contains("\"pistonNumber\":$pistonNumber") == true ||
                    event.payload?.contains("\"piston_number\":$pistonNumber") == true
                }
            } else {
                results
            }
        }
    }

    /**
     * Create a device for a specific user (admin access)
     *
     * @param adminUserId ID of the admin performing the action
     * @param targetUserId ID of the user who will own the device
     * @param deviceName Name for the device
     * @param mqttClientId Unique MQTT client identifier
     * @return AdminResult with success or failure
     */
    suspend fun createDeviceForUser(
        adminUserId: UUID,
        targetUserId: UUID,
        deviceName: String,
        mqttClientId: String
    ): AdminResult {
        // Validate device name
        if (deviceName.isBlank()) {
            return AdminResult.Failure("Device name cannot be empty", statusCode = 400)
        }

        // Validate MQTT client ID
        if (mqttClientId.isBlank()) {
            return AdminResult.Failure("MQTT client ID cannot be empty", statusCode = 400)
        }

        // Verify target user exists
        val targetUser = dbQuery {
            Users.select { Users.id eq targetUserId }
                .singleOrNull()
        }

        if (targetUser == null) {
            return AdminResult.Failure("Target user not found", statusCode = 404)
        }

        // Log the action
        auditLogService.logAction(
            userId = adminUserId,
            action = "CREATE_DEVICE_FOR_USER",
            targetUserId = targetUserId,
            targetResourceType = "DEVICE",
            targetResourceId = mqttClientId,
            details = mapOf(
                "deviceName" to deviceName,
                "mqttClientId" to mqttClientId
            )
        )

        // Delegate to DeviceService to create the device
        return when (val result = deviceService.createDevice(targetUserId, deviceName, mqttClientId)) {
            is DeviceService.DeviceResult.Success -> {
                AdminResult.Success(result.device)
            }
            is DeviceService.DeviceResult.Failure -> {
                AdminResult.Failure(result.error, result.statusCode)
            }
            else -> AdminResult.Failure("Unexpected error creating device", statusCode = 500)
        }
    }
}
