package com.pistoncontrol.services

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import mu.KotlinLogging
import java.util.Properties

private val logger = KotlinLogging.logger {}

class EmailService(
    private val smtpHost: String,
    private val smtpPort: Int,
    private val smtpUsername: String,
    private val smtpPassword: String,
    private val fromAddress: String,
    private val starttls: Boolean = true
) {
    private val session: Session by lazy {
        val useAuth = smtpUsername.isNotBlank()
        val props = Properties().apply {
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPort.toString())
            put("mail.smtp.auth", useAuth.toString())
            put("mail.smtp.starttls.enable", if (useAuth) starttls.toString() else "false")
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
        }

        if (useAuth) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(smtpUsername, smtpPassword)
                }
            })
        } else {
            Session.getInstance(props)
        }
    }

    fun sendVerificationCode(toEmail: String, code: String, firstName: String?) {
        val displayName = firstName ?: "there"

        val htmlBody = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                <h2 style="color: #333;">Piston Control - Email Verification</h2>
                <p>Hi $displayName,</p>
                <p>Your verification code is:</p>
                <div style="background-color: #f4f4f4; padding: 20px; text-align: center; border-radius: 8px; margin: 20px 0;">
                    <span style="font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #333;">$code</span>
                </div>
                <p>This code expires in <strong>15 minutes</strong>.</p>
                <p>If you didn't request this, you can safely ignore this email.</p>
                <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
                <p style="color: #999; font-size: 12px;">Piston Control System</p>
            </body>
            </html>
        """.trimIndent()

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(fromAddress, "Piston Control"))
            setRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
            subject = "Your verification code: $code"
            setContent(htmlBody, "text/html; charset=UTF-8")
        }

        Transport.send(message)
        logger.info { "Verification email sent to $toEmail" }
    }
}
