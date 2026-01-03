package com.pistoncontrol.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger {}

/**
 * Avatar Routes - Handles profile picture upload and serving
 *
 * Endpoints:
 * - POST /user/avatar - Upload avatar image (multipart/form-data)
 * - DELETE /user/avatar - Remove avatar
 * - GET /avatars/{filename} - Serve avatar image (public)
 *
 * Storage:
 * - Avatars stored in /app/uploads/avatars/ (container) or ./uploads/avatars/ (local)
 * - Filename format: {userId}.{extension}
 * - Supported formats: JPG, JPEG, PNG, WebP
 * - Max size: 5MB
 */

@Serializable
data class AvatarUploadResponse(
    val message: String,
    val avatarUrl: String
)

@Serializable
data class AvatarDeleteResponse(
    val message: String
)

// Configuration
private const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5MB
private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
private val ALLOWED_CONTENT_TYPES = setOf(
    ContentType.Image.JPEG,
    ContentType.Image.PNG,
    ContentType("image", "webp")
)

/**
 * Get the uploads directory, creating it if necessary
 */
private fun getUploadsDir(): File {
    // Try container path first, fallback to local
    val dir = if (File("/app").exists()) {
        File("/app/uploads/avatars")
    } else {
        File("./uploads/avatars")
    }

    if (!dir.exists()) {
        dir.mkdirs()
        logger.info { "Created avatar uploads directory: ${dir.absolutePath}" }
    }

    return dir
}

/**
 * Get file extension from content type
 */
private fun getExtensionFromContentType(contentType: ContentType?): String {
    return when (contentType) {
        ContentType.Image.JPEG -> "jpg"
        ContentType.Image.PNG -> "png"
        ContentType("image", "webp") -> "webp"
        else -> "jpg"
    }
}

/**
 * Delete existing avatar files for a user (any extension)
 */
private fun deleteExistingAvatars(userId: String, uploadsDir: File) {
    ALLOWED_EXTENSIONS.forEach { ext ->
        val existingFile = File(uploadsDir, "$userId.$ext")
        if (existingFile.exists()) {
            existingFile.delete()
            logger.info { "Deleted existing avatar: ${existingFile.name}" }
        }
    }
}

