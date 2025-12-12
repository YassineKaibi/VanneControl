package com.pistoncontrol.services

import com.pistoncontrol.database.DatabaseFactory.dbQuery
import com.pistoncontrol.database.Devices
import com.pistoncontrol.database.Pistons
import com.pistoncontrol.database.Telemetry
import com.pistoncontrol.mqtt.MqttManager
import com.pistoncontrol.routes.*
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * DeviceService - Centralized Device and Piston Management Business Logic
 *
 * This service handles all device-related operations including:
 * - Device CRUD operations
 * - Ownership verification
 * - Piston state management
 * - MQTT command publishing
 * - Device-to-piston relationship management
 *
 * Security Notes:
 * - All device operations verify ownership before execution
 * - Prevents unauthorized access to devices
 * - Piston numbers validated (1-8 range)
 * - MQTT commands use binary protocol for efficiency
 */
class DeviceService(private val mqttManager: MqttManager) {

    companion object {
        private const val MIN_PISTON_NUMBER = 1
        private const val MAX_PISTON_NUMBER = 8
        private const val DEFAULT_DEVICE_STATUS = "offline"
    }

    /**
     * Sealed class for type-safe device operation results
     * Eliminates null checks and provides clear error states
     */
    sealed class DeviceResult {
        data class Success(val device: DeviceResponse) : DeviceResult()
        data class DeviceWithPistonsSuccess(val device: DeviceWithPistonsResponse) : DeviceResult()
        data class DevicesListSuccess(val devices: List<DeviceWithPistonsResponse>) : DeviceResult()
        data class PistonSuccess(val piston: PistonWithIdResponse, val message: String) : DeviceResult()
        data class PistonsListSuccess(val pistons: List<PistonResponse>) : DeviceResult()
        data class DeviceStatsSuccess(val stats: DeviceStatsResponse) : DeviceResult()
        data class TelemetryListSuccess(val telemetry: List<TelemetryEventResponse>) : DeviceResult()
        data class Failure(val error: String, val statusCode: Int = 400) : DeviceResult()
    }

    /**
     * Create a new device for the specified user
     *
     * Process:
     * 1. Check if MQTT client ID already exists
     * 2. Insert device into database with offline status
     * 3. Return created device
     *
     * @param userId Owner's UUID
     * @param name Device name
     * @param mqttClientId Unique MQTT client identifier
     * @return DeviceResult.Success with device, or Failure if duplicate exists
     */
    suspend fun createDevice(userId: UUID, name: String, mqttClientId: String): DeviceResult {
        val deviceId = dbQuery {
            // Check for duplicate MQTT client ID across all devices
            val existing = Devices.select { Devices.mqttClientId eq mqttClientId }.singleOrNull()
            if (existing != null) {
                return@dbQuery null
            }

            // Create new device with default offline status
            Devices.insert {
                it[ownerId] = userId
                it[Devices.name] = name
                it[Devices.mqttClientId] = mqttClientId
                it[status] = DEFAULT_DEVICE_STATUS
            } get Devices.id
        }

        return if (deviceId == null) {
            DeviceResult.Failure(
                "Device with this MQTT client ID already exists",
                statusCode = 409
            )
        } else {
            DeviceResult.Success(
                DeviceResponse(
                    id = deviceId.toString(),
                    name = name,
                    mqttClientId = mqttClientId,
                    status = DEFAULT_DEVICE_STATUS
                )
            )
        }
    }

    /**
     * Get all devices owned by the specified user
     * Includes all pistons for each device
     *
     * @param userId Owner's UUID
     * @return DeviceResult.DevicesListSuccess with list of devices and their pistons
     */
    suspend fun getUserDevices(userId: UUID): DeviceResult {
        val devicesWithPistons = dbQuery {
            Devices.select { Devices.ownerId eq userId }.map { deviceRow ->
                val deviceId = deviceRow[Devices.id]

                // Fetch all pistons for this device
                val pistons = getPistonsForDeviceInternal(deviceId)

                DeviceWithPistonsResponse(
                    id = deviceId.toString(),
                    name = deviceRow[Devices.name],
                    device_id = deviceRow[Devices.mqttClientId],
                    status = deviceRow[Devices.status],
                    last_seen = null,  // TODO: implement timestamp tracking
                    pistons = pistons
                )
            }
        }

        return DeviceResult.DevicesListSuccess(devicesWithPistons)
    }

