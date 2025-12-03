package com.pistoncontrol.services

import com.pistoncontrol.database.DatabaseFactory.dbQuery
import com.pistoncontrol.database.AuditLogs
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
            val query = AuditLogs.selectAll()

            // Apply filters
            userId?.let { query.andWhere { AuditLogs.userId eq it } }
            action?.let { query.andWhere { AuditLogs.action eq it } }

            query
                .orderBy(AuditLogs.createdAt to SortOrder.DESC)
                .limit(limit, offset)
                .map { row ->
                    AuditLog(
                        id = row[AuditLogs.id].toString(),
                        userId = row[AuditLogs.userId].toString(),
                        action = row[AuditLogs.action],
                        targetUserId = row[AuditLogs.targetUserId]?.toString(),
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
     * Get total count of audit logs (for pagination)
     */
    suspend fun getAuditLogCount(): Long {
        return dbQuery {
            AuditLogs.selectAll().count()
        }
    }
}
