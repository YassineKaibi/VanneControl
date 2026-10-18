package com.pistoncontrol

import com.pistoncontrol.database.DatabaseFactory
import com.pistoncontrol.mqtt.MqttManager
import com.pistoncontrol.services.DeviceMessageHandler
import com.pistoncontrol.services.EmailService
import com.pistoncontrol.services.FlowFaultDetector
import com.pistoncontrol.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    logger.info { "🚀 Starting Piston Control Backend..." }
    
    // ════════════════════════════════════════════════════════════════
    // STEP 1: Initialize Database
    // ════════════════════════════════════════════════════════════════
    try {
        DatabaseFactory.init()
        logger.info { "✅ Database initialized successfully" }
    } catch (e: Exception) {
        logger.error(e) { "❌ Failed to initialize database" }
        throw e
    }
    
    // ════════════════════════════════════════════════════════════════
    // STEP 2: Initialize MQTT Manager with Binary Protocol Support
    // ════════════════════════════════════════════════════════════════
    val mqttBroker = System.getenv("MQTT_BROKER") 
        ?: throw IllegalStateException("MQTT_BROKER not set")
    
    val mqttManager = MqttManager(
        broker = mqttBroker,
        clientId = "ktor-backend-${System.currentTimeMillis()}"
    )
    
    try {
        mqttManager.connect()
        logger.info { "✅ MQTT Manager connected to $mqttBroker" }
        logger.info { "📡 Binary protocol parser enabled" }
    } catch (e: Exception) {
        logger.error(e) { "❌ Failed to connect to MQTT broker" }
        throw e
    }
    
    // ════════════════════════════════════════════════════════════════
    // STEP 3: Initialize Device Message Handler
    // ════════════════════════════════════════════════════════════════
    val flowFaultDetector = FlowFaultDetector(
        gracePeriodMillis = (System.getenv("FLOW_FAULT_GRACE_SECONDS") ?: "15").toLong() * 1000,
        noFlowThresholdLpm = (System.getenv("FLOW_NO_FLOW_THRESHOLD_LPM") ?: "0.5").toFloat(),
        leakThresholdLpm = (System.getenv("FLOW_LEAK_THRESHOLD_LPM") ?: "0.5").toFloat()
    )
    val messageHandler = DeviceMessageHandler(flowFaultDetector)

    // Subscribe to MQTT message flow and process messages
    GlobalScope.launch {
        logger.info { "🔄 Starting MQTT message processor..." }

        mqttManager.messageFlow.collect { message ->
            try {
                // Process each message through our handler
                messageHandler.handleMessage(message)

                logger.debug {
                    "Processed ${message.messageType} from ${message.deviceId}"
                }
            } catch (e: Exception) {
                logger.error(e) {
                    "Error processing message from ${message.deviceId}"
                }
            }
        }
    }

    logger.info { "✅ Device message handler initialized" }

    // ════════════════════════════════════════════════════════════════
    // STEP 3.5: Initialize Schedule Executor with Quartz
    // ════════════════════════════════════════════════════════════════
    val scheduleService = com.pistoncontrol.services.ScheduleService()
    val scheduleExecutor = com.pistoncontrol.services.ScheduleExecutor(scheduleService, mqttManager)

    try {
        scheduleExecutor.setMqttManager(mqttManager)
        scheduleExecutor.start()
        logger.info { "✅ Schedule Executor started with Quartz" }
    } catch (e: Exception) {
        logger.error(e) { "❌ Failed to start Schedule Executor" }
    }

    // ════════════════════════════════════════════════════════════════
    // STEP 3.6: Initialize Email Service (SMTP)
    // ════════════════════════════════════════════════════════════════
    val emailService = EmailService(
        smtpHost = System.getenv("SMTP_HOST") ?: "",
        smtpPort = (System.getenv("SMTP_PORT") ?: "587").toInt(),
        smtpUsername = System.getenv("SMTP_USERNAME") ?: "",
        smtpPassword = System.getenv("SMTP_PASSWORD") ?: "",
        fromAddress = System.getenv("SMTP_FROM") ?: "noreply@vannecontrol.com"
    )
    logger.info { "✅ Email Service configured" }

    // ════════════════════════════════════════════════════════════════
    // STEP 4: Configure Ktor Plugins
    // ════════════════════════════════════════════════════════════════
    configureSerialization()
    logger.info { "✅ JSON serialization configured" }
    
    configureSecurity()
    logger.info { "✅ JWT authentication configured" }

    // Admin Web Dashboard plugins
    configureTemplating()
    logger.info { "✅ FreeMarker templating configured" }

    configureSessions()
    logger.info { "✅ Session management configured" }

    configureStaticContent()
    logger.info { "✅ Static content serving configured" }

    configureWebSockets()
    logger.info { "✅ WebSocket support configured" }
    
    configureMonitoring()
    logger.info { "✅ Request monitoring configured" }

    configureRouting(mqttManager, messageHandler, scheduleService, scheduleExecutor, emailService)
    logger.info { "✅ REST API routes configured" }

    // ════════════════════════════════════════════════════════════════
    // STEP 5: Graceful Shutdown Handler
    // ════════════════════════════════════════════════════════════════
    environment.monitor.subscribe(ApplicationStopped) {
        logger.info { "🛑 Shutting down gracefully..." }

        try {
            scheduleExecutor.stop()
            logger.info { "✅ Schedule Executor stopped" }
        } catch (e: Exception) {
            logger.error(e) { "Error stopping Schedule Executor" }
        }

        try {
            mqttManager.disconnect()
            logger.info { "✅ MQTT disconnected" }
        } catch (e: Exception) {
            logger.error(e) { "Error disconnecting MQTT" }
        }
    }
    
    // ════════════════════════════════════════════════════════════════
    // STARTUP COMPLETE
    // ════════════════════════════════════════════════════════════════
    logger.info { """

        ╔══════════════════════════════════════════════════════════╗
        ║                                                          ║
        ║           🔧 Piston Control Backend - READY              ║
        ║                                                          ║
        ║              Features Enabled:                           ║
        ║                ✓ JSON Backward Compatibility             ║
        ║                ✓ Real-time WebSocket Updates             ║
        ║                ✓ Secure JWT Authentication               ║
        ║                ✓ MQTT Device Communication               ║
        ║                ✓ Scheduled Operations (Quartz)           ║
        ║                                                          ║
        ╚══════════════════════════════════════════════════════════╝

    """.trimIndent() }
}