    /**
     * Get a specific device by ID
     * Verifies user owns the device before returning
     *
     * @param userId Owner's UUID
     * @param deviceId Device UUID
     * @return DeviceResult.DeviceWithPistonsSuccess or Failure if not found/not owned
     */
    suspend fun getDeviceById(userId: UUID, deviceId: UUID): DeviceResult {
        val deviceData = dbQuery {
            // Check ownership and fetch device in single query
            val device = Devices.select {
                (Devices.id eq deviceId) and (Devices.ownerId eq userId)
            }.singleOrNull()

            if (device == null) {
                return@dbQuery null
            }

            // Fetch pistons for this device
            val pistons = getPistonsForDeviceInternal(deviceId)

            DeviceWithPistonsResponse(
                id = device[Devices.id].toString(),
                name = device[Devices.name],
                device_id = device[Devices.mqttClientId],
                status = device[Devices.status],
                last_seen = null,
                pistons = pistons
            )
        }

        return if (deviceData == null) {
            DeviceResult.Failure("Device not found", statusCode = 404)
        } else {
            DeviceResult.DeviceWithPistonsSuccess(deviceData)
        }
    }

    /**
     * Get all pistons for a specific device
     * Verifies user owns the device
     *
     * @param userId Owner's UUID
     * @param deviceId Device UUID
     * @return DeviceResult.PistonsListSuccess or Failure if not owned
     */
    suspend fun getPistonsForDevice(userId: UUID, deviceId: UUID): DeviceResult {
        // Verify ownership first
        if (!verifyOwnership(userId, deviceId)) {
            return DeviceResult.Failure("Access denied", statusCode = 403)
        }

        val pistons = dbQuery {
            getPistonsForDeviceInternal(deviceId)
        }

        return DeviceResult.PistonsListSuccess(pistons)
    }

    /**
     * Control a piston (activate or deactivate)
     *
     * Process:
     * 1. Verify user owns the device
     * 2. Validate piston number (1-8)
     * 3. Send MQTT command to device
     * 4. Update piston state in database
     * 5. Return updated piston state
     *
     * @param userId Owner's UUID
     * @param deviceId Device UUID
     * @param pistonNumber Piston number (1-8)
     * @param action "activate" or "deactivate"
     * @return DeviceResult.PistonSuccess or Failure
     */
    suspend fun controlPiston(
        userId: UUID,
        deviceId: UUID,
        pistonNumber: Int,
        action: String
    ): DeviceResult {
        // Validate piston number
        if (pistonNumber !in MIN_PISTON_NUMBER..MAX_PISTON_NUMBER) {
            return DeviceResult.Failure("Invalid piston number. Must be between 1 and 8")
        }

        // Verify ownership
        if (!verifyOwnership(userId, deviceId)) {
            return DeviceResult.Failure("Access denied", statusCode = 403)
        }

        // Publish MQTT command using binary protocol
        mqttManager.publishCommand(
            deviceId.toString(),
            "$action:$pistonNumber",
            useBinary = true
        )

        // Update database state
        val newState = if (action == "activate") "active" else "inactive"
        val updatedPiston = updatePistonState(deviceId, pistonNumber, newState)

        return DeviceResult.PistonSuccess(
            piston = updatedPiston,
            message = "Piston ${if (newState == "active") "activated" else "deactivated"}"
        )
    }

    /**
     * Verify that a user owns a specific device
     *
     * Security-critical method used before all device operations
     * Prevents unauthorized access to devices
     *
     * @param userId Owner's UUID
     * @param deviceId Device UUID
     * @return true if user owns device, false otherwise
     */
    private suspend fun verifyOwnership(userId: UUID, deviceId: UUID): Boolean {
        return dbQuery {
            Devices.select {
                (Devices.id eq deviceId) and (Devices.ownerId eq userId)
            }.singleOrNull() != null
        }
    }

    /**
     * Update or insert piston state in database
     *
     * If piston record exists: updates state and last_triggered timestamp
     * If piston record doesn't exist: creates new record
     * Also records telemetry event for tracking valve history
     *
     * @param deviceId Device UUID
     * @param pistonNumber Piston number (1-8)
     * @param state New state ("active" or "inactive")
     * @return Updated piston with ID
     */
    private suspend fun updatePistonState(
        deviceId: UUID,
        pistonNumber: Int,
        state: String
    ): PistonWithIdResponse {
        return dbQuery {
            val now = Instant.now()

            // Check if piston record exists
            val existing = Pistons.select {
                (Pistons.deviceId eq deviceId) and (Pistons.pistonNumber eq pistonNumber)
            }.singleOrNull()

            val pistonId: UUID

            if (existing != null) {
                // Update existing piston
                pistonId = existing[Pistons.id]
                Pistons.update({
                    (Pistons.deviceId eq deviceId) and (Pistons.pistonNumber eq pistonNumber)
                }) {
                    it[Pistons.state] = state
                    it[lastTriggered] = now
                }
            } else {
                // Create new piston record
                pistonId = Pistons.insert {
                    it[Pistons.deviceId] = deviceId
                    it[Pistons.pistonNumber] = pistonNumber
                    it[Pistons.state] = state
                    it[lastTriggered] = now
                } get Pistons.id
            }

            // Record telemetry event for valve history tracking
            val jsonPayload = buildJsonObject {
                put("piston_number", pistonNumber)
                put("timestamp", now.toEpochMilli())
            }.toString()

            Telemetry.insert {
                it[Telemetry.deviceId] = deviceId
                it[Telemetry.pistonId] = pistonId
                it[eventType] = if (state == "active") "activated" else "deactivated"
                it[payload] = jsonPayload
                it[createdAt] = now
            }

            PistonWithIdResponse(
                id = pistonId.toString(),
                piston_number = pistonNumber,
                state = state,
                last_triggered = now.toString()
            )
        }
    }

