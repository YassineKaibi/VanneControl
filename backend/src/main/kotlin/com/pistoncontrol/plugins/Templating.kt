package com.pistoncontrol.plugins

import freemarker.cache.ClassTemplateLoader
import io.ktor.server.application.*
import io.ktor.server.freemarker.*

/**
 * Templating Plugin - Configures FreeMarker for server-side HTML rendering
 *
 * FreeMarker templates will be stored in src/main/resources/templates/
 * This enables us to serve dynamic HTML pages for the admin dashboard
 * without requiring a separate frontend build process.
 *
 * Why FreeMarker?
 * - Lightweight (no heavy JavaScript frameworks needed)
 * - Server-side rendering (SEO-friendly, fast initial load)
 * - Perfect for admin dashboards with forms and tables
 * - Familiar template syntax for developers
 */
fun Application.configureTemplating() {
    install(FreeMarker) {
        // Load templates from resources/templates directory
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }
}
