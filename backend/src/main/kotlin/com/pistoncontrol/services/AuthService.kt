package com.pistoncontrol.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.pistoncontrol.database.DatabaseFactory.dbQuery
import com.pistoncontrol.database.EmailVerificationCodes
import com.pistoncontrol.database.Users
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Date
import java.util.UUID

private val logger = KotlinLogging.logger {}

class AuthService(
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String,
    private val emailService: EmailService
) {
    companion object {
        private const val TOKEN_EXPIRY_MS = 86400000L // 24 hours
        private const val MIN_PASSWORD_LENGTH = 8
        private const val OTP_LENGTH = 6
        private const val OTP_EXPIRY_MINUTES = 15L
        private const val MAX_VERIFY_ATTEMPTS = 5
        private const val MAX_RESEND_PER_HOUR = 3
    }

    sealed class AuthResult {
        data class Success(val token: String, val userId: String) : AuthResult()
        data class VerificationRequired(val userId: String, val message: String) : AuthResult()
        data class Failure(val error: String, val statusCode: Int = 400) : AuthResult()
    }

    suspend fun register(firstName: String, lastName: String, email: String, phoneNumber: String, password: String): AuthResult {
        if (!isValidEmail(email)) {
            return AuthResult.Failure("Invalid email format")
        }

        val passwordError = validatePassword(password)
        if (passwordError != null) {
            return AuthResult.Failure(passwordError)
        }

        val hashedPassword = hashPassword(password)

        val userId = dbQuery {
            val existingUser = findUserByEmail(email)
            if (existingUser != null) {
                return@dbQuery null
            }

            Users.insert {
                it[Users.email] = email
                it[Users.passwordHash] = hashedPassword
                it[Users.role] = "user"
                it[Users.firstName] = firstName
                it[Users.lastName] = lastName
                it[Users.phoneNumber] = phoneNumber
                it[Users.emailVerified] = false
                it[Users.createdAt] = Instant.now()
                it[Users.updatedAt] = Instant.now()
            } get Users.id
        }

        if (userId == null) {
            return AuthResult.Failure("Email already registered", statusCode = 409)
        }

        val otpCode = generateOtpCode()
        storeVerificationCode(userId, otpCode)

        try {
            emailService.sendVerificationCode(email, otpCode, firstName)
        } catch (e: Exception) {
            logger.error(e) { "Failed to send verification email to $email" }
        }

        return AuthResult.VerificationRequired(userId.toString(), "Registration successful. Please verify your email with the code sent to $email")
    }

    suspend fun login(email: String, password: String): AuthResult {
        val user = dbQuery {
            findUserByEmail(email)
        }

        if (user == null || !verifyPassword(password, user[Users.passwordHash])) {
            return AuthResult.Failure("Invalid credentials", statusCode = 401)
        }

        if (!user[Users.emailVerified]) {
            return AuthResult.Failure("Email not verified. Please verify your email before logging in.", statusCode = 403)
        }

        val userId = user[Users.id]
        val role = user[Users.role]
        val token = generateToken(userId, role)
        return AuthResult.Success(token, userId.toString())
    }

    suspend fun verifyEmail(userId: String, code: String): AuthResult {
        val userUuid: UUID
        try {
            userUuid = UUID.fromString(userId)
        } catch (e: IllegalArgumentException) {
            return AuthResult.Failure("Invalid user ID format", statusCode = 400)
        }

        return dbQuery {
            val user = Users.select { Users.id eq userUuid }.singleOrNull()
                ?: return@dbQuery AuthResult.Failure("User not found", statusCode = 404)

            if (user[Users.emailVerified]) {
                return@dbQuery AuthResult.Failure("Email already verified", statusCode = 409)
            }

            val verificationCode = EmailVerificationCodes
                .select { EmailVerificationCodes.userId eq userUuid }
                .orderBy(EmailVerificationCodes.createdAt, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?: return@dbQuery AuthResult.Failure("No verification code found. Please request a new one.", statusCode = 404)

            if (verificationCode[EmailVerificationCodes.expiresAt].isBefore(Instant.now())) {
                EmailVerificationCodes.deleteWhere { SqlExpressionBuilder.run { EmailVerificationCodes.userId eq userUuid } }
                return@dbQuery AuthResult.Failure("Verification code expired. Please request a new one.", statusCode = 410)
            }

            val currentAttempts = verificationCode[EmailVerificationCodes.attempts]
            if (currentAttempts >= MAX_VERIFY_ATTEMPTS) {
                EmailVerificationCodes.deleteWhere { SqlExpressionBuilder.run { EmailVerificationCodes.userId eq userUuid } }
                return@dbQuery AuthResult.Failure("Too many failed attempts. Please request a new code.", statusCode = 429)
            }

            EmailVerificationCodes.update({ EmailVerificationCodes.id eq verificationCode[EmailVerificationCodes.id] }) {
                it[attempts] = currentAttempts + 1
            }

            val codeHash = hashCode(code)
            if (codeHash != verificationCode[EmailVerificationCodes.codeHash]) {
                val remaining = MAX_VERIFY_ATTEMPTS - (currentAttempts + 1)
                return@dbQuery AuthResult.Failure("Invalid verification code. $remaining attempts remaining.", statusCode = 400)
            }

            Users.update({ Users.id eq userUuid }) {
                it[emailVerified] = true
                it[updatedAt] = Instant.now()
            }

            EmailVerificationCodes.deleteWhere { SqlExpressionBuilder.run { EmailVerificationCodes.userId eq userUuid } }

            val role = user[Users.role]
            val token = generateToken(userUuid, role)
            AuthResult.Success(token, userId)
        }
    }

    suspend fun resendVerificationCode(userId: String): AuthResult {
        val userUuid: UUID
        try {
            userUuid = UUID.fromString(userId)
        } catch (e: IllegalArgumentException) {
            return AuthResult.Failure("Invalid user ID format", statusCode = 400)
        }

        val user = dbQuery {
            Users.select { Users.id eq userUuid }.singleOrNull()
        } ?: return AuthResult.Failure("User not found", statusCode = 404)

        if (user[Users.emailVerified]) {
            return AuthResult.Failure("Email already verified", statusCode = 409)
        }

        val recentCodesCount = dbQuery {
            val oneHourAgo = Instant.now().minusSeconds(3600)
            EmailVerificationCodes
                .select { (EmailVerificationCodes.userId eq userUuid) and (EmailVerificationCodes.createdAt greaterEq oneHourAgo) }
                .count()
        }

        if (recentCodesCount >= MAX_RESEND_PER_HOUR) {
            return AuthResult.Failure("Too many code requests. Please wait before requesting a new code.", statusCode = 429)
        }

        val otpCode = generateOtpCode()
        storeVerificationCode(userUuid, otpCode)

        val email = user[Users.email]
        val firstName = user[Users.firstName]

        try {
            emailService.sendVerificationCode(email, otpCode, firstName)
        } catch (e: Exception) {
            logger.error(e) { "Failed to send verification email to $email" }
            return AuthResult.Failure("Failed to send verification email. Please try again later.", statusCode = 500)
        }

        return AuthResult.VerificationRequired(userId, "Verification code sent to $email")
    }

    private fun generateOtpCode(): String {
        val code = SecureRandom().nextInt(900000) + 100000
        return code.toString()
    }

    private fun hashCode(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(code.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun storeVerificationCode(userId: UUID, code: String) {
        val codeHash = hashCode(code)
        dbQuery {
            EmailVerificationCodes.insert {
                it[EmailVerificationCodes.userId] = userId
                it[EmailVerificationCodes.codeHash] = codeHash
                it[EmailVerificationCodes.attempts] = 0
                it[EmailVerificationCodes.expiresAt] = Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60)
                it[EmailVerificationCodes.createdAt] = Instant.now()
            }
        }
    }

    private fun generateToken(userId: UUID, role: String): String {
        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("userId", userId.toString())
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + TOKEN_EXPIRY_MS))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    private fun hashPassword(plaintext: String): String {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt())
    }

    private fun verifyPassword(plaintext: String, hash: String): Boolean {
        return BCrypt.checkpw(plaintext, hash)
    }

    private fun findUserByEmail(email: String): ResultRow? {
        return Users.select { Users.email eq email }.singleOrNull()
    }

    private fun isValidEmail(email: String): Boolean {
        return email.contains("@")
    }

    private fun validatePassword(password: String): String? {
        if (password.length < MIN_PASSWORD_LENGTH) {
            return "Password must be at least $MIN_PASSWORD_LENGTH characters"
        }
        if (!password.any { it.isDigit() }) {
            return "Password must contain at least one digit"
        }
        if (!password.any { it.isUpperCase() }) {
            return "Password must contain at least one uppercase letter"
        }
        if (!password.any { it.isLowerCase() }) {
            return "Password must contain at least one lowercase letter"
        }
        return null
    }
}
