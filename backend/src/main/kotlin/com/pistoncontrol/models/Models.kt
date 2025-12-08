package com.pistoncontrol.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class User(
    val id: String,
    val email: String,
    val role: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phoneNumber: String? = null,
    val dateOfBirth: String? = null,
    val location: String? = null,
    val avatarUrl: String? = null,
    val preferences: String = "{}"
)

@Serializable
data class UserProfileResponse(
    val id: String,
    val email: String,
    val role: String,
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val dateOfBirth: String?,
    val location: String?,
    val avatarUrl: String?,
    val preferences: String
)

@Serializable
data class UpdateProfileRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val phoneNumber: String? = null,
    val dateOfBirth: String? = null,
    val location: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class UpdatePreferencesRequest(
    val preferences: String
)

@Serializable
data class Device(
    val id: String,
    val name: String,
    val ownerId: String,
    val mqttClientId: String,
    val status: String,
    val createdAt: String
)

@Serializable
data class Piston(
    val id: String,
    val deviceId: String,
    val pistonNumber: Int,
    val state: String,
    val lastTriggered: String?
)

@Serializable
data class TelemetryEvent(
    val id: Long,
    val deviceId: String,
    val pistonId: String?,
    val eventType: String,
    val payload: String?,
    val createdAt: String
)

@Serializable
data class Schedule(
    val id: String,
    val name: String,
    val deviceId: String,
    val pistonNumber: Int,
    val action: String, // "ACTIVATE" or "DEACTIVATE"
    val cronExpression: String,
    val enabled: Boolean,
    val userId: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateScheduleRequest(
    val name: String,
    val deviceId: String,
    val pistonNumber: Int,
    val action: String,
    val cronExpression: String,
    val enabled: Boolean = true
)

@Serializable
data class UpdateScheduleRequest(
    val name: String? = null,
    val action: String? = null,
    val cronExpression: String? = null,
    val enabled: Boolean? = null
)

@Serializable
data class AuditLog(
    val id: String,
    val userId: String,
    val userFullName: String? = null,  // First + Last name of admin who performed action
    val action: String,
    val targetUserId: String? = null,
    val targetUserFullName: String? = null,  // First + Last name of target user
    val targetResourceType: String? = null,
    val targetResourceId: String? = null,
    val details: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val createdAt: String
)

@Serializable
data class UpdateUserRoleRequest(
    val role: String
)

@Serializable
data class AdminStatsResponse(
    val totalUsers: Long,
    val totalAdmins: Long,
    val totalDevices: Long,
    val totalSchedules: Long,
    val recentAuditLogs: List<AuditLog>
)
