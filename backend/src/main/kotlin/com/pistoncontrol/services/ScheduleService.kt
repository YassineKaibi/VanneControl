package com.pistoncontrol.services

import com.pistoncontrol.database.DatabaseFactory.dbQuery
import com.pistoncontrol.database.Schedules
import com.pistoncontrol.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.quartz.CronExpression
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Schedule Service
 *
 * Handles CRUD operations for scheduled valve operations
 */
class ScheduleService {

    /**
     * Create a new schedule
     */
    suspend fun createSchedule(userId: String, request: CreateScheduleRequest): Schedule? {
        // Validate cron expression
        if (!isValidCronExpression(request.cronExpression)) {
            logger.warn { "Invalid cron expression: ${request.cronExpression}" }
            return null
        }

        // Validate action
        if (request.action !in listOf("ACTIVATE", "DEACTIVATE")) {
            logger.warn { "Invalid action: ${request.action}" }
            return null
        }

        return dbQuery {
            val now = Instant.now()
            val scheduleId = UUID.randomUUID()

            Schedules.insert {
                it[id] = scheduleId
                it[name] = request.name
                it[deviceId] = UUID.fromString(request.deviceId)
                it[pistonNumber] = request.pistonNumber
                it[action] = request.action
                it[cronExpression] = request.cronExpression
                it[enabled] = request.enabled
                it[Schedules.userId] = UUID.fromString(userId)
                it[createdAt] = now
                it[updatedAt] = now
            }

            logger.info { "Created schedule $scheduleId for device ${request.deviceId}" }

            Schedules.select { Schedules.id eq scheduleId }
                .singleOrNull()
                ?.let { rowToSchedule(it) }
        }
    }

    /**
     * Get all schedules for a user
     */
    suspend fun getSchedulesByUser(userId: String): List<Schedule> {
        return dbQuery {
            Schedules.select { Schedules.userId eq UUID.fromString(userId) }
                .orderBy(Schedules.createdAt to SortOrder.DESC)
                .map { rowToSchedule(it) }
        }
    }

    /**
     * Get schedules for a specific device
     */
    suspend fun getSchedulesByDevice(deviceId: String): List<Schedule> {
        return dbQuery {
            Schedules.select { Schedules.deviceId eq UUID.fromString(deviceId) }
                .orderBy(Schedules.createdAt to SortOrder.DESC)
                .map { rowToSchedule(it) }
        }
    }

    /**
     * Get a single schedule by ID
     */
    suspend fun getScheduleById(scheduleId: String): Schedule? {
        return dbQuery {
            Schedules.select { Schedules.id eq UUID.fromString(scheduleId) }
                .singleOrNull()
                ?.let { rowToSchedule(it) }
        }
    }

    /**
     * Get all enabled schedules (for scheduler execution)
     */
    suspend fun getEnabledSchedules(): List<Schedule> {
        return dbQuery {
            Schedules.select { Schedules.enabled eq true }
                .map { rowToSchedule(it) }
        }
    }

    /**
     * Update a schedule
     */
    suspend fun updateSchedule(scheduleId: String, userId: String, request: UpdateScheduleRequest): Schedule? {
        // Validate cron expression if provided
        if (request.cronExpression != null && !isValidCronExpression(request.cronExpression)) {
            logger.warn { "Invalid cron expression: ${request.cronExpression}" }
            return null
        }

        // Validate action if provided
        if (request.action != null && request.action !in listOf("ACTIVATE", "DEACTIVATE")) {
            logger.warn { "Invalid action: ${request.action}" }
            return null
        }

        return dbQuery {
            val scheduleUuid = UUID.fromString(scheduleId)
            val userUuid = UUID.fromString(userId)

            // Check if schedule exists and belongs to user
            val existingSchedule = Schedules.select {
                (Schedules.id eq scheduleUuid) and (Schedules.userId eq userUuid)
            }.singleOrNull() ?: return@dbQuery null

            // Update schedule
            Schedules.update({ Schedules.id eq scheduleUuid }) {
                request.name?.let { value -> it[name] = value }
                request.action?.let { value -> it[action] = value }
                request.cronExpression?.let { value -> it[cronExpression] = value }
                request.enabled?.let { value -> it[enabled] = value }
                it[updatedAt] = Instant.now()
            }

            logger.info { "Updated schedule $scheduleId" }

            // Return updated schedule
            Schedules.select { Schedules.id eq scheduleUuid }
                .singleOrNull()
                ?.let { rowToSchedule(it) }
        }
    }

    /**
     * Delete a schedule
     */
    suspend fun deleteSchedule(scheduleId: String, userId: String): Boolean {
        return dbQuery {
            val scheduleUuid = UUID.fromString(scheduleId)
            val userUuid = UUID.fromString(userId)

            // Check if schedule exists and belongs to user
            val existingSchedule = Schedules.select {
                (Schedules.id eq scheduleUuid) and (Schedules.userId eq userUuid)
            }.singleOrNull() ?: return@dbQuery false

            val deletedCount = Schedules.deleteWhere {
                Schedules.id eq scheduleUuid and (Schedules.userId eq userUuid)
            }

            if (deletedCount > 0) {
                logger.info { "Deleted schedule $scheduleId" }
                true
            } else {
                false
            }
        }
    }

    /**
     * Convert database row to Schedule model
     */
    private fun rowToSchedule(row: ResultRow): Schedule {
        return Schedule(
            id = row[Schedules.id].toString(),
            name = row[Schedules.name],
            deviceId = row[Schedules.deviceId].toString(),
            pistonNumber = row[Schedules.pistonNumber],
            action = row[Schedules.action],
            cronExpression = row[Schedules.cronExpression],
            enabled = row[Schedules.enabled],
            userId = row[Schedules.userId].toString(),
            createdAt = row[Schedules.createdAt].toString(),
            updatedAt = row[Schedules.updatedAt].toString()
        )
    }

    /**
     * Validate cron expression using Quartz
     */
    private fun isValidCronExpression(cronExpression: String): Boolean {
        return try {
            CronExpression.isValidExpression(cronExpression)
        } catch (e: Exception) {
            false
        }
    }
}
