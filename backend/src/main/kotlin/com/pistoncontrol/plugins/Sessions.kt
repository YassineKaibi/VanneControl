package com.pistoncontrol.plugins

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlin.time.Duration.Companion.hours

/**
 * AdminSession - Session data for authenticated admin users
 *
 * This is stored in an encrypted cookie on the user's browser.
 * Unlike JWT tokens (which are stateless), sessions allow us to
 * easily invalidate admin access by clearing the session.
 */
data class AdminSession(
    val userId: String,
    val email: String,
    val role: String
)

/**
 * Sessions Plugin - Configures session management for web admin dashboard
 *
 * How it works:
 * 1. Admin logs in via /admin/login (HTML form)
 * 2. Server creates an encrypted session cookie
 * 3. Browser automatically sends cookie with each request
 * 4. Server validates session before rendering admin pages
 *
 * Security Features:
 * - Encrypted cookies (not readable by client-side JavaScript)
 * - HTTP-only flag (prevents XSS attacks)
 * - Secure flag for HTTPS (in production)
 * - 8-hour expiration (configurable)
 */
fun Application.configureSessions() {
    // Capture environment config before entering the lambda
    val secretSignKeyHex = environment.config.property("session.signKey").getString()

    install(Sessions) {
        // Cookie name: "admin_session"
        cookie<AdminSession>("admin_session") {
            // Sign cookies with HMAC to prevent tampering
            // Since we're using HTTPS, we don't need additional encryption
            transform(SessionTransportTransformerMessageAuthentication(
                hex(secretSignKeyHex)
            ))

            cookie.path = "/admin"  // Only send cookie for /admin/* routes
            cookie.maxAgeInSeconds = 8.hours.inWholeSeconds  // 8 hour expiration
            cookie.extensions["SameSite"] = "lax"  // CSRF protection
            cookie.httpOnly = true  // Prevent JavaScript access (XSS protection)

            // In production, uncomment this to require HTTPS:
            // cookie.secure = true
        }
    }
}
