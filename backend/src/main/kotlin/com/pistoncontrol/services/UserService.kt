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
 * User Service
 *
 * Handles user profile management operations
 */
class UserService {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Get user profile by user ID
     * Returns full profile including all optional fields
     */
    suspend fun getUserById(userId: String): UserProfileResponse? {
        return dbQuery {
            Users.select { Users.id eq UUID.fromString(userId) }
                .singleOrNull()
                ?.let { rowToUserProfile(it) }
        }
    }

    /**
     * Update user profile details
     * Only updates fields that are provided (non-null)
     */
    suspend fun updateUserDetails(userId: String, request: UpdateProfileRequest): UserProfileResponse? {
        return dbQuery {
            val userUuid = UUID.fromString(userId)

            // Check if user exists
            val existingUser = Users.select { Users.id eq userUuid }
                .singleOrNull() ?: return@dbQuery null

            // Update only provided fields
            Users.update({ Users.id eq userUuid }) {
                request.firstName?.let { value -> it[firstName] = value }
                request.lastName?.let { value -> it[lastName] = value }
                request.phoneNumber?.let { value -> it[phoneNumber] = value }
                request.dateOfBirth?.let { value ->
                    try {
                        it[dateOfBirth] = LocalDate.parse(value, dateFormatter)
                    } catch (e: Exception) {
                        logger.warn { "Invalid date format for dateOfBirth: $value" }
                    }
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
    }

    /**
     * Update user preferences (JSONB field)
     * Validates that preferences is valid JSON
     */
    suspend fun updateUserPreferences(userId: String, request: UpdatePreferencesRequest): UserProfileResponse? {
        return dbQuery {
            val userUuid = UUID.fromString(userId)

            // Check if user exists
            val existingUser = Users.select { Users.id eq userUuid }
                .singleOrNull() ?: return@dbQuery null

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
