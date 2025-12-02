package com.pistoncontrol.services

import com.pistoncontrol.mqtt.MqttManager
import com.pistoncontrol.models.Schedule
import mu.KotlinLogging
import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.quartz.Job as QuartzJob

private val logger = KotlinLogging.logger {}

/**
 * Schedule Executor with Quartz Integration
 *
 * Manages scheduled valve operations using Quartz Scheduler.
 * Loads schedules from database and executes them via MQTT.
 */
class ScheduleExecutor(
    private val scheduleService: ScheduleService,
    private val mqttManager: MqttManager
) {
    private val scheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Start the scheduler and load all enabled schedules
     */
    fun start() {
        logger.info { "Starting Schedule Executor..." }

        // Start Quartz scheduler
        scheduler.start()
        logger.info { "Quartz Scheduler started" }

        // Load and schedule all enabled schedules
        scope.launch {
            try {
                loadAndScheduleAll()
                logger.info { "All enabled schedules loaded and scheduled" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load schedules" }
            }
        }

        logger.info { "✅ Schedule Executor initialized" }
    }

    /**
     * Stop the scheduler gracefully
     */
    fun stop() {
        logger.info { "Stopping Schedule Executor..." }

        try {
            scheduler.shutdown(true)
            scope.cancel()
            logger.info { "✅ Schedule Executor stopped" }
        } catch (e: Exception) {
            logger.error(e) { "Error stopping scheduler" }
        }
    }

    /**
     * Load all enabled schedules and add them to Quartz
     */
    private suspend fun loadAndScheduleAll() {
        val schedules = scheduleService.getEnabledSchedules()
        logger.info { "Loading ${schedules.size} enabled schedules" }

        schedules.forEach { schedule ->
            try {
                addScheduleToQuartz(schedule)
                logger.info { "Scheduled: ${schedule.name} (${schedule.cronExpression})" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to schedule ${schedule.name}" }
            }
        }
    }

    /**
     * Add a new schedule to Quartz
     */
    suspend fun addSchedule(schedule: Schedule) {
        if (!schedule.enabled) {
            logger.debug { "Schedule ${schedule.id} is disabled, not scheduling" }
            return
        }

        try {
            addScheduleToQuartz(schedule)
            logger.info { "Added schedule: ${schedule.name}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to add schedule ${schedule.id}" }
            throw e
        }
    }

    /**
     * Remove a schedule from Quartz
     */
    fun removeSchedule(scheduleId: String) {
        try {
            val jobKey = JobKey.jobKey(scheduleId, "schedules")
            scheduler.deleteJob(jobKey)
            logger.info { "Removed schedule: $scheduleId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to remove schedule $scheduleId" }
            throw e
        }
    }

    /**
     * Update an existing schedule in Quartz
     */
    suspend fun updateSchedule(schedule: Schedule) {
        try {
            // Remove old schedule
            removeSchedule(schedule.id)

            // Add updated schedule if enabled
            if (schedule.enabled) {
                addScheduleToQuartz(schedule)
                logger.info { "Updated schedule: ${schedule.name}" }
            } else {
                logger.info { "Schedule ${schedule.name} disabled, not rescheduling" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update schedule ${schedule.id}" }
            throw e
        }
    }

    /**
     * Reload all schedules (useful for manual refresh)
     */
    suspend fun reloadAllSchedules() {
        logger.info { "Reloading all schedules..." }

        try {
            // Clear all existing scheduled jobs
            scheduler.clear()

            // Reload from database
            loadAndScheduleAll()

            logger.info { "✅ All schedules reloaded" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to reload schedules" }
            throw e
        }
    }

    /**
     * Internal method to add a schedule to Quartz
     */
    private fun addScheduleToQuartz(schedule: Schedule) {
        // Create job detail
        val jobDetail = JobBuilder.newJob(ValveOperationJob::class.java)
            .withIdentity(schedule.id, "schedules")
            .usingJobData("scheduleId", schedule.id)
            .usingJobData("scheduleName", schedule.name)
            .usingJobData("deviceId", schedule.deviceId)
            .usingJobData("pistonNumber", schedule.pistonNumber)
            .usingJobData("action", schedule.action)
            .build()

        // Create trigger with cron expression
        val trigger = TriggerBuilder.newTrigger()
            .withIdentity("${schedule.id}-trigger", "schedules")
            .withSchedule(
                CronScheduleBuilder.cronSchedule(schedule.cronExpression)
                    .withMisfireHandlingInstructionFireAndProceed()
            )
            .build()

        // Schedule the job
        scheduler.scheduleJob(jobDetail, trigger)

        logger.debug {
            "Scheduled job: ${schedule.name} for device ${schedule.deviceId}, " +
                    "piston ${schedule.pistonNumber}, action ${schedule.action}"
        }
    }

    /**
     * Quartz Job implementation for executing valve operations
     */
    class ValveOperationJob : QuartzJob {
        override fun execute(context: JobExecutionContext) {
            val dataMap = context.jobDetail.jobDataMap

            val scheduleId = dataMap.getString("scheduleId")
            val scheduleName = dataMap.getString("scheduleName")
            val deviceId = dataMap.getString("deviceId")
            val pistonNumber = dataMap.getInt("pistonNumber")
            val action = dataMap.getString("action")

            logger.info {
                "⏰ Executing scheduled operation: $scheduleName - " +
                        "$action piston $pistonNumber on device $deviceId"
            }

            try {
                // Get MQTT manager from job context
                val mqttManager = context.scheduler.context.get("mqttManager") as? MqttManager

                if (mqttManager == null) {
                    logger.error { "MqttManager not found in scheduler context" }
                    return
                }

                // Build command string (e.g., "activate:3" or "deactivate:5")
                val command = "${action.lowercase()}:$pistonNumber"

                // Publish command via MQTT
                mqttManager.publishCommand(deviceId, command, useBinary = true)

                logger.info {
                    "✅ Successfully executed schedule: $scheduleName - " +
                            "sent $action command for piston $pistonNumber"
                }

            } catch (e: Exception) {
                logger.error(e) {
                    "❌ Failed to execute schedule $scheduleName: ${e.message}"
                }
                throw JobExecutionException(e)
            }
        }
    }

    /**
     * Store MQTT manager in scheduler context for job access
     */
    fun setMqttManager(mqttManager: MqttManager) {
        scheduler.context.put("mqttManager", mqttManager)
    }
}
