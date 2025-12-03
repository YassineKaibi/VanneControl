package com.pistoncontrol.plugins

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

/**
 * Static Content Plugin - Serves static files (CSS, JS, images)
 *
 * Files will be served from src/main/resources/static/
 *
 * Directory structure:
 * resources/static/
 * ├── css/
 * │   └── admin.css       (Admin dashboard styles)
 * ├── js/
 * │   └── admin.js        (Dashboard interactivity)
 * └── img/
 *     └── logo.png        (Company logo - optional)
 *
 * Why static files?
 * - CSS for styling the admin dashboard
 * - Minimal JavaScript for interactivity (no framework needed)
 * - Images/icons for better UX
 * - All served efficiently by Ktor
 */
fun Application.configureStaticContent() {
    routing {
        // Serve static files from /admin/static/*
        static("/admin/static") {
            resources("static")
        }
    }
}
