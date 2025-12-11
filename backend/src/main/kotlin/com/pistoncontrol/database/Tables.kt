package com.pistoncontrol.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

object Users : Table("users") {
    val id = uuid("id").autoGenerate()
    val email = text("email")
    val passwordHash = text("password_hash")
    val role = text("role")
    val firstName = text("first_name").nullable()
    val lastName = text("last_name").nullable()
    val phoneNumber = text("phone_number").nullable()
    val dateOfBirth = date("date_of_birth").nullable()
    val location = text("location").nullable()
    val avatarUrl = text("avatar_url").nullable()
    val preferences = jsonb("preferences").default("{}")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object Devices : Table("devices") {
    val id = uuid("id").autoGenerate()
    val name = text("name")
    val ownerId = uuid("owner_id")
    val mqttClientId = text("mqtt_client_id")
    val status = text("status")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object Pistons : Table("pistons") {
    val id = uuid("id").autoGenerate()
    val deviceId = uuid("device_id")
    val pistonNumber = integer("piston_number")
    val state = text("state")
    val lastTriggered = timestamp("last_triggered").nullable()

    override val primaryKey = PrimaryKey(id)
}

object Telemetry : Table("telemetry") {
    val id = long("id")
    val deviceId = uuid("device_id")
    val pistonId = uuid("piston_id").nullable()
    val eventType = text("event_type")
    
    // ✅ SOLUTION: Use our custom jsonb() function
    // This properly casts to JSONB in PostgreSQL
    val payload = jsonb("payload").nullable()
    
    val createdAt = timestamp("created_at")
    
    override val primaryKey = PrimaryKey(id)
}

object AuthTokens : Table("auth_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val refreshToken = text("refresh_token")
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object Schedules : Table("schedules") {
    val id = uuid("id").autoGenerate()
    val name = text("name")
    val deviceId = uuid("device_id")
    val pistonNumber = integer("piston_number")
    val action = text("action") // "ACTIVATE" or "DEACTIVATE"
    val cronExpression = text("cron_expression")
    val enabled = bool("enabled").default(true)
    val userId = uuid("user_id")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object AuditLogs : Table("audit_logs") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id") // Admin who performed the action
    val action = text("action") // e.g., "UPDATE_USER_ROLE", "DELETE_USER", "VIEW_AUDIT_LOGS"
    val targetUserId = uuid("target_user_id").nullable() // User affected by the action (if applicable)
    val targetResourceType = text("target_resource_type").nullable() // e.g., "USER", "DEVICE", "SCHEDULE"
    val targetResourceId = text("target_resource_id").nullable() // ID of the affected resource
    val details = jsonb("details").nullable() // Additional context (old values, new values, etc.)
    val ipAddress = text("ip_address").nullable()
    val userAgent = text("user_agent").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
