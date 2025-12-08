package com.pistoncontrol.services

import com.pistoncontrol.database.DatabaseFactory.dbQuery
import com.pistoncontrol.database.AuditLogs
import com.pistoncontrol.database.Users
import com.pistoncontrol.models.AuditLog
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * AuditLogService - Centralized Audit Logging for Admin Actions
 *
 * This service handles all audit logging operations to track admin activities including:
 * - Recording admin actions (user role changes, deletions, etc.)
 * - Retrieving audit logs for review
 * - Tracking IP addresses and user agents for security
 */
class AuditLogService {

    /**
     * Log an admin action to the audit log
     *
     * @param userId ID of the admin performing the action
     * @param action Description of the action (e.g., "UPDATE_USER_ROLE", "DELETE_USER")
     * @param targetUserId ID of the user affected by the action (optional)
     * @param targetResourceType Type of resource affected (e.g., "USER", "DEVICE")
     * @param targetResourceId ID of the resource affected
     * @param details Additional context as a map (will be stored as JSON)
     * @param ipAddress IP address of the admin
     * @param userAgent User agent string
     */
    suspend fun logAction(
        userId: UUID,
        action: String,
        targetUserId: UUID? = null,
        targetResourceType: String? = null,
        targetResourceId: String? = null,
        details: Map<String, String>? = null,
        ipAddress: String? = null,
        userAgent: String? = null
    ) {
        dbQuery {
            AuditLogs.insert {
                it[AuditLogs.userId] = userId
                it[AuditLogs.action] = action
                it[AuditLogs.targetUserId] = targetUserId
                it[AuditLogs.targetResourceType] = targetResourceType
                it[AuditLogs.targetResourceId] = targetResourceId
                it[AuditLogs.details] = details?.let { d -> Json.encodeToString(d) }
                it[AuditLogs.ipAddress] = ipAddress
                it[AuditLogs.userAgent] = userAgent
                it[AuditLogs.createdAt] = Instant.now()
            }
        }
    }

    /**
     * Retrieve audit logs with optional filtering
     *
     * @param limit Maximum number of logs to retrieve
     * @param offset Number of logs to skip (for pagination)
     * @param userId Filter by admin user ID (optional)
     * @param action Filter by action type (optional)
     * @return List of audit logs
     */
    suspend fun getAuditLogs(
        limit: Int = 100,
        offset: Long = 0,
        userId: UUID? = null,
        action: String? = null
    ): List<AuditLog> {
        return dbQuery {
            // Create aliases for user tables to join both performer and target
            val performerUser = Users.alias("performer")
            val targetUser = Users.alias("target")

            // Build query with left joins to get user names
            val query = AuditLogs
                .leftJoin(performerUser, { AuditLogs.userId }, { performerUser[Users.id] })
                .leftJoin(targetUser, { AuditLogs.targetUserId }, { targetUser[Users.id] })
                .selectAll()

            // Apply filters
            userId?.let { query.andWhere { AuditLogs.userId eq it } }
            action?.let { query.andWhere { AuditLogs.action eq it } }

            query
                .orderBy(AuditLogs.createdAt to SortOrder.DESC)
                .limit(limit, offset)
                .map { row ->
                    // Build full name for performer
                    val performerFirstName = row.getOrNull(performerUser[Users.firstName])
                    val performerLastName = row.getOrNull(performerUser[Users.lastName])
                    val performerFullName = buildFullName(performerFirstName, performerLastName)

                    // Build full name for target user
                    val targetFirstName = row.getOrNull(targetUser[Users.firstName])
                    val targetLastName = row.getOrNull(targetUser[Users.lastName])
                    val targetFullName = buildFullName(targetFirstName, targetLastName)

                    AuditLog(
                        id = row[AuditLogs.id].toString(),
                        userId = row[AuditLogs.userId].toString(),
                        userFullName = performerFullName,
                        action = row[AuditLogs.action],
                        targetUserId = row[AuditLogs.targetUserId]?.toString(),
                        targetUserFullName = targetFullName,
                        targetResourceType = row[AuditLogs.targetResourceType],
                        targetResourceId = row[AuditLogs.targetResourceId],
                        details = row[AuditLogs.details],
                        ipAddress = row[AuditLogs.ipAddress],
                        userAgent = row[AuditLogs.userAgent],
                        createdAt = row[AuditLogs.createdAt].toString()
                    )
                }
        }
    }

    /**
     * Helper function to build full name from first and last names
     */
    private fun buildFullName(firstName: String?, lastName: String?): String? {
        return when {
            firstName != null && lastName != null -> "$firstName $lastName"
            firstName != null -> firstName
            lastName != null -> lastName
            else -> null
        }
    }

    /**
     * Get total count of audit logs (for pagination)
     */
    suspend fun getAuditLogCount(): Long {
        return dbQuery {
            AuditLogs.selectAll().count()
        }
    }
}
