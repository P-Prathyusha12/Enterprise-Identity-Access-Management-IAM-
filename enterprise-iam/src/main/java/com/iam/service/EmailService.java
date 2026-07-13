package com.iam.service;

import com.iam.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    @Async("taskExecutor")
    public void sendVerificationEmail(String to, String firstName, String token) {
        String verificationUrl = appProperties.getMail().getBaseUrl() + "/api/v1/auth/verify-email?token=" + token;
        String htmlContent = buildEmailTemplate(
                "Verify Your Email",
                "Hello " + firstName + ",",
                "Thank you for registering. Please click the button below to verify your email address and activate your account.",
                verificationUrl,
                "Verify Email",
                "This link will expire in " + appProperties.getSecurity().getEmailTokenExpiryHours() + " hours."
        );
        sendHtmlEmail(to, "Verify Your Email - Enterprise IAM", htmlContent);
    }

    @Async("taskExecutor")
    public void sendPasswordResetEmail(String to, String firstName, String token) {
        String resetUrl = "http://localhost:3000/reset-password?token=" + token; // or matching web client
        String htmlContent = buildEmailTemplate(
                "Reset Your Password",
                "Hello " + firstName + ",",
                "You requested to reset your password. Please click the button below to set a new password for your account.",
                resetUrl,
                "Reset Password",
                "This link will expire in " + appProperties.getSecurity().getPasswordResetTokenExpiryHours() + " hour. If you did not request this, you can safely ignore this email."
        );
        sendHtmlEmail(to, "Reset Your Password - Enterprise IAM", htmlContent);
    }

    @Async("taskExecutor")
    public void sendOtpEmail(String to, String firstName, String otp) {
        String htmlContent = buildOtpTemplate(
                "Your Multi-Factor Verification Code",
                "Hello " + firstName + ",",
                "Here is your one-time verification code to complete your login:",
                otp,
                "This code is valid for 5 minutes. Please do not share it with anyone."
        );
        sendHtmlEmail(to, "MFA Verification Code - Enterprise IAM", htmlContent);
    }

    @Async("taskExecutor")
    public void sendAccountLockedEmail(String to, String firstName, LocalDateTime lockTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedLockTime = lockTime.format(formatter);
        String htmlContent = buildAlertTemplate(
                "Security Alert: Account Temporarily Locked",
                "Hello " + firstName + ",",
                "Your account has been temporarily locked due to 5 consecutive failed login attempts.",
                "Lock Time: " + formattedLockTime + " UTC",
                "Your account will be automatically unlocked in 30 minutes, or you can contact support to unlock it earlier."
        );
        sendHtmlEmail(to, "Security Alert: Account Locked - Enterprise IAM", htmlContent);
    }

    @Async("taskExecutor")
    public void sendPasswordChangedEmail(String to, String firstName) {
        String htmlContent = buildAlertTemplate(
                "Security Alert: Password Changed",
                "Hello " + firstName + ",",
                "The password for your account has been successfully changed.",
                "If you performed this action, no further steps are required.",
                "If you did not change your password, please contact system administration immediately to lock your account."
        );
        sendHtmlEmail(to, "Security Alert: Password Changed - Enterprise IAM", htmlContent);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(appProperties.getMail().getFrom(), appProperties.getMail().getFromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String buildEmailTemplate(String title, String salutation, String bodyText, String actionUrl, String actionLabel, String footerText) {
        return "<html><body style='font-family: Arial, sans-serif; background-color: #f4f6f9; padding: 20px; color: #333;'>"
                + "<div style='max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; padding: 30px; box-shadow: 0 4px 10px rgba(0,0,0,0.05);'>"
                + "<h2 style='color: #2b5c8f; border-bottom: 2px solid #eaeaea; padding-bottom: 10px; margin-top: 0;'>" + title + "</h2>"
                + "<p style='font-size: 16px; line-height: 1.5;'>" + salutation + "</p>"
                + "<p style='font-size: 16px; line-height: 1.5;'>" + bodyText + "</p>"
                + "<div style='text-align: center; margin: 30px 0;'>"
                + "<a href='" + actionUrl + "' style='background-color: #2b5c8f; color: white; padding: 12px 25px; text-decoration: none; border-radius: 5px; font-weight: bold; display: inline-block;'>" + actionLabel + "</a>"
                + "</div>"
                + "<p style='font-size: 12px; color: #888; border-top: 1px solid #eaeaea; padding-top: 15px;'>" + footerText + "</p>"
                + "</div></body></html>";
    }

    private String buildOtpTemplate(String title, String salutation, String bodyText, String code, String footerText) {
        return "<html><body style='font-family: Arial, sans-serif; background-color: #f4f6f9; padding: 20px; color: #333;'>"
                + "<div style='max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; padding: 30px; box-shadow: 0 4px 10px rgba(0,0,0,0.05);'>"
                + "<h2 style='color: #2b5c8f; border-bottom: 2px solid #eaeaea; padding-bottom: 10px; margin-top: 0;'>" + title + "</h2>"
                + "<p style='font-size: 16px; line-height: 1.5;'>" + salutation + "</p>"
                + "<p style='font-size: 16px; line-height: 1.5;'>" + bodyText + "</p>"
                + "<div style='text-align: center; margin: 30px 0;'>"
                + "<span style='font-family: monospace; font-size: 32px; letter-spacing: 5px; font-weight: bold; background-color: #f0f4f8; padding: 15px 30px; border-radius: 5px; border: 1px dashed #2b5c8f; color: #2b5c8f; display: inline-block;'>" + code + "</span>"
                + "</div>"
                + "<p style='font-size: 12px; color: #888; border-top: 1px solid #eaeaea; padding-top: 15px;'>" + footerText + "</p>"
                + "</div></body></html>";
    }

    private String buildAlertTemplate(String title, String salutation, String bodyText, String infoBoxText, String footerText) {
        return "<html><body style='font-family: Arial, sans-serif; background-color: #f4f6f9; padding: 20px; color: #333;'>"
                + "<div style='max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; padding: 30px; box-shadow: 0 4px 10px rgba(0,0,0,0.05);'>"
                + "<h2 style='color: #d9534f; border-bottom: 2px solid #eaeaea; padding-bottom: 10px; margin-top: 0;'>" + title + "</h2>"
                + "<p style='font-size: 16px; line-height: 1.5;'>" + salutation + "</p>"
                + "<p style='font-size: 16px; line-height: 1.5;'>" + bodyText + "</p>"
                + "<div style='background-color: #fdf7f7; border-left: 4px solid #d9534f; padding: 15px; margin: 25px 0; font-size: 15px; color: #b94a48; font-weight: bold;'>"
                + infoBoxText
                + "</div>"
                + "<p style='font-size: 12px; color: #888; border-top: 1px solid #eaeaea; padding-top: 15px;'>" + footerText + "</p>"
                + "</div></body></html>";
    }
}