    /**
     * Internal helper to fetch pistons for a device
     * Does NOT check ownership - caller must verify first
     *
     * Returns all 8 pistons (1-8) even if they don't exist in the database yet.
     * Non-existent pistons are returned with default values (inactive state, null last_triggered).
     * This enables lazy creation - pistons are created when first activated.
     *
     * @param deviceId Device UUID
     * @return List of all 8 pistons for the device (some may not exist in DB yet)
     */
    private fun getPistonsForDeviceInternal(deviceId: UUID): List<PistonResponse> {
        // Fetch existing pistons from database
        val existingPistons = Pistons.select { Pistons.deviceId eq deviceId }
            .associate { it[Pistons.pistonNumber] to it }

        // Return all 8 pistons (1-8), using DB values for existing ones and defaults for non-existent
        return (MIN_PISTON_NUMBER..MAX_PISTON_NUMBER).map { pistonNumber ->
            val existing = existingPistons[pistonNumber]
            PistonResponse(
                piston_number = pistonNumber,
                state = existing?.get(Pistons.state) ?: "inactive",
                last_triggered = existing?.get(Pistons.lastTriggered)?.toString()
            )
        }
    }

    /**
     * Get statistics for a specific device
     *
     * @param userId Owner's UUID for authorization
     * @param deviceId Device UUID
     * @return DeviceResult with device statistics
     */
    suspend fun getDeviceStats(userId: UUID, deviceId: UUID): DeviceResult {
        return dbQuery {
            // Verify ownership
            val device = Devices.select {
                (Devices.id eq deviceId) and (Devices.ownerId eq userId)
            }.singleOrNull()

            if (device == null) {
                return@dbQuery DeviceResult.Failure("Device not found", 404)
            }

            // Get piston statistics
            val pistons = Pistons.select { Pistons.deviceId eq deviceId }.toList()
            val activePistons = pistons.count { it[Pistons.state] == "active" }
            val totalPistons = pistons.size

            // Get telemetry count
            val totalEvents = Telemetry.select { Telemetry.deviceId eq deviceId }.count()

            // Get last activity timestamp
            val lastActivity = Telemetry
                .select { Telemetry.deviceId eq deviceId }
                .orderBy(Telemetry.createdAt to SortOrder.DESC)
                .limit(1)
                .map { it[Telemetry.createdAt] }
                .firstOrNull()

            DeviceResult.DeviceStatsSuccess(
                DeviceStatsResponse(
                    deviceId = deviceId.toString(),
                    deviceName = device[Devices.name],
                    status = device[Devices.status],
                    activePistons = activePistons,
                    totalPistons = totalPistons,
                    totalEvents = totalEvents,
                    lastActivity = lastActivity?.toString()
                )
            )
        }
    }

    /**
     * Get telemetry/history for user's devices with optional filtering
     *
     * @param userId User's UUID for authorization
     * @param deviceId Optional device filter
     * @param pistonNumber Optional piston number filter (1-8)
     * @param action Optional action filter ("activated" or "deactivated")
     * @param startDate Optional start date (ISO format)
     * @param endDate Optional end date (ISO format)
     * @param limit Maximum number of results
     * @return DeviceResult with telemetry events
     */
    suspend fun getUserTelemetry(
        userId: UUID,
        deviceId: UUID? = null,
        pistonNumber: Int? = null,
        action: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        limit: Int = 100
    ): DeviceResult {
        return dbQuery {
            // Get all device IDs for the user (or just the specified device)
            val userDeviceIds = if (deviceId != null) {
                // Verify ownership of specified device
                val device = Devices.select {
                    (Devices.id eq deviceId) and (Devices.ownerId eq userId)
                }.singleOrNull()

                if (device == null) {
                    return@dbQuery DeviceResult.Failure("Device not found", 404)
                }
                listOf(deviceId)
            } else {
                Devices.select { Devices.ownerId eq userId }.map { it[Devices.id] }
            }

            if (userDeviceIds.isEmpty()) {
                return@dbQuery DeviceResult.TelemetryListSuccess(emptyList())
            }

            // Build query with filters
            var query = Telemetry.select {
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

            var results = query
                .orderBy(Telemetry.createdAt to SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    TelemetryEventResponse(
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
                results = results.filter { event ->
                    event.payload?.contains("\"piston_number\":$pistonNumber") == true ||
                    event.payload?.contains("\"pistonNumber\":$pistonNumber") == true
                }
            }

            DeviceResult.TelemetryListSuccess(results)
        }
    }
}
