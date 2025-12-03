package com.pistoncontrol.services

import com.pistoncontrol.database.DatabaseFactory.dbQuery
import com.pistoncontrol.database.Users
import com.pistoncontrol.models.*
import org.jetbrains.exposed.sql.*
import mu.KotlinLogging
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * UserService - Centralized User Profile Management Business Logic
 *
 * This service handles all user profile-related operations including:
 * - User profile retrieval
 * - Profile details update with validation
 * - Preferences management with JSON validation
 *
 * All methods return sealed UserResult types for type-safe error handling
 */
class UserService {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Sealed class for type-safe user operation results
     * Eliminates null checks and provides clear error states
     */
    sealed class UserResult {
        data class Success(val profile: UserProfileResponse) : UserResult()
        data class Failure(val error: String, val statusCode: Int = 400) : UserResult()
    }

    /**
     * Get user profile by user ID
     *
     * @param userId User's UUID
     * @return UserResult.Success with profile, or Failure if not found
     */
    suspend fun getUserById(userId: String): UserResult {
        val profile = dbQuery {
            Users.select { Users.id eq UUID.fromString(userId) }
                .singleOrNull()
                ?.let { rowToUserProfile(it) }
        }

        return if (profile != null) {
            UserResult.Success(profile)
        } else {
            UserResult.Failure("User not found", statusCode = 404)
        }
    }

    /**
     * Update user profile details
     *
     * Process:
     * 1. Validate date format if provided
     * 2. Check if user exists
     * 3. Update only provided (non-null) fields
     * 4. Return updated profile
     *
     * @param userId User's UUID
     * @param request Update request with optional fields
     * @return UserResult.Success with updated profile, or Failure with error
     */
    suspend fun updateUserDetails(userId: String, request: UpdateProfileRequest): UserResult {
        // Validate date format if provided
        if (request.dateOfBirth != null) {
            try {
                LocalDate.parse(request.dateOfBirth, dateFormatter)
            } catch (e: Exception) {
                logger.warn { "Invalid date format for dateOfBirth: ${request.dateOfBirth}" }
                return UserResult.Failure(
                    "Invalid date format. Expected ISO format (YYYY-MM-DD)",
                    statusCode = 400
                )
            }
        }

        val updatedProfile = dbQuery {
            val userUuid = UUID.fromString(userId)

            // Check if user exists
            val existingUser = Users.select { Users.id eq userUuid }
                .singleOrNull()

            if (existingUser == null) {
                return@dbQuery null
            }

            // Update only provided fields
            Users.update({ Users.id eq userUuid }) {
                request.firstName?.let { value -> it[firstName] = value }
                request.lastName?.let { value -> it[lastName] = value }
                request.phoneNumber?.let { value -> it[phoneNumber] = value }
                request.dateOfBirth?.let { value ->
                    it[dateOfBirth] = LocalDate.parse(value, dateFormatter)
                }
                request.location?.let { value -> it[location] = value }
                request.avatarUrl?.let { value -> it[avatarUrl] = value }
            }

            logger.info { "Updated profile for user $userId" }

            // Return updated user (refetch in same dbQuery block)
            Users.select { Users.id eq userUuid }
                .singleOrNull()
                ?.let { rowToUserProfile(it) }
        }

        return if (updatedProfile != null) {
            UserResult.Success(updatedProfile)
        } else {
            UserResult.Failure("User not found", statusCode = 404)
        }
    }

    /**
     * Update user preferences (JSONB field)
     *
     * Process:
     * 1. Validate JSON format
     * 2. Check if user exists
     * 3. Update preferences
     * 4. Return updated profile
     *
     * @param userId User's UUID
     * @param request Preferences update request with JSON string
     * @return UserResult.Success with updated profile, or Failure with error
     */
    suspend fun updateUserPreferences(userId: String, request: UpdatePreferencesRequest): UserResult {
        // Validate JSON format
        try {
            kotlinx.serialization.json.Json.parseToJsonElement(request.preferences)
        } catch (e: Exception) {
            logger.warn { "Invalid JSON format for preferences: ${e.message}" }
            return UserResult.Failure(
                "Invalid JSON format for preferences",
                statusCode = 400
            )
        }

        val updatedProfile = dbQuery {
            val userUuid = UUID.fromString(userId)

            // Check if user exists
            val existingUser = Users.select { Users.id eq userUuid }
                .singleOrNull()

            if (existingUser == null) {
                return@dbQuery null
            }

            // Update preferences
            Users.update({ Users.id eq userUuid }) {
                it[preferences] = request.preferences
            }

            logger.info { "Updated preferences for user $userId" }

            // Return updated user (refetch in same dbQuery block)
            Users.select { Users.id eq userUuid }
                .singleOrNull()
                ?.let { rowToUserProfile(it) }
        }

        return if (updatedProfile != null) {
            UserResult.Success(updatedProfile)
        } else {
            UserResult.Failure("User not found", statusCode = 404)
        }
    }

    /**
     * Convert database row to UserProfileResponse
     */
    private fun rowToUserProfile(row: ResultRow): UserProfileResponse {
        return UserProfileResponse(
            id = row[Users.id].toString(),
            email = row[Users.email],
            role = row[Users.role],
            firstName = row[Users.firstName],
            lastName = row[Users.lastName],
            phoneNumber = row[Users.phoneNumber],
            dateOfBirth = row[Users.dateOfBirth]?.format(dateFormatter),
            location = row[Users.location],
            avatarUrl = row[Users.avatarUrl],
            preferences = row[Users.preferences]
        )
    }
}