fun Route.avatarRoutes(baseUrl: String) {
    val uploadsDir = getUploadsDir()

    // ═══════════════════════════════════════════════════════════════
    // PUBLIC ROUTE: Serve avatar images
    // ═══════════════════════════════════════════════════════════════
    route("/avatars") {
        /**
         * GET /avatars/{filename}
         * Serve avatar image file
         *
         * No authentication required - avatars are public
         */
        get("/{filename}") {
            val filename = call.parameters["filename"]

            if (filename.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing filename"))
                return@get
            }

            // Security: Only allow specific extensions, no path traversal
            val sanitizedFilename = filename.replace(Regex("[^a-zA-Z0-9.-]"), "")
            val extension = sanitizedFilename.substringAfterLast('.', "").lowercase()

            if (extension !in ALLOWED_EXTENSIONS) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid file type"))
                return@get
            }

            val file = File(uploadsDir, sanitizedFilename)

            if (!file.exists() || !file.isFile) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Avatar not found"))
                return@get
            }

            // Ensure file is within uploads directory (prevent path traversal)
            if (!file.canonicalPath.startsWith(uploadsDir.canonicalPath)) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("Access denied"))
                return@get
            }

            // Set content type based on extension
            val contentType = when (extension) {
                "jpg", "jpeg" -> ContentType.Image.JPEG
                "png" -> ContentType.Image.PNG
                "webp" -> ContentType("image", "webp")
                else -> ContentType.Application.OctetStream
            }

            // Cache headers for performance
            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400") // 24 hours
            call.respondFile(file)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // AUTHENTICATED ROUTES: Upload and delete avatar
    // ═══════════════════════════════════════════════════════════════
    authenticate("auth-jwt") {
        route("/user") {
            /**
             * POST /user/avatar
             * Upload new avatar image
             *
             * Request: multipart/form-data with "avatar" file field
             * Max size: 5MB
             * Supported: JPG, JPEG, PNG, WebP
             *
             * Response:
             * {
             *   "message": "Avatar uploaded successfully",
             *   "avatarUrl": "http://server/avatars/user-id.jpg"
             * }
             */
            post("/avatar") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()

                    logger.info { "Avatar upload request from user: $userId" }

                    // Parse multipart data
                    val multipart = call.receiveMultipart()
                    var avatarFile: File? = null
                    var savedExtension: String? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                if (part.name == "avatar") {
                                    // Validate content type
                                    val contentType = part.contentType
                                    if (contentType == null || contentType !in ALLOWED_CONTENT_TYPES) {
                                        part.dispose()
                                        call.respond(
                                            HttpStatusCode.BadRequest,
                                            ErrorResponse("Invalid file type. Allowed: JPG, PNG, WebP")
                                        )
                                        return@post
                                    }

                                    // Read file bytes
                                    val fileBytes = part.streamProvider().readBytes()

                                    // Validate size
                                    if (fileBytes.size > MAX_FILE_SIZE) {
                                        part.dispose()
                                        call.respond(
                                            HttpStatusCode.BadRequest,
                                            ErrorResponse("File too large. Maximum size: 5MB")
                                        )
                                        return@post
                                    }

                                    // Delete existing avatars for this user
                                    deleteExistingAvatars(userId, uploadsDir)

                                    // Save new avatar
                                    savedExtension = getExtensionFromContentType(contentType)
                                    val filename = "$userId.$savedExtension"
                                    avatarFile = File(uploadsDir, filename)
                                    avatarFile!!.writeBytes(fileBytes)

                                    logger.info { "Saved avatar: $filename (${fileBytes.size} bytes)" }
                                }
                            }
                            else -> {}
                        }
                        part.dispose()
                    }

                    if (avatarFile == null || savedExtension == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("No avatar file provided. Use 'avatar' as field name.")
                        )
                        return@post
                    }

                    // Build the avatar URL
                    val avatarUrl = "${baseUrl}avatars/$userId.$savedExtension"

                    // Update user's avatarUrl in database
                    updateUserAvatarUrl(userId, avatarUrl)

                    call.respond(
                        HttpStatusCode.OK,
                        AvatarUploadResponse(
                            message = "Avatar uploaded successfully",
                            avatarUrl = avatarUrl
                        )
                    )

                } catch (e: Exception) {
                    logger.error(e) { "Avatar upload failed" }
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to upload avatar: ${e.message}")
                    )
                }
            }

            /**
             * DELETE /user/avatar
             * Remove current user's avatar
             *
             * Response:
             * {
             *   "message": "Avatar removed successfully"
             * }
             */
            delete("/avatar") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()

                    logger.info { "Avatar delete request from user: $userId" }

                    // Delete all avatar files for this user
                    deleteExistingAvatars(userId, uploadsDir)

                    // Clear avatarUrl in database
                    updateUserAvatarUrl(userId, null)

                    call.respond(
                        HttpStatusCode.OK,
                        AvatarDeleteResponse(message = "Avatar removed successfully")
                    )

                } catch (e: Exception) {
                    logger.error(e) { "Avatar delete failed" }
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("Failed to delete avatar: ${e.message}")
                    )
                }
            }
        }
    }
}

/**
 * Update user's avatarUrl in database
 */
private suspend fun updateUserAvatarUrl(userId: String, avatarUrl: String?) {
    com.pistoncontrol.database.DatabaseFactory.dbQuery {
        com.pistoncontrol.database.Users.update(
            { com.pistoncontrol.database.Users.id eq UUID.fromString(userId) }
        ) {
            it[com.pistoncontrol.database.Users.avatarUrl] = avatarUrl
        }
    }
    logger.info { "Updated avatarUrl for user $userId: $avatarUrl" }
}
