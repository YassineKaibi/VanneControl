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
 * ScheduleService - Centralized Schedule Management Business Logic
 *
 * This service handles all schedule-related operations including:
 * - Schedule CRUD operations
 * - Validation (cron expressions, actions, piston numbers)
 * - Ownership verification
 * - Schedule state management
 *
 * All methods return sealed ScheduleResult types for type-safe error handling
 */
class ScheduleService {

    companion object {
        private const val MIN_PISTON_NUMBER = 1
        private const val MAX_PISTON_NUMBER = 8
        private val VALID_ACTIONS = listOf("ACTIVATE", "DEACTIVATE")
    }

    /**
     * Sealed class for type-safe schedule operation results
     * Eliminates null checks and provides clear error states
     */
    sealed class ScheduleResult {
        data class Success(val schedule: Schedule) : ScheduleResult()
        data class ListSuccess(val schedules: List<Schedule>) : ScheduleResult()
        data class DeleteSuccess(val message: String = "Schedule deleted successfully") : ScheduleResult()
        data class Failure(val error: String, val statusCode: Int = 400) : ScheduleResult()
    }

    /**
     * Create a new schedule
     *
     * Process:
     * 1. Validate action (ACTIVATE/DEACTIVATE)
     * 2. Validate piston number (1-8)
     * 3. Validate cron expression format
     * 4. Insert schedule into database
     * 5. Return created schedule
     *
     * @param userId Owner's user ID
     * @param request Schedule creation request
     * @return ScheduleResult.Success with schedule, or Failure with error
     */
    suspend fun createSchedule(userId: String, request: CreateScheduleRequest): ScheduleResult {
        // Validate action
        if (request.action !in VALID_ACTIONS) {
            logger.warn { "Invalid action: ${request.action}" }
            return ScheduleResult.Failure(
                "Invalid action. Must be ACTIVATE or DEACTIVATE",
                statusCode = 400
            )
        }

        // Validate piston number
        if (request.pistonNumber !in MIN_PISTON_NUMBER..MAX_PISTON_NUMBER) {
            logger.warn { "Invalid piston number: ${request.pistonNumber}" }
            return ScheduleResult.Failure(
                "Invalid piston number. Must be between 1 and 8",
                statusCode = 400
            )
        }

        // Validate cron expression
        val cronValidation = validateCronExpression(request.cronExpression)
        if (!cronValidation.isValid) {
            logger.warn { "Invalid cron expression: ${request.cronExpression} - ${cronValidation.error}" }
            return ScheduleResult.Failure(
                cronValidation.error ?: "Invalid cron expression format",
                statusCode = 400
            )
        }

        val schedule = dbQuery {
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

        return if (schedule != null) {
            ScheduleResult.Success(schedule)
        } else {
            ScheduleResult.Failure("Failed to create schedule", statusCode = 500)
        }
    }

    /**
     * Get all schedules for a user
     *
     * @param userId Owner's user ID
     * @return ScheduleResult.ListSuccess with schedules
     */
    suspend fun getSchedulesByUser(userId: String): ScheduleResult {
        val schedules = dbQuery {
            Schedules.select { Schedules.userId eq UUID.fromString(userId) }
                .orderBy(Schedules.createdAt to SortOrder.DESC)
                .map { rowToSchedule(it) }
        }
        return ScheduleResult.ListSuccess(schedules)
    }

    /**
     * Get schedules for a specific device
     *
     * @param deviceId Device UUID
     * @return ScheduleResult.ListSuccess with schedules
     */
    suspend fun getSchedulesByDevice(deviceId: String): ScheduleResult {
        val schedules = dbQuery {
            Schedules.select { Schedules.deviceId eq UUID.fromString(deviceId) }
                .orderBy(Schedules.createdAt to SortOrder.DESC)
                .map { rowToSchedule(it) }
        }
        return ScheduleResult.ListSuccess(schedules)
    }

    /**
     * Get a single schedule by ID with ownership verification
     *
     * @param scheduleId Schedule UUID
     * @param userId Owner's user ID (for ownership verification)
     * @return ScheduleResult.Success with schedule, or Failure if not found/not owned
     */
    suspend fun getScheduleById(scheduleId: String, userId: String): ScheduleResult {
        val schedule = dbQuery {
            Schedules.select {
                (Schedules.id eq UUID.fromString(scheduleId)) and
                (Schedules.userId eq UUID.fromString(userId))
            }.singleOrNull()
                ?.let { rowToSchedule(it) }
        }

        return if (schedule != null) {
            ScheduleResult.Success(schedule)
        } else {
            ScheduleResult.Failure("Schedule not found", statusCode = 404)
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
     * Update a schedule with ownership verification
     *
     * Process:
     * 1. Validate action if provided (ACTIVATE/DEACTIVATE)
     * 2. Validate cron expression if provided
     * 3. Verify schedule exists and user owns it
     * 4. Update schedule fields
     * 5. Return updated schedule
     *
     * @param scheduleId Schedule UUID
     * @param userId Owner's user ID (for ownership verification)
     * @param request Update request with optional fields
     * @return ScheduleResult.Success with updated schedule, or Failure with error
     */
    suspend fun updateSchedule(scheduleId: String, userId: String, request: UpdateScheduleRequest): ScheduleResult {
        // Validate action if provided
        if (request.action != null && request.action !in VALID_ACTIONS) {
            logger.warn { "Invalid action: ${request.action}" }
            return ScheduleResult.Failure(
                "Invalid action. Must be ACTIVATE or DEACTIVATE",
                statusCode = 400
            )
        }

        // Validate cron expression if provided
        if (request.cronExpression != null) {
            val cronValidation = validateCronExpression(request.cronExpression)
            if (!cronValidation.isValid) {
                logger.warn { "Invalid cron expression: ${request.cronExpression} - ${cronValidation.error}" }
                return ScheduleResult.Failure(
                    cronValidation.error ?: "Invalid cron expression format",
                    statusCode = 400
                )
            }
        }

        val updatedSchedule = dbQuery {
            val scheduleUuid = UUID.fromString(scheduleId)
            val userUuid = UUID.fromString(userId)

            // Check if schedule exists and belongs to user
            val existingSchedule = Schedules.select {
                (Schedules.id eq scheduleUuid) and (Schedules.userId eq userUuid)
            }.singleOrNull()

            if (existingSchedule == null) {
                return@dbQuery null
            }

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

        return if (updatedSchedule != null) {
            ScheduleResult.Success(updatedSchedule)
        } else {
            ScheduleResult.Failure("Schedule not found", statusCode = 404)
        }
    }

    /**
     * Delete a schedule with ownership verification
     *
     * Process:
     * 1. Verify schedule exists and user owns it
     * 2. Delete schedule from database
     * 3. Return success or failure
     *
     * @param scheduleId Schedule UUID
     * @param userId Owner's user ID (for ownership verification)
     * @return ScheduleResult.DeleteSuccess or Failure if not found
     */
    suspend fun deleteSchedule(scheduleId: String, userId: String): ScheduleResult {
        val deleted = dbQuery {
            val scheduleUuid = UUID.fromString(scheduleId)
            val userUuid = UUID.fromString(userId)

            // Check if schedule exists and belongs to user
            val existingSchedule = Schedules.select {
                (Schedules.id eq scheduleUuid) and (Schedules.userId eq userUuid)
            }.singleOrNull()

            if (existingSchedule == null) {
                return@dbQuery false
            }

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

        return if (deleted) {
            ScheduleResult.DeleteSuccess()
        } else {
            ScheduleResult.Failure("Schedule not found", statusCode = 404)
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
     * Result of cron expression validation
     */
    private data class CronValidationResult(
        val isValid: Boolean,
        val error: String? = null,
        val nextFireTime: java.util.Date? = null
    )

    /**
     * Validate cron expression using Quartz
     * Checks both syntax and that expression will fire in the future
     */
    private fun validateCronExpression(cronExpression: String): CronValidationResult {
        return try {
            // First check syntax
            if (!CronExpression.isValidExpression(cronExpression)) {
                logger.debug { "Cron expression syntax is invalid: $cronExpression" }
                return CronValidationResult(
                    isValid = false,
                    error = "Invalid cron expression syntax. Expected format: 'second minute hour day month day-of-week [year]'"
                )
            }

            // Then check if it will actually fire in the future
            val cron = CronExpression(cronExpression)
            val nextFireTime = cron.getNextValidTimeAfter(java.util.Date())

            if (nextFireTime == null) {
                logger.debug { "Cron expression will never fire: $cronExpression" }
                return CronValidationResult(
                    isValid = false,
                    error = "Cron expression will never fire. This usually means the specified time is in the past."
                )
            }

            logger.debug { "Cron expression is valid. Next fire time: $nextFireTime" }
            CronValidationResult(isValid = true, nextFireTime = nextFireTime)
        } catch (e: Exception) {
            logger.debug(e) { "Error validating cron expression: $cronExpression" }
            CronValidationResult(
                isValid = false,
                error = "Invalid cron expression: ${e.message}"
            )
        }
    }
}
