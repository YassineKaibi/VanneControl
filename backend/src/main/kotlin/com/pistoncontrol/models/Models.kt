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
