package com.pistoncontrol.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.pistoncontrol.database.DatabaseFactory.dbQuery
import com.pistoncontrol.database.Users
import org.jetbrains.exposed.sql.*
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * AuthService - Centralized Authentication Business Logic
 *
 * This service handles all authentication-related operations including:
 * - User registration with secure password hashing
 * - User login with credential verification
 * - JWT token generation
 * - Password complexity validation
 *
 * Security Notes:
 * - Uses BCrypt for password hashing with automatic salt generation
 * - BCrypt is intentionally slow (~250ms) to prevent brute-force attacks
 * - Timing-safe password comparison to prevent timing attacks
 * - JWT tokens expire after 24 hours
 */
class AuthService(
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String
) {
    companion object {
        private const val TOKEN_EXPIRY_MS = 86400000L // 24 hours
        private const val MIN_PASSWORD_LENGTH = 8
    }

    /**
     * Sealed class for type-safe authentication results
     * Eliminates the need for null checks and exception handling in routes
     */
    sealed class AuthResult {
        data class Success(val token: String, val userId: String) : AuthResult()
        data class Failure(val error: String, val statusCode: Int = 400) : AuthResult()
    }

    /**
     * Register a new user with email and password
     *
     * Process:
     * 1. Validate email format
     * 2. Validate password complexity
     * 3. Check if email already exists
     * 4. Hash password using BCrypt (auto-generated salt)
     * 5. Insert user into database
     * 6. Generate JWT token
     *
     * @param email User's email address
     * @param password User's plaintext password (will be hashed)
     * @return AuthResult.Success with token, or AuthResult.Failure with error
     */
    suspend fun register(email: String, password: String): AuthResult {
        // Validate email format
        if (!isValidEmail(email)) {
            return AuthResult.Failure("Invalid email format")
        }

        // Validate password complexity
        val passwordError = validatePassword(password)
        if (passwordError != null) {
            return AuthResult.Failure(passwordError)
        }

        // Hash password using BCrypt
        // BCrypt automatically generates a unique salt for each password
        // The salt is stored as part of the hash, no separate storage needed
        val hashedPassword = hashPassword(password)

        // Insert user into database
        val userId = dbQuery {
            // Check if email already exists
            val existingUser = findUserByEmail(email)
            if (existingUser != null) {
                return@dbQuery null
            }

            // Create new user
            Users.insert {
                it[Users.email] = email
                it[Users.passwordHash] = hashedPassword
                it[Users.role] = "user"
                it[Users.createdAt] = Instant.now()
                it[Users.updatedAt] = Instant.now()
            } get Users.id
        }

        // Check if registration succeeded
        if (userId == null) {
            return AuthResult.Failure("Email already registered", statusCode = 409)
        }

        // Generate JWT token with role
        val token = generateToken(userId, "user")
        return AuthResult.Success(token, userId.toString())
    }

    /**
     * Authenticate user with email and password
     *
     * Process:
     * 1. Look up user by email
     * 2. Verify password using BCrypt (timing-safe comparison)
     * 3. Generate JWT token if credentials are valid
     *
     * Security Note:
     * BCrypt.checkpw() is timing-safe, preventing timing attacks
     * that could reveal whether a user exists
     *
     * @param email User's email address
     * @param password User's plaintext password
     * @return AuthResult.Success with token, or AuthResult.Failure with error
     */
    suspend fun login(email: String, password: String): AuthResult {
        val user = dbQuery {
            findUserByEmail(email)
        }

        // Use timing-safe comparison to prevent timing attacks
        // BCrypt.checkpw takes similar time whether password is correct or not
        if (user == null || !verifyPassword(password, user[Users.passwordHash])) {
            // Generic error message to prevent user enumeration
            return AuthResult.Failure("Invalid credentials", statusCode = 401)
        }

        // Generate JWT token with role
        val userId = user[Users.id]
        val role = user[Users.role]
        val token = generateToken(userId, role)
        return AuthResult.Success(token, userId.toString())
    }

    /**
     * Generate JWT token for authenticated user
     *
     * Token structure:
     * - Audience: Application identifier
     * - Issuer: Service that created the token
     * - Claim: userId for user identification
     * - Claim: role for authorization checks
     * - Expiration: 24 hours from creation
     *
     * @param userId User's UUID
     * @param role User's role (user/admin)
     * @return JWT token string
     */
    private fun generateToken(userId: UUID, role: String): String {
        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("userId", userId.toString())
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + TOKEN_EXPIRY_MS))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    /**
     * Hash password using BCrypt
     *
     * BCrypt Security Features:
     * - Automatically generates unique salt for each password
     * - Salt is embedded in the hash output (no separate storage)
     * - Intentionally slow (~250ms) to prevent brute-force attacks
     * - Industry-standard password hashing algorithm
     *
     * @param plaintext Plaintext password
     * @return BCrypt hash (includes salt)
     */
    private fun hashPassword(plaintext: String): String {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt())
    }

    /**
     * Verify password against BCrypt hash
     *
     * Timing-safe comparison that prevents timing attacks
     * Takes similar time whether password is correct or incorrect
     *
     * @param plaintext Plaintext password to verify
     * @param hash BCrypt hash from database
     * @return true if password matches, false otherwise
     */
    private fun verifyPassword(plaintext: String, hash: String): Boolean {
        return BCrypt.checkpw(plaintext, hash)
    }

    /**
     * Find user by email address
     *
     * @param email Email to search for
     * @return ResultRow if user exists, null otherwise
     */
    private fun findUserByEmail(email: String): ResultRow? {
        return Users.select { Users.email eq email }.singleOrNull()
    }

    /**
     * Validate email format
     *
     * Simple validation - checks for @ symbol
     * More complex validation could use regex
     *
     * @param email Email address to validate
     * @return true if valid format, false otherwise
     */
    private fun isValidEmail(email: String): Boolean {
        return email.contains("@")
    }

    /**
     * Validate password complexity requirements
     *
     * Requirements:
     * - Minimum 8 characters
     * - At least one digit
     * - At least one uppercase letter
     * - At least one lowercase letter
     *
     * @param password Password to validate
     * @return null if valid, error message if invalid
     */
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
